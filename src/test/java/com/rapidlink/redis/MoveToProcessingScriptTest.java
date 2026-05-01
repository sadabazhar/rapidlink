package com.rapidlink.redis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoveToProcessingScriptTest extends BaseRedisTest{

    private static final String CLICK_PREFIX = "click_count:";
    private static final String PROCESSING_PREFIX = "processing:";

    // Test: Lua script should return success indicator (1)
    @Test
    void shouldReturnSuccess_whenLuaScriptExecutes() {

        set(CLICK_PREFIX + "abc", 10);

        Long result = redisTemplate.execute(
                moveToProcessingScript,
                List.of(
                        CLICK_PREFIX + "abc",
                        PROCESSING_PREFIX + "abc"
                )
        );

        assertEquals(1L, result);
    }

    // Test: should move value from click_count → processing
    @Test
    void shouldMoveValueToProcessing_whenSourceKeyExists() {

        set(CLICK_PREFIX + "abc", 10);

        execute("abc");

        assertEquals(10L, getLong(PROCESSING_PREFIX + "abc"));
    }


    // Test: should delete source key after moving value to processing
    @Test
    void shouldDeleteSourceKey_whenMovedToProcessing() {

        set(CLICK_PREFIX + "abc", 10);

        execute("abc");

        assertFalse(exists(CLICK_PREFIX + "abc"));
    }

    // Test: should merge values if processing key already exists (prevents overwrite)
    @Test
    void shouldMergeValues_whenProcessingKeyAlreadyExists() {

        set(CLICK_PREFIX + "abc", 10);
        set(PROCESSING_PREFIX + "abc", 5);

        execute("abc");

        assertEquals(15L, getLong(PROCESSING_PREFIX + "abc"));
    }

    // Test: should do nothing if source key does not exist
    @Test
    void shouldDoNothing_whenSourceKeyDoesNotExist() {

        execute("abc");

        assertNull(getLong(PROCESSING_PREFIX + "abc"));
        assertFalse(exists(CLICK_PREFIX + "abc"));
    }


    // Test: should be idempotent (running multiple times should not duplicate data)
    @Test
    void shouldBeIdempotent_whenExecutedMultipleTimes() {

        set(CLICK_PREFIX + "abc", 10);

        execute("abc");
        execute("abc"); // second run should not add again

        assertEquals(10L, getLong(PROCESSING_PREFIX + "abc"));
    }

    // Test: should not affect other keys
    @Test
    void shouldNotAffectOtherKeys_whenProcessingOneKey() {

        set(CLICK_PREFIX + "abc", 10);
        set(CLICK_PREFIX + "xyz", 20);

        execute("abc");

        assertEquals(10L, getLong(PROCESSING_PREFIX + "abc"));
        assertEquals(20L, getLong(CLICK_PREFIX + "xyz")); // untouched
    }

    // Test: Multiple keys handled independently
    @Test
    void shouldHandleMultipleKeysIndependently() {

        set(CLICK_PREFIX + "abc", 3);
        set(CLICK_PREFIX + "xyz", 7);

        execute("abc");
        execute("xyz");

        assertEquals(3L, getLong(PROCESSING_PREFIX + "abc"));
        assertEquals(7L, getLong(PROCESSING_PREFIX + "xyz"));
    }

    // Test: Large value safety
    @Test
    void shouldHandleLargeValuesSafely() {

        set(CLICK_PREFIX + "abc", 1_000_000);

        execute("abc");

        assertEquals(1_000_000L, getLong(PROCESSING_PREFIX + "abc"));
    }

    // ---------- Helper ----------

    private void execute(String shortCode) {
        redisTemplate.execute(
                moveToProcessingScript,
                List.of(
                        CLICK_PREFIX + shortCode,
                        PROCESSING_PREFIX + shortCode
                )
        );
    }
}
