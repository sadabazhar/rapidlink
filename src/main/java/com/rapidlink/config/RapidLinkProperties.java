package com.rapidlink.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
@ConfigurationProperties(prefix = "rapidlink")
public class RapidLinkProperties {

    private String baseUrl;

    private Qr qr = new Qr();

    @Getter
    @Setter
    public static class Qr {

        private int defaultSize = 300;

        private int minSize = 100;

        private int maxSize = 1000;

        private String format = "PNG";

        private Duration cacheTtl = Duration.ofDays(7);
    }
}
