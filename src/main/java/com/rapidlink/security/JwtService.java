package com.rapidlink.security;

import com.rapidlink.config.RapidLinkProperties;
import com.rapidlink.entity.User;
import com.rapidlink.enums.Role;
import com.rapidlink.enums.TokenType;
import com.rapidlink.exception.JwtTokenExpiredException;
import com.rapidlink.exception.JwtTokenInvalidException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
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
public class JwtService {

    private final RapidLinkProperties.Jwt jwtProperties;

    /**
     * Secret key used to sign and verify JWTs.
     *
     * <p>The key is created once during startup and reused for all JWT operations.
     */

    private final SecretKey signingKey;

    public JwtService(RapidLinkProperties rapidLinkProperties) {
        this.jwtProperties = rapidLinkProperties.getSecurity().getJwt();
        this.signingKey = Keys.hmacShaKeyFor(
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
     * Validates an access token for the given user.
     *
     * @param jwt parsed JWT
     * @param user expected user
     * @throws JwtTokenInvalidException if the token is invalid
     * @throws JwtTokenExpiredException if the token has expired
     */
    public void validateAccessToken(ParsedJwt jwt, User user) {
        validateToken(jwt, user, TokenType.ACCESS);
    }

    /**
     * Validates a refresh token for the given user.
     *
     * @param jwt parsed JWT
     * @param user expected user
     * @throws JwtTokenInvalidException if the token is invalid
     * @throws JwtTokenExpiredException if the token has expired
     */
    public void validateRefreshToken(ParsedJwt jwt, User user) {
        validateToken(jwt, user, TokenType.REFRESH);
    }

    /**
     * Validates a parsed JWT for the given user.
     *
     * @param jwt parsed JWT
     * @param user expected user
     * @param expectedType required token type
     * @throws JwtTokenInvalidException if the token is invalid
     * @throws JwtTokenExpiredException if the token has expired
     */
    private void validateToken(
            ParsedJwt jwt,
            User user,
            TokenType expectedType
    ) {
        if (!user.getId().equals(jwt.userId())) {
            throw new JwtTokenInvalidException("Token does not belong to the user.");
        }

        if (jwt.tokenType() != expectedType) {
            throw new JwtTokenInvalidException("Unexpected token type.");
        }

        if (jwt.expiration().isBefore(Instant.now())) {
            throw new JwtTokenExpiredException("JWT expired.");
        }
    }


    // -------------------- Token Parsing --------------------

    /**
     * Returns the configured access token lifetime in seconds.
     *
     * @return access token lifetime in seconds
     */
    public long getAccessTokenExpirationInSeconds() {
        return jwtProperties.getAccessTokenExpiration().toSeconds();
    }

    /**
     * Returns the remaining lifetime of a parsed JWT in seconds.
     *
     * @param jwt parsed JWT
     * @return remaining lifetime in seconds
     */
    public long getTokenExpirationInSeconds(ParsedJwt jwt) {
        return Duration.between(
                Instant.now(),
                jwt.expiration()
        ).toSeconds();
    }

    /**
     * Parses and validates a JWT.
     *
     * <p>The token signature is verified before its claims are converted
     * into a {@link ParsedJwt}. If the token is expired, malformed,
     * contains invalid claim values, or has an invalid signature,
     * an appropriate JWT exception is thrown.
     *
     * @param token JWT to parse
     * @return immutable representation of the validated JWT
     */
    public ParsedJwt parse(String token) {

        Claims claims = parseClaims(token);

        try {

            String subject = claims.getSubject();
            String email = claims.get(JwtClaims.EMAIL, String.class);
            String role = claims.get(JwtClaims.ROLE, String.class);
            String tokenType = claims.get(JwtClaims.TOKEN_TYPE, String.class);
            Date expiration = claims.getExpiration();

            if (
                    subject == null ||
                            email == null ||
                            role == null ||
                            tokenType == null ||
                            expiration == null
            ) {
                throw new JwtTokenInvalidException("Invalid JWT claims");
            }

            return new ParsedJwt(
                    UUID.fromString(subject),
                    email,
                    Role.valueOf(role),
                    TokenType.valueOf(tokenType),
                    expiration.toInstant()
            );

        } catch (IllegalArgumentException | NullPointerException ex) {

            throw new JwtTokenInvalidException("Invalid JWT claims");
        }
    }

    /**
     * Parses the JWT and returns all its claims.
     *
     * <p>The token signature is verified before the claims are returned.
     *
     * @param token JWT to parse
     * @return parsed claims
     */
    private Claims parseClaims(String token) {

        try {

            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (ExpiredJwtException ex) {

            throw new JwtTokenExpiredException("JWT access token expired");

        } catch (
                SignatureException |
                MalformedJwtException |
                UnsupportedJwtException |
                IllegalArgumentException ex
        ) {

            throw new JwtTokenInvalidException("Invalid JWT received");
        }
    }

}

final class JwtClaims {

    private JwtClaims() {
    }

    public static final String EMAIL = "email";
    public static final String ROLE = "role";
    public static final String TOKEN_TYPE = "tokenType";
}
