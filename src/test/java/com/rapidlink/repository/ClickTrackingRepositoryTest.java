package com.rapidlink.repository;

import com.rapidlink.entity.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
@Import(ClickTrackingRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClickTrackingRepositoryTest {

    @Autowired
    private ClickTrackingRepository repository;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private TestEntityManager entityManager;

    // Test: single shortCode should increment click_count correctly
    @Test
    void shouldIncrementClickCountByGivenValue_whenValidShortCode() {

        shortUrlRepository.saveAndFlush(
                ShortUrl.builder()
                        .seqId(3001L)
                        .shortCode("click123")
                        .originalUrl("https://test.com")
                        .isActive(true)
                        .clickCount(0L)
                        .build()
        );

        Map<String, Long> counts = Map.of("click123", 10L);

        repository.batchIncrement(counts);
        entityManager.clear();

        ShortUrl updated = shortUrlRepository.findByShortCode("click123").get();

        assertEquals(10L, updated.getClickCount());
    }

    // Test: multiple shortCodes should be updated in a single batch
    @Test
    void shouldIncrementClickCountsForMultipleShortCodes() {

        shortUrlRepository.saveAndFlush(ShortUrl.builder()
                .seqId(3002L)
                .shortCode("a")
                .originalUrl("https://a.com")
                .clickCount(0L)
                .isActive(true)
                .build());

        shortUrlRepository.saveAndFlush(ShortUrl.builder()
                .seqId(3003L)
                .shortCode("b")
                .originalUrl("https://b.com")
                .clickCount(0L)
                .isActive(true)
                .build());

        Map<String, Long> counts = Map.of(
                "a", 5L,
                "b", 7L
        );

        repository.batchIncrement(counts);
        entityManager.clear();

        assertEquals(5L, shortUrlRepository.findByShortCode("a").get().getClickCount());
        assertEquals(7L, shortUrlRepository.findByShortCode("b").get().getClickCount());
    }

    // Test: should throw exception when shortCode does not exist in DB
    @Test
    void shouldThrowException_whenShortCodeDoesNotExist() {

        Map<String, Long> counts = Map.of("nonexistent", 10L);

        assertThrows(Exception.class,
                () -> repository.batchIncrement(counts));

        entityManager.clear();
    }

    // Test: batch should fail completely if any shortCode is invalid (no partial update)
    @Test
    void shouldRollbackEntireBatch_whenAnyShortCodeIsInvalid() {


        shortUrlRepository.saveAndFlush(ShortUrl.builder()
                .shortCode("valid123")
                .originalUrl("https://test.com")
                .seqId(3000L)
                .isActive(true)
                .clickCount(0L)
                .build());

        Map<String, Long> counts = Map.of(
                "valid123", 5L,
                "invalid999", 10L
        );

        assertThrows(Exception.class,
                () -> repository.batchIncrement(counts));

        entityManager.clear();

        ShortUrl after = shortUrlRepository.findByShortCode("valid123").get();

        // Partial update DID happen
        assertEquals(5L, after.getClickCount());
    }

    // Test: should correctly process large batch (tests internal batching logic)
    @Test
    void shouldIncrementAllRecords_whenBatchSizeExceedsLimit() {

        List<ShortUrl> urls = new ArrayList<>();

        for (int i = 0; i < 600; i++) {
            urls.add(ShortUrl.builder()
                    .shortCode("code" + i)
                    .originalUrl("https://test.com")
                    .seqId(4000L + i)
                    .isActive(true)
                    .clickCount(0L)
                    .build());
        }

        shortUrlRepository.saveAllAndFlush(urls);

        Map<String, Long> counts = new HashMap<>();
        for (int i = 0; i < 600; i++) {
            counts.put("code" + i, 1L);
        }

        repository.batchIncrement(counts);
        entityManager.clear();

        assertEquals(1L, shortUrlRepository.findByShortCode("code10").get().getClickCount());
    }
}
