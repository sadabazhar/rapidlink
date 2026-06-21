package com.rapidlink.security;

import com.rapidlink.config.RapidLinkProperties;
import com.rapidlink.entity.User;
import com.rapidlink.enums.Role;
import com.rapidlink.enums.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Service for generating, validating, and parsing JSON Web Tokens (JWTs)
 * used for user authentication.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final RapidLinkProperties rapidLinkProperties;
    private RapidLinkProperties.Jwt jwtProperties;

    /**
     * Secret key used to sign and verify JWTs.
     *
     * <p>The key is created once during startup and reused for all JWT operations.
     */
    private SecretKey signingKey;

    @PostConstruct
    void initializeSigningKey() {

        jwtProperties = rapidLinkProperties.getSecurity().getJwt();

        signingKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    // -------------------- Token Generation --------------------

    /**
     * Generates a JWT access token for the given user.
     *
     * <p>Access tokens are sent with authenticated requests and have a short lifetime.
     *
     * @param user authenticated user
     * @return signed access token
     */
    public String generateAccessToken(User user) {
        return generateToken(
                user,
                TokenType.ACCESS,
                jwtProperties.getAccessTokenExpiration()
        );
    }

    /**
     * Generates a JWT refresh token for the given user.
     *
     * <p>Refresh tokens are used to obtain new access tokens after they expire.
     *
     * @param user authenticated user
     * @return signed refresh token
     */
    public String generateRefreshToken(User user) {
        return generateToken(
                user,
                TokenType.REFRESH,
                jwtProperties.getRefreshTokenExpiration()
        );
    }

    /**
     * Creates and signs a JWT for the given user.
     *
     * <p>The token contains the user's ID as the subject, along with
     * the user's email, role, token type, issued time, and expiration time.
     *
     * @param user authenticated user
     * @param tokenType access or refresh token
     * @param expiration token lifetime
     * @return signed JWT
     */
    private String generateToken(
            User user,
            TokenType tokenType,
            Duration expiration
    ) {

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(JwtClaims.EMAIL, user.getEmail())
                .claim(JwtClaims.ROLE, user.getRole().name())
                .claim(JwtClaims.TOKEN_TYPE, tokenType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    // -------------------- Token Validation --------------------

    /**
     * Checks whether the given access token is valid for the user.
     *
     * <p>A token is valid if:
     * <ul>
     *     <li>the user ID matches,</li>
     *     <li>the token type is ACCESS,</li>
     *     <li>the token has not expired.</li>
     * </ul>
     *
     * @param token access token
     * @param user expected user
     * @return {@code true} if the token is valid
     */
    public boolean isAccessTokenValid(String token, User user) {
        return isTokenValid(token, user, TokenType.ACCESS);
    }

    /**
     * Checks whether the given refresh token is valid for the user.
     *
     * <p>A token is valid if:
     * <ul>
     *     <li>the user ID matches,</li>
     *     <li>the token type is REFRESH,</li>
     *     <li>the token has not expired.</li>
     * </ul>
     *
     * @param token refresh token
     * @param user expected user
     * @return {@code true} if the token is valid
     */
    public boolean isRefreshTokenValid(String token, User user) {
        return isTokenValid(token, user, TokenType.REFRESH);
    }

    /**
     * Performs common validation for JWTs.
     *
     * @param token JWT to validate
     * @param user expected user
     * @param expectedTokenType required token type
     * @return {@code true} if the token is valid
     */
    private boolean isTokenValid(
            String token,
            User user,
            TokenType expectedTokenType
    ) {

        return user.getId().equals(extractUserId(token))
                && expectedTokenType == extractTokenType(token)
                && !isTokenExpired(token);
    }

    /**
     * Checks whether the token has expired.
     *
     * @param token JWT to inspect
     * @return {@code true} if the token is expired
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }


    // -------------------- Claim Extraction --------------------

    /**
     * Extracts the user's ID from the JWT.
     *
     * @param token JWT to inspect
     * @return user's ID
     */
    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    /**
     * Extracts the user's email from the JWT.
     *
     * @param token JWT to inspect
     * @return user's email
     */
    public String extractEmail(String token) {
        return extractAllClaims(token).get(JwtClaims.EMAIL, String.class);
    }

    /**
     * Extracts the user's role from the JWT.
     *
     * @param token JWT to inspect
     * @return user's role
     */
    public Role extractUserRole(String token) {

        String role = extractAllClaims(token).get(JwtClaims.ROLE, String.class);
        return Role.valueOf(role);
    }


    /**
     * Extracts the token's type from the JWT.
     *
     * @param token JWT to inspect
     * @return token's type
     */
    public TokenType extractTokenType(String token) {

        String tokenType = extractAllClaims(token).get(JwtClaims.TOKEN_TYPE, String.class);
        return TokenType.valueOf(tokenType);
    }

    /**
     * Extracts the token's expiration from the JWT.
     *
     * @param token JWT to inspect
     * @return expiration date
     */
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }


    /**
     * Parses the JWT and returns all its claims.
     *
     * <p>The token signature is verified before the claims are returned.
     *
     * @param token JWT to parse
     * @return parsed claims
     */
    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}

final class JwtClaims {

    private JwtClaims() {
    }

    public static final String EMAIL = "email";
    public static final String ROLE = "role";
    public static final String TOKEN_TYPE = "tokenType";
}
