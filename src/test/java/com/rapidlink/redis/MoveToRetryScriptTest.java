package com.rapidlink.redis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MoveToRetryScriptTest extends BaseRedisTest  {

    private static final String PROCESSING_PREFIX = "processing:";
    private static final String RETRY_PREFIX = "retry:";
    private static final String RETRY_COUNT_PREFIX = "retry_count:";
    private static final String RETRY_KEYS_SET = "retry_keys";


    // Test: Lua script should return success indicator (1)
    @Test
    void shouldReturnSuccess_whenLuaScriptExecutes() {

        set(PROCESSING_PREFIX + "abc", 10);

        Long result = redisTemplate.execute(
                moveToRetryAtomicScript,
                List.of(
                        PROCESSING_PREFIX + "abc",
                        RETRY_PREFIX + "abc",
                        RETRY_COUNT_PREFIX + "abc",
                        RETRY_KEYS_SET
                ),
                "abc"
        );

        assertEquals(1L, result);
    }

    // Test: should move value from processing → retry
    @Test
    void shouldMoveValueToRetry_whenProcessingKeyExists() {

        set(PROCESSING_PREFIX + "abc", 10);

        execute("abc");

        assertEquals(10L, getLong(RETRY_PREFIX + "abc"));
    }

    // Test: should delete processing key after move
    @Test
    void shouldDeleteProcessingKey_whenMovedToRetry() {

        set(PROCESSING_PREFIX + "abc", 10);

        execute("abc");

        assertFalse(exists(PROCESSING_PREFIX + "abc"));
    }

    // Test: should merge values if retry key already exists (NO overwrite)
    @Test
    void shouldMergeRetryValues_whenRetryKeyAlreadyExists() {

        set(PROCESSING_PREFIX + "abc", 10);
        set(RETRY_PREFIX + "abc", 20);

        execute("abc");

        assertEquals(30L, getLong(RETRY_PREFIX + "abc"));
    }

    // Test: should increment retry count
    @Test
    void shouldIncrementRetryCount_whenMovedToRetry() {

        set(PROCESSING_PREFIX + "abc", 10);

        execute("abc");

        assertEquals(1L, getLong(RETRY_COUNT_PREFIX + "abc"));
    }

    // Test: should accumulate retry count on multiple retries
    @Test
    void shouldAccumulateRetryCount_whenMultipleRetriesOccur() {

        set(PROCESSING_PREFIX + "abc", 10);
        execute("abc");

        set(PROCESSING_PREFIX + "abc", 5);
        execute("abc");

        assertEquals(2L, getLong(RETRY_COUNT_PREFIX + "abc"));
        assertEquals(15L, getLong(RETRY_PREFIX + "abc"));
    }

    // Test: should add shortCode to retry set
    @Test
    void shouldAddShortCodeToRetrySet_whenMovedToRetry() {

        set(PROCESSING_PREFIX + "abc", 10);

        execute("abc");

        Set<String> members = redisTemplate.opsForSet().members(RETRY_KEYS_SET);

        assertNotNull(members);
        assertTrue(members.contains("abc"));
    }

    // Test: should not duplicate retry set entries
    @Test
    void shouldNotDuplicateRetrySetEntries_whenMultipleRetriesOccur() {

        set(PROCESSING_PREFIX + "abc", 10);
        execute("abc");

        set(PROCESSING_PREFIX + "abc", 5);
        execute("abc");

        Long size = redisTemplate.opsForSet().size(RETRY_KEYS_SET);

        assertEquals(1L, size);
    }

    // Test: should do nothing if processing key does not exist
    @Test
    void shouldDoNothing_whenProcessingKeyDoesNotExist() {

        execute("abc");

        assertNull(getLong(RETRY_PREFIX + "abc"));
        assertNull(getLong(RETRY_COUNT_PREFIX + "abc"));
        assertFalse(redisTemplate.opsForSet().isMember(RETRY_KEYS_SET, "abc"));
    }

    // Test: atomic behavior - all state updates must happen together
    @Test
    void shouldUpdateAllStateAtomically_whenScriptExecutes() {

        set(PROCESSING_PREFIX + "abc", 10);

        execute("abc");

        // Validate ALL state transitions
        assertEquals(10L, getLong(RETRY_PREFIX + "abc"));
        assertEquals(1L, getLong(RETRY_COUNT_PREFIX + "abc"));
        assertTrue(redisTemplate.opsForSet().isMember(RETRY_KEYS_SET, "abc"));
        assertFalse(exists(PROCESSING_PREFIX + "abc"));
    }

    // Test: should not affect other keys
    @Test
    void shouldNotAffectOtherKeys_whenProcessingSingleKey() {

        set(PROCESSING_PREFIX + "abc", 10);
        set(PROCESSING_PREFIX + "xyz", 20);

        execute("abc");

        assertEquals(10L, getLong(RETRY_PREFIX + "abc"));
        assertEquals(20L, getLong(PROCESSING_PREFIX + "xyz")); // untouched
    }

    // ---------- Helper ----------

    private void execute(String shortCode) {
        redisTemplate.execute(
                moveToRetryAtomicScript,
                List.of(
                        PROCESSING_PREFIX + shortCode,
                        RETRY_PREFIX + shortCode,
                        RETRY_COUNT_PREFIX + shortCode,
                        RETRY_KEYS_SET
                ),
                shortCode
        );
    }
}
