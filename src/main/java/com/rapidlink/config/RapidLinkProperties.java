package com.rapidlink.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Centralized configuration properties for the RapidLink application.
 *
 * <p>Maps properties defined under the {@code rapidlink} prefix in
 * {@code application.yml}. The configuration is organized into
 * feature-specific nested classes to keep related settings together.
 *
 * <p>Current configuration groups:
 * <ul>
 *   <li>QR code generation</li>
 *   <li>Security (JWT authentication)</li>
 * </ul>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "rapidlink")
public class RapidLinkProperties {

    private String baseUrl;

    @Valid
    private Qr qr = new Qr();

    @Valid
    private Security security = new Security();

    /**
     * Configuration properties for QR code generation.
     *
     * <p>Defines default QR code generation settings including image size,
     * output format, and cache duration. Validation ensures all configured
     * values remain within acceptable bounds.
     */
    @Getter
    @Setter
    public static class Qr {

        @Min(1)
        private int defaultSize = 300;

        @Min(1)
        private int minSize = 100;

        @Min(1)
        private int maxSize = 1000;

        @NotBlank
        private String format = "PNG";

        @NotNull
        private Duration cacheTtl = Duration.ofDays(7);

        @AssertTrue(message = "qr.defaultSize must be between qr.minSize and qr.maxSize")
        public boolean isDefaultSizeWithinBounds() {
            return defaultSize >= minSize && defaultSize <= maxSize;
        }

        @AssertTrue(message = "qr.minSize must be <= qr.maxSize")
        public boolean isMinLessThanOrEqualToMax() {
            return minSize <= maxSize;
        }

        @AssertTrue(message = "qr.cacheTtl must be positive")
        public boolean isCacheTtlPositive() {
            return cacheTtl != null && !cacheTtl.isZero() && !cacheTtl.isNegative();
        }
    }

    /**
     * Security-related configuration.
     *
     * <p>Groups authentication and authorization settings used throughout
     * the application.
     */
    @Getter
    @Setter
    public static class Security {

        @Valid
        private Jwt jwt = new Jwt();
    }

    /**
     * JWT (JSON Web Token) configuration.
     *
     * <p>Contains the secret key used to sign JWTs and the expiration
     * durations for access and refresh tokens.
     *
     * <p>These values are typically supplied through environment variables
     * in production environments.
     */
    @Getter
    @Setter
    public static class Jwt {

        @NotBlank
        @Size(min = 32, message = "JWT secret must be at least 32 characters long")
        private String secret;

        @NotNull
        private Duration accessTokenExpiration;

        @NotNull
        private Duration refreshTokenExpiration;
    }
}
