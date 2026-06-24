package com.rapidlink.security;

import com.rapidlink.enums.TokenType;
import com.rapidlink.exception.JwtTokenExpiredException;
import com.rapidlink.exception.JwtTokenInvalidException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/**
 * Spring Security filter responsible for authenticating requests
 * using JWT access tokens.
 *
 * <p>The filter executes once per request and performs the following steps:
 * <ol>
 *     <li>Extract the JWT from the Authorization header.</li>
 *     <li>Validate and parse the token.</li>
 *     <li>Load the user identified by the JWT subject.</li>
 *     <li>Create an authenticated SecurityContext.</li>
 * </ol>
 *
 * <p>If the token is missing, invalid, expired, or the user no longer
 * exists, the request continues without authentication and Spring
 * Security handles it as an anonymous request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        /*
         * Skip authentication if already set by another filter
         *
         * This protects against duplicate authentication if another
         * mechanism has already authenticated the request.
         */
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT from Authorization header
        String jwt = extractTokenFromRequest(request);

        // If no token is present, continue with remaining filters
        if (!StringUtils.hasText(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {

            /*
             * Only access tokens are allowed to authenticate requests.
             *
             * Refresh tokens are intended exclusively for obtaining
             * new access tokens and must never be accepted for
             * resource access.
             *
             * Validate the token type before performing any database
             * lookup to avoid unnecessary work.
             */
            if (jwtService.extractTokenType(jwt) != TokenType.ACCESS) {

                log.debug(
                        "Rejected non-access token for uri={}",
                        request.getRequestURI()
                );

                filterChain.doFilter(request, response);
                return;
            }

            /*
             * The JWT subject stores the user's unique identifier.
             */
            UUID userId = jwtService.extractUserId(jwt);

            /*
             * Load the authenticated user.
             *
             * The user may have been deleted after the token was issued.
             */
            RapidLinkUserDetails userDetails = userDetailsService.loadUserById(userId);

            /*
             * Verify:
             * - token signature
             * - token expiration
             * - token type is ACCESS
             * - token belongs to the expected user
             */
            if (jwtService.isAccessTokenValid(
                    jwt,
                    userDetails.getUser()
            )) {

                /*
                 * Create Spring Security authentication object.
                 *
                 * Credentials are set to null because the user has
                 * already been authenticated via JWT.
                 */
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                /*
                 * Attach request-specific details such as:
                 * - client IP
                 * - session information
                 */
                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                /*
                 * Store the authenticated user in the SecurityContext.
                 *
                 * From this point onward Spring Security considers
                 * the request authenticated.
                 */
                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);


                log.debug(
                        "JWT authentication successful for userId={} uri={}",
                        userId,
                        request.getRequestURI()
                );
            }

        } catch (JwtTokenExpiredException ex) {
            // Token is valid but expired (client should refresh)
            log.debug("JWT access token expired");

        } catch (JwtTokenInvalidException ex) {
            // Token is malformed or tampered with
            log.warn("Invalid JWT received");

        } catch (UsernameNotFoundException ex) {
            // Token references a user that no longer exists
            log.debug("JWT user not found");
        }

        // Continue processing the request.
        filterChain.doFilter(request, response);

    }

    /**
     * Extracts a JWT from the Authorization header.
     *
     * <p>Expected format:
     *
     * <pre>
     * Authorization: Bearer &lt;jwt-token&gt;
     * </pre>
     *
     * @param request current HTTP request
     * @return the JWT if present; otherwise {@code null}
     */
    private String extractTokenFromRequest(HttpServletRequest request) {

        String authHeader =
                request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader)
                || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authHeader.substring(BEARER_PREFIX.length());
    }
}
