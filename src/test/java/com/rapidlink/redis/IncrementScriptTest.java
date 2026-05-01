package com.rapidlink.redis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class IncrementScriptTest extends BaseRedisTest{

    private static final String CLICK_PREFIX = "click_count:";
    private static final String ACTIVE_SET = "active_click_keys";


    //  Test: Lua script should return success indicator (1)
    @Test
    void shouldReturnSuccess_whenLuaScriptExecutes() {

        Long result = redisTemplate.execute(
                incrementAndTrackScript,
                List.of(CLICK_PREFIX + "abc", ACTIVE_SET),
                "abc"
        );

        assertEquals(1L, result);
    }

    // Test: single increment should increase click count to 1
    @Test
    void shouldIncrementClickCount_whenScriptExecutedOnce() {

        execute("abc");

        Long count = getLong(CLICK_PREFIX + "abc");
        assertEquals(1L, count);
    }

    // Test: shortCode should be added to active set after increment
    @Test
    void shouldAddShortCodeToActiveSet_whenScriptExecuted() {

        execute("abc");

        Set<String> members = redisTemplate.opsForSet().members(ACTIVE_SET);

        assertNotNull(members);
        assertTrue(members.contains("abc"));
    }


    // Test: multiple increments should accumulate count (not overwrite)
    @Test
    void shouldAccumulateClickCount_whenIncrementCalledMultipleTimes() {

        for (int i = 0; i < 5; i++) {
            execute("abc");
        }

        Long count = getLong(CLICK_PREFIX + "abc");

        assertEquals(5L, count);
    }


    // Test: different shortCodes should maintain independent counters
    @Test
    void shouldMaintainSeparateCounts_whenMultipleShortCodesUsed() {

        execute("abc");
        execute("xyz");
        execute("abc");

        assertEquals(2L, getLong(CLICK_PREFIX + "abc"));
        assertEquals(1L, getLong(CLICK_PREFIX + "xyz"));
    }


    // Test: all used shortCodes should be tracked in active set
    @Test
    void shouldTrackAllShortCodesInActiveSet_whenMultipleCodesUsed() {

        execute("abc");
        execute("xyz");

        Set<String> members = redisTemplate.opsForSet().members(ACTIVE_SET);

        assertNotNull(members);
        assertEquals(2, members.size());
        assertTrue(members.contains("abc"));
        assertTrue(members.contains("xyz"));
    }

    // Test: active set should not contain duplicates
    @Test
    void shouldNotDuplicateActiveSetEntries_whenIncrementCalledMultipleTimes() {

        for (int i = 0; i < 5; i++) {
            execute("abc");
        }

        Long setSize = redisTemplate.opsForSet().size(ACTIVE_SET);

        assertEquals(1L, setSize); // still only one entry
    }

    // Test: incrementing one key should not affect other keys
    @Test
    void shouldNotAffectOtherKeys_whenIncrementingDifferentShortCode() {

        set(CLICK_PREFIX + "other", 100);

        execute("abc");

        assertEquals(100L, getLong(CLICK_PREFIX + "other"));
        assertEquals(1L, getLong(CLICK_PREFIX + "abc"));
    }

    // ---------- Helper ----------
    private void execute(String shortCode) {
        redisTemplate.execute(
                incrementAndTrackScript,
                List.of(CLICK_PREFIX + shortCode, ACTIVE_SET),
                shortCode
        );
    }
}
