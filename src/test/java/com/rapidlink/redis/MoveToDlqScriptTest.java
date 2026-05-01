package com.rapidlink.redis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoveToDlqScriptTest extends BaseRedisTest{

    private static final String RETRY_PREFIX = "retry:";
    private static final String DLQ_PREFIX = "dlq:";

    /**
     * Test: should move value from retry → DLQ
     */
    @Test
    void shouldMoveValueToDlq_whenRetryKeyExists() {

        set(RETRY_PREFIX + "abc", 10);

        execute("abc");

        assertEquals(10L, getLong(DLQ_PREFIX + "abc"));
    }

    /**
     * Test: should delete retry key after move
     */
    @Test
    void shouldDeleteRetryKey_whenMovedToDlq() {

        set(RETRY_PREFIX + "abc", 10);

        execute("abc");

        assertFalse(exists(RETRY_PREFIX + "abc"));
    }

    /**
     * Test: should merge values if DLQ key already exists (NO overwrite)
     */
    @Test
    void shouldMergeValues_whenDlqKeyAlreadyExists() {

        set(RETRY_PREFIX + "abc", 10);
        set(DLQ_PREFIX + "abc", 50);

        execute("abc");

        assertEquals(60L, getLong(DLQ_PREFIX + "abc"));
    }

    /**
     * Test: should do nothing if retry key does not exist
     */
    @Test
    void shouldDoNothing_whenRetryKeyDoesNotExist() {

        execute("abc");

        assertNull(getLong(DLQ_PREFIX + "abc"));
        assertFalse(exists(RETRY_PREFIX + "abc"));
    }

    /**
     * Test: should be idempotent (executing multiple times should not duplicate data)
     */
    @Test
    void shouldBeIdempotent_whenExecutedMultipleTimes() {

        set(RETRY_PREFIX + "abc", 10);

        execute("abc");
        execute("abc"); // second execution should not add again

        assertEquals(10L, getLong(DLQ_PREFIX + "abc"));
    }

    /**
     * Test: Large values safety
     */
    @Test
    void shouldHandleLargeValues_whenMovingToDlq() {

        set(RETRY_PREFIX + "abc", 1_000_000);

        execute("abc");

        assertEquals(1_000_000L, getLong(DLQ_PREFIX + "abc"));
    }

    /**
     * Test: should not affect other keys
     */
    @Test
    void shouldNotAffectOtherKeys_whenMovingSingleKey() {

        set(RETRY_PREFIX + "abc", 10);
        set(RETRY_PREFIX + "xyz", 20);

        execute("abc");

        assertEquals(10L, getLong(DLQ_PREFIX + "abc"));
        assertEquals(20L, getLong(RETRY_PREFIX + "xyz")); // untouched
    }

    /**
     * Test: Multiple keys independent
     */
    @Test
    void shouldHandleMultipleKeysIndependently() {

        set(RETRY_PREFIX + "a", 3);
        set(RETRY_PREFIX + "b", 7);

        execute("a");
        execute("b");

        assertEquals(3L, getLong(DLQ_PREFIX + "a"));
        assertEquals(7L, getLong(DLQ_PREFIX + "b"));
    }

    /**
     * Test: Lua script should return success indicator
     */
    @Test
    void shouldReturnSuccess_whenLuaScriptExecutes() {

        set(RETRY_PREFIX + "abc", 10);

        Long result = redisTemplate.execute(
                moveToDlqScript,
                List.of(
                        RETRY_PREFIX + "abc",
                        DLQ_PREFIX + "abc"
                )
        );

        assertEquals(1L, result);
    }

    // ---------- Helper ----------

    private void execute(String shortCode) {
        redisTemplate.execute(
                moveToDlqScript,
                List.of(
                        RETRY_PREFIX + shortCode,
                        DLQ_PREFIX + shortCode
                )
        );
    }
}
