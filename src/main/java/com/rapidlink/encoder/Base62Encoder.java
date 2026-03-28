package com.rapidlink.encoder;

/**
 * Utility class for Base62 encoding.
 *
 * What it does:
 * - Converts a numeric value (long) into a Base62 string.
 * - Base62 uses characters: 0-9, a-z, A-Z (total 62 characters).
 *
 * Why we use this:
 * - Used to generate short, URL-safe codes from database sequence IDs (seq_id).
 * - Ensures no collisions (since seq_id is unique).
 * - Produces clean and readable short URLs (no special characters like +, /, =).
 *
 * Example:
 * - 1        -> "1"
 * - 62       -> "10"
 * - 1000000  -> "4C92"
 *
 * Design decisions:
 * - This class is stateless and contains only pure logic.
 * - Marked as final to prevent inheritance.
 * - Private constructor to prevent instantiation.
 * - Static method for easy and fast access without Spring dependency.
 *
 * Usage:
 *   String shortCode = Base62Encoder.encode(seqId);
 *
 * Note:
 * - Input must be non-negative.
 * - Output is deterministic (same input → same output).
 */
public final class Base62Encoder {

    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Base62Encoder() {} // prevent instantiation

    // Converts a numeric value into a Base62 encoded string
    public static String encode(long value) {

        // Edge case: if value is 0, directly return "0"
        if (value == 0) return "0";

        StringBuilder sb = new StringBuilder();

        // Repeat until the entire number is converted
        while (value > 0) {

            // Get remainder when divided by 62
            int remainder = (int) (value % 62);

            // Append corresponding character for this remainder
            sb.append(BASE62.charAt(remainder));

            // Reduce the number by dividing it by 62
            // Moves to the next "digit" in Base62
            value /= 62;
        }

        // Characters are appended in reverse order, so reverse before returning
        return sb.reverse().toString();
    }
}
