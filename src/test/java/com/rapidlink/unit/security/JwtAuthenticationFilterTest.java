package com.rapidlink.unit.security;

import com.rapidlink.entity.User;
import com.rapidlink.enums.Role;
import com.rapidlink.enums.TokenType;
import com.rapidlink.exception.JwtTokenExpiredException;
import com.rapidlink.exception.JwtTokenInvalidException;
import com.rapidlink.security.*;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------------
    // Successful Authentication
    // ------------------------------------------------------------------------

    @Test
    void shouldAuthenticateValidJwt() throws Exception {

        User user = createUser();

        ParsedJwt parsedJwt = createParsedJwt(user);

        RapidLinkUserDetails userDetails = createUserDetails(user);

        setBearerToken("valid-jwt");

        when(jwtService.parse("valid-jwt"))
                .thenReturn(parsedJwt);

        when(userDetailsService.loadUserById(user.getId()))
                .thenReturn(userDetails);

        doNothing().when(jwtService)
                .validateAccessToken(parsedJwt, user);

        filter.doFilter(request, response, filterChain);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal()).isEqualTo(userDetails);
        assertThat(authentication.isAuthenticated()).isTrue();

        verify(jwtService).parse("valid-jwt");
        verify(userDetailsService).loadUserById(user.getId());
        verify(jwtService).validateAccessToken(parsedJwt, user);
    }

    @Test
    void shouldStoreWebAuthenticationDetails() throws Exception {

        User user = createUser();

        ParsedJwt parsedJwt = createParsedJwt(user);

        RapidLinkUserDetails userDetails = createUserDetails(user);

        request.setRemoteAddr("127.0.0.1");
        setBearerToken("valid-token");

        when(jwtService.parse("valid-token"))
                .thenReturn(parsedJwt);

        when(userDetailsService.loadUserById(user.getId()))
                .thenReturn(userDetails);

        filter.doFilter(request, response, filterChain);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getDetails()).isInstanceOf(WebAuthenticationDetails.class);
        assertThat(authentication.getDetails()).isNotNull();

        WebAuthenticationDetails details =
                (WebAuthenticationDetails) authentication.getDetails();

        assertThat(details.getRemoteAddress())
                .isEqualTo("127.0.0.1");
    }

    // ------------------------------------------------------------------------
    // Missing or Invalid Authorization Header
    // ------------------------------------------------------------------------

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderMissing() throws Exception {

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        verifyNoInteractions(jwtService);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderIsNotBearer() throws Exception {

        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc123");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        verifyNoInteractions(jwtService);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void shouldContinueFilterChainWhenBearerTokenIsBlank() throws Exception {

        setBearerToken("");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        verifyNoInteractions(jwtService);
        verifyNoInteractions(userDetailsService);
    }

    // ------------------------------------------------------------------------
    // Authentication Already Exists
    // ------------------------------------------------------------------------

    @Test
    void shouldSkipAuthenticationWhenAlreadyAuthenticated() throws Exception {

        Authentication existingAuthentication = mock(Authentication.class);

        SecurityContextHolder.getContext()
                .setAuthentication(existingAuthentication);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isEqualTo(existingAuthentication);

        verifyNoInteractions(jwtService);
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    // ------------------------------------------------------------------------
    // JWT Processing Failures
    // ------------------------------------------------------------------------

    @Test
    void shouldClearSecurityContextWhenJwtIsInvalid() throws Exception {

        setBearerToken("invalid-token");

        when(jwtService.parse("invalid-token"))
                .thenThrow(new JwtTokenInvalidException("Invalid JWT received"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        assertThat(request.getAttribute("jwt.error"))
                .isEqualTo("Invalid JWT received");

        verify(jwtService).parse("invalid-token");
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldClearSecurityContextWhenJwtExpired() throws Exception {

        setBearerToken("expired-token");

        when(jwtService.parse("expired-token"))
                .thenThrow(new JwtTokenExpiredException("JWT access token expired"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        assertThat(request.getAttribute("jwt.error"))
                .isEqualTo("JWT access token expired");

        verify(jwtService).parse("expired-token");
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldClearSecurityContextWhenUserNotFound() throws Exception {

        User user = createUser();

        ParsedJwt parsedJwt = createParsedJwt(user);

        setBearerToken("valid-token");

        when(jwtService.parse("valid-token"))
                .thenReturn(parsedJwt);

        when(userDetailsService.loadUserById(user.getId()))
                .thenThrow(new UsernameNotFoundException("User not found"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        assertThat(request.getAttribute("jwt.error"))
                .isEqualTo("User not found");

        verify(jwtService).parse("valid-token");
        verify(userDetailsService).loadUserById(user.getId());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateWhenAccessTokenValidationFails() throws Exception {

        User user = createUser();

        ParsedJwt parsedJwt = createParsedJwt(user);

        RapidLinkUserDetails userDetails = createUserDetails(user);

        setBearerToken("valid-token");

        when(jwtService.parse("valid-token"))
                .thenReturn(parsedJwt);

        when(userDetailsService.loadUserById(user.getId()))
                .thenReturn(userDetails);

        doThrow(new JwtTokenInvalidException("Unexpected token type."))
                .when(jwtService)
                .validateAccessToken(parsedJwt, user);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        assertThat(request.getAttribute("jwt.error"))
                .isEqualTo("Unexpected token type.");

        verify(jwtService).parse("valid-token");
        verify(userDetailsService).loadUserById(user.getId());
        verify(jwtService).validateAccessToken(parsedJwt, user);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAlwaysContinueFilterChain() throws Exception {

        setBearerToken("invalid-token");

        when(jwtService.parse("invalid-token"))
                .thenThrow(new JwtTokenInvalidException("Invalid JWT received"));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ------------------------------------------------------------------------
    // Test Helpers
    // ------------------------------------------------------------------------

    private User createUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("sadab@example.com")
                .role(Role.USER)
                .build();
    }

    private ParsedJwt createParsedJwt(User user) {
        return new ParsedJwt(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                TokenType.ACCESS,
                Instant.now().plusSeconds(900)
        );
    }

    private RapidLinkUserDetails createUserDetails(User user) {
        return new RapidLinkUserDetails(user);
    }

    private void setBearerToken(String token) {
        request.addHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + token
        );
    }
}
