package com.rapidlink.repository;

import com.rapidlink.entity.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ShortUrlRepositoryTest {

    @Autowired
    private ShortUrlRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    // Test: sequence should generate unique values on each call
    @Test
    void shouldReturnUniqueSeqIds_whenCalledMultipleTimes() {
        Long id1 = repository.nextSeqId();
        Long id2 = repository.nextSeqId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
    }

    // Test: sequence values should increase monotonically
    @Test
    void shouldReturnIncreasingSeqIds_whenSequenceIsUsed() {

        Long id1 = repository.nextSeqId();
        Long id2 = repository.nextSeqId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertTrue(id2 > id1);
    }

    // Test: should fetch correct ShortUrl by shortCode
    @Test
    void shouldReturnUrl_whenShortCodeExists() {
        ShortUrl url = ShortUrl.builder()
                .seqId(1001L)
                .shortCode("abc123")
                .originalUrl("https://test.com")
                .isActive(true)
                .build();

        entityManager.persistAndFlush(url);

        Optional<ShortUrl> result = repository.findByShortCode("abc123");

        assertTrue(result.isPresent());
        assertEquals("https://test.com", result.get().getOriginalUrl());
    }

    // Test: should return true when shortCode exists in DB
    @Test
    void shouldReturnTrue_whenShortCodeExists() {
        ShortUrl url = ShortUrl.builder()
                .seqId(1002L)
                .shortCode("exists123")
                .originalUrl("https://test.com")
                .isActive(true)
                .build();

        entityManager.persistAndFlush(url);

        assertTrue(repository.existsByShortCode("exists123"));
    }

    // Test: should fail when inserting duplicate shortCode (unique constraint)
    @Test
    void shouldThrowException_whenDuplicateShortCodeInserted() {
        ShortUrl url1 = ShortUrl.builder()
                .seqId(1003L)
                .shortCode("dup123")
                .originalUrl("https://a.com")
                .isActive(true)
                .build();

        ShortUrl url2 = ShortUrl.builder()
                .seqId(1004L)
                .shortCode("dup123") // same
                .originalUrl("https://b.com")
                .isActive(true)
                .build();

        entityManager.persistAndFlush(url1);

        assertThrows(Exception.class, () -> {
            entityManager.persistAndFlush(url2);
        });
    }

    // Test: should count only non-expired (or no-expiry) URLs
    @Test
    void shouldCountOnlyNonExpiredUrls_whenMixedExpiryStatesExist() {
        LocalDateTime now = LocalDateTime.now();

        // Baseline count before test inserts
        long baseline = repository.countByExpiresAtAfterOrExpiresAtIsNull(now);

        ShortUrl active = ShortUrl.builder()
                .seqId(2001L)
                .shortCode("active")
                .originalUrl("https://a.com")
                .expiresAt(null)
                .isActive(true)
                .build();

        ShortUrl future = ShortUrl.builder()
                .seqId(2002L)
                .shortCode("future")
                .originalUrl("https://b.com")
                .expiresAt(now.plusDays(1))
                .isActive(true)
                .build();

        ShortUrl expired = ShortUrl.builder()
                .seqId(2003L)
                .shortCode("expired")
                .originalUrl("https://c.com")
                .expiresAt(now.minusDays(1))
                .isActive(true)
                .build();

        entityManager.persist(active);
        entityManager.persist(future);
        entityManager.persist(expired);
        entityManager.flush();

        long countAfterInsert = repository.countByExpiresAtAfterOrExpiresAtIsNull(now);

        // Only active + future should increase count
        assertEquals(baseline + 2, countAfterInsert);
    }

}
