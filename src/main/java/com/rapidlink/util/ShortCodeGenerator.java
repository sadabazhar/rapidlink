package com.rapidlink.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    // Generates random 8-character alphanumeric string
    // NOTE: Not collision-proof at scale
    // TODO: Replace with Base62 encoding (ID → shortCode)
    public String generate() {
        return RandomStringUtils.randomAlphanumeric(8);
    }
}
