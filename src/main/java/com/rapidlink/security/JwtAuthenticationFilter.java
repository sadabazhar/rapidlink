package com.rapidlink.security;

import com.rapidlink.exception.JwtTokenExpiredException;
import com.rapidlink.exception.JwtTokenInvalidException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

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

    private final UserDetailsChecker userDetailsChecker = new AccountStatusUserDetailsChecker();

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
             * Parse and validate the JWT signature.
             *
             * The returned ParsedJwt contains all claims required for
             * authentication, avoiding repeated JWT parsing.
             */
            ParsedJwt parsedJwt = jwtService.parse(jwt);

            /*
             * Load the authenticated user.
             *
             * The user may have been deleted after the token was issued.
             */
            RapidLinkUserDetails userDetails =
                    userDetailsService.loadUserById(parsedJwt.userId());

            /*
             * Re-check the user's account status since it may have changed
             * after the JWT was issued.
             */
            userDetailsChecker.check(userDetails);

            /*
             * Validate that:
             * - the token belongs to the user
             * - the token is an ACCESS token
             * - the token has not expired
             *
             * An exception is thrown if validation fails.
             */
            jwtService.validateAccessToken(
                    parsedJwt,
                    userDetails.getUser()
            );

            /*
             * Create Spring Security authentication object.
             *
             * Credentials are set to null because the user has
             * already been authenticated using the JWT.
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
             * From this point onward, Spring Security considers
             * the request authenticated.
             */
            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            log.debug(
                    "JWT authentication successful for userId={} uri={}",
                    parsedJwt.userId(),
                    request.getRequestURI()
            );

        } catch (JwtTokenExpiredException
                 | JwtTokenInvalidException
                 | UsernameNotFoundException
                 | AccountStatusException ex) {

            /*
             * Store the authentication failure so the AuthenticationEntryPoint
             * can return an appropriate 401 response.
             */
            request.setAttribute(
                    "jwt.error",
                    ex.getMessage()
            );

            // Ensure no authentication is stored for this request.
            SecurityContextHolder.clearContext();

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
