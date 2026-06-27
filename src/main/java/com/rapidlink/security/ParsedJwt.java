package com.rapidlink.security;

import com.rapidlink.enums.Role;
import com.rapidlink.enums.TokenType;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable representation of a validated JWT.
 *
 * <p>Instances are created only after the JWT signature has been
 * successfully verified and all required claims have been parsed.
 */
public record ParsedJwt(
        UUID userId,
        String email,
        Role role,
        TokenType tokenType,
        Instant expiration
) {
}
