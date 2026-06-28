package com.rapidlink.unit.service;

import com.rapidlink.config.RapidLinkProperties;
import com.rapidlink.entity.User;
import com.rapidlink.enums.Role;
import com.rapidlink.enums.TokenType;
import com.rapidlink.exception.JwtTokenExpiredException;
import com.rapidlink.exception.JwtTokenInvalidException;
import com.rapidlink.security.JwtService;
import com.rapidlink.security.ParsedJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private User user;

    @BeforeEach
    void setUp() {

        jwtService = createJwtService(Duration.ofMinutes(15));

        user = User.builder()
                .id(UUID.randomUUID())
                .firstName("Sadab")
                .lastName("Azhar")
                .email("sadab@example.com")
                .role(Role.USER)
                .build();
    }

    // ------------------------------------------------------------------------
    // Token Generation & Parsing
    // ------------------------------------------------------------------------

    @Test
    void shouldGenerateAndParseValidAccessToken() {

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();

        ParsedJwt parsedJwt = jwtService.parse(token);

        assertThat(parsedJwt.userId()).isEqualTo(user.getId());
        assertThat(parsedJwt.email()).isEqualTo(user.getEmail());
        assertThat(parsedJwt.role()).isEqualTo(user.getRole());
        assertThat(parsedJwt.tokenType()).isEqualTo(TokenType.ACCESS);
        assertThat(parsedJwt.expiration()).isAfter(Instant.now());
    }

    @Test
    void shouldGenerateAndParseValidRefreshToken() {

        String token = jwtService.generateRefreshToken(user);

        assertThat(token).isNotBlank();

        ParsedJwt parsedJwt = jwtService.parse(token);

        assertThat(parsedJwt.userId()).isEqualTo(user.getId());
        assertThat(parsedJwt.email()).isEqualTo(user.getEmail());
        assertThat(parsedJwt.role()).isEqualTo(user.getRole());
        assertThat(parsedJwt.tokenType()).isEqualTo(TokenType.REFRESH);
        assertThat(parsedJwt.expiration()).isAfter(Instant.now());
    }

    // ------------------------------------------------------------------------
    // Token Validation
    // ------------------------------------------------------------------------

    @Test
    void shouldValidateAccessTokenSuccessfully() {

        String token = jwtService.generateAccessToken(user);

        ParsedJwt parsedJwt = jwtService.parse(token);

        assertThatCode(() ->
                jwtService.validateAccessToken(parsedJwt, user)
        ).doesNotThrowAnyException();
    }

    @Test
    void shouldValidateRefreshTokenSuccessfully() {

        String token = jwtService.generateRefreshToken(user);

        ParsedJwt parsedJwt = jwtService.parse(token);

        assertThatCode(() ->
                jwtService.validateRefreshToken(parsedJwt, user)
        ).doesNotThrowAnyException();
    }

    @Test
    void shouldThrowExceptionWhenAccessTokenValidatedAsRefreshToken() {

        String accessToken = jwtService.generateAccessToken(user);

        ParsedJwt parsedJwt = jwtService.parse(accessToken);

        assertThatThrownBy(() ->
                jwtService.validateRefreshToken(parsedJwt, user)
        )
                .isInstanceOf(JwtTokenInvalidException.class)
                .hasMessage("Unexpected token type.");
    }

    @Test
    void shouldThrowExceptionWhenRefreshTokenValidatedAsAccessToken() {

        String refreshToken = jwtService.generateRefreshToken(user);

        ParsedJwt parsedJwt = jwtService.parse(refreshToken);

        assertThatThrownBy(() ->
                jwtService.validateAccessToken(parsedJwt, user)
        )
                .isInstanceOf(JwtTokenInvalidException.class)
                .hasMessage("Unexpected token type.");
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotMatchToken() {

        String token = jwtService.generateAccessToken(user);

        ParsedJwt parsedJwt = jwtService.parse(token);

        User anotherUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .role(Role.USER)
                .build();

        assertThatThrownBy(() ->
                jwtService.validateAccessToken(parsedJwt, anotherUser)
        )
                .isInstanceOf(JwtTokenInvalidException.class)
                .hasMessage("Token does not belong to the user.");
    }

    // ------------------------------------------------------------------------
    // Invalid Token Handling
    // ------------------------------------------------------------------------

    @Test
    void shouldThrowExceptionWhenJwtIsMalformed() {

        String malformedToken = "this-is-not-a-valid-jwt";

        assertThatThrownBy(() ->
                jwtService.parse(malformedToken)
        )
                .isInstanceOf(JwtTokenInvalidException.class)
                .hasMessage("Invalid JWT received");
    }

    @Test
    void shouldThrowExceptionWhenSignatureIsInvalid() {

        String accessToken = jwtService.generateAccessToken(user);

        String invalidToken = accessToken.substring(0, accessToken.length() - 1)
                + (accessToken.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() ->
                jwtService.parse(invalidToken)
        )
                .isInstanceOf(JwtTokenInvalidException.class)
                .hasMessage("Invalid JWT received");
    }

    // ------------------------------------------------------------------------
    // Token Expiration
    // ------------------------------------------------------------------------

    @Test
    void shouldReturnConfiguredAccessTokenExpiration() {

        assertThat(jwtService.getAccessTokenExpirationInSeconds())
                .isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void shouldReturnRemainingTokenLifetime() {

        String accessToken = jwtService.generateAccessToken(user);

        ParsedJwt parsedJwt = jwtService.parse(accessToken);

        long remainingLifetime = jwtService.getTokenExpirationInSeconds(parsedJwt);

        assertThat(remainingLifetime)
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void shouldThrowExceptionWhenAccessTokenExpired() throws InterruptedException {

        JwtService shortLivedJwtService =
                createJwtService(Duration.ofSeconds(1));

        String token = shortLivedJwtService.generateAccessToken(user);

        Thread.sleep(1100);

        assertThatThrownBy(() ->
                shortLivedJwtService.parse(token)
        )
                .isInstanceOf(JwtTokenExpiredException.class)
                .hasMessage("JWT access token expired");
    }

    // ------------------------------------------------------------------------
    // Test Helpers
    // ------------------------------------------------------------------------

    private JwtService createJwtService(Duration accessExpiration) {
        RapidLinkProperties properties = new RapidLinkProperties();

        RapidLinkProperties.Security security = new RapidLinkProperties.Security();
        RapidLinkProperties.Jwt jwt = new RapidLinkProperties.Jwt();

        jwt.setSecret("ThisIsASecretKeyThatIsAtLeast32CharactersLong!");
        jwt.setAccessTokenExpiration(accessExpiration);
        jwt.setRefreshTokenExpiration(Duration.ofDays(7));

        security.setJwt(jwt);
        properties.setSecurity(security);

        return new JwtService(properties);
    }
}
