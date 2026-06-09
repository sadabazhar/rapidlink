package com.rapidlink.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for QR code generation.
 *
 * Maps values from application.yml using the prefix: rapidlink
 *
 * Default values are provided as fallback values and can be
 * overridden from application.yml without changing source code.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "rapidlink")
public class RapidLinkProperties {

    private String baseUrl;

    @Valid
    private Qr qr = new Qr();

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
}
