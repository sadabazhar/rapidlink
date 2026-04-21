package com.rapidlink.unit.encoder;

import com.rapidlink.encoder.Base62Encoder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    // Test: encoding 0 should return "0"
    @Test
    void shouldReturnZero_whenInputIsZero() {
        String result = Base62Encoder.encode(0);
        assertEquals("0", result);
    }

    // Test: encoding small number (single digit in Base62)
    @Test
    void shouldReturnSingleCharacter_whenInputIsWithinBase62Range() {
        String result = Base62Encoder.encode(10);
        assertEquals("a", result);
    }

    // Test: encoding large number should still work
    @Test
    void shouldReturnNonEmptyString_whenInputIsLargeNumber() {
        String result = Base62Encoder.encode(999999);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // Test: same input → same output (important property)
    @Test
    void shouldReturnSameOutput_whenSameInputProvided() {
        String first = Base62Encoder.encode(12345);
        String second = Base62Encoder.encode(12345);

        assertEquals(first, second);
    }

    // Test: different inputs → different outputs
    @Test
    void shouldReturnDifferentOutputs_whenInputsAreDifferent() {
        String val1 = Base62Encoder.encode(100);
        String val2 = Base62Encoder.encode(101);

        assertNotEquals(val1, val2);
    }

    // Test: output should only contain Base62 characters
    @Test
    void shouldContainOnlyBase62Characters_whenEncoded() {
        String result = Base62Encoder.encode(123456789);

        assertTrue(result.matches("^[0-9a-zA-Z]+$"));
    }

    // Test: -ve should not be encoded
    @Test
    void shouldThrowException_whenInputIsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> Base62Encoder.encode(-1));
    }

    // Test: extreme case (very large number)
    @Test
    void shouldProduceShorterString_forLargerBase() {
        String result = Base62Encoder.encode(Long.MAX_VALUE);

        assertTrue(result.length() < String.valueOf(Long.MAX_VALUE).length());
    }
}
