package com.rapidlink.repository;

import com.rapidlink.entity.ClickEvent;
import com.rapidlink.entity.ShortUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClickEventRepositoryTest {

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    private ShortUrl shortUrl;

    private static final AtomicLong seqCounter = new AtomicLong(2001L);

    @BeforeEach
    void setUp() {

        shortUrl = ShortUrl.builder()
                .seqId(seqCounter.getAndIncrement())
                .shortCode("abc" + UUID.randomUUID().toString().substring(0,6))
                .originalUrl("https://example.com")
                .clickCount(10L)
                .isActive(true)
                .build();

        entityManager.persistAndFlush(shortUrl);

        seedClickEvents();
    }

    private void seedClickEvents() {

        List<ClickEvent> events = List.of(

                ClickEvent.builder()
                        .shortUrl(shortUrl)
                        .ipHash("ip1")
                        .country("IN")
                        .deviceType("Mobile")
                        .referrer("Google")
                        .build(),

                ClickEvent.builder()
                        .shortUrl(shortUrl)
                        .ipHash("ip2")
                        .country("US")
                        .deviceType("Desktop")
                        .referrer("Twitter")
                        .build(),

                ClickEvent.builder()
                        .shortUrl(shortUrl)
                        .ipHash("ip1") // duplicate visitor
                        .country("IN")
                        .deviceType("Mobile")
                        .referrer("Google")
                        .build(),

                ClickEvent.builder()
                        .shortUrl(shortUrl)
                        .ipHash("ip3")
                        .country(null)
                        .deviceType(null)
                        .referrer(null)
                        .build()
        );

        clickEventRepository.saveAll(events);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void shouldFindAllClickEventsByShortUrlId() {

        List<ClickEvent> events =
                clickEventRepository.findByShortUrlId(shortUrl.getId());

        assertEquals(4, events.size());
    }

    @Test
    void shouldCountDistinctVisitorsCorrectly() {

        long uniqueVisitors =
                clickEventRepository.countDistinctVisitors(shortUrl.getId());

        // ip1, ip2, ip3
        assertEquals(3, uniqueVisitors);
    }

    @Test
    void shouldReturnCorrectCountryBreakdown() {

        List<Object[]> breakdown =
                clickEventRepository.getCountryBreakdown(shortUrl.getId());

        Map<String, Long> result = toMap(breakdown);

        assertEquals(2L, result.get("IN"));
        assertEquals(1L, result.get("US"));
        assertEquals(1L, result.get("UNKNOWN"));
    }

    @Test
    void shouldReturnCorrectDeviceBreakdown() {

        List<Object[]> breakdown =
                clickEventRepository.getDeviceBreakdown(shortUrl.getId());

        Map<String, Long> result = toMap(breakdown);

        assertEquals(2L, result.get("Mobile"));
        assertEquals(1L, result.get("Desktop"));
        assertEquals(1L, result.get("UNKNOWN"));
    }

    @Test
    void shouldReturnCorrectReferrerBreakdown() {

        List<Object[]> breakdown =
                clickEventRepository.getReferrerBreakdown(shortUrl.getId());

        Map<String, Long> result = toMap(breakdown);

        assertEquals(2L, result.get("Google"));
        assertEquals(1L, result.get("Twitter"));
        assertEquals(1L, result.get("Direct"));
    }

    @Test
    void shouldReturnEmptyResultsForUnknownShortUrl() {

        UUID unknownId = UUID.randomUUID();

        List<ClickEvent> events =
                clickEventRepository.findByShortUrlId(unknownId);

        long uniqueVisitors =
                clickEventRepository.countDistinctVisitors(unknownId);

        assertTrue(events.isEmpty());
        assertEquals(0, uniqueVisitors);
    }

    /**
     * Utility method to convert JPQL Object[] breakdowns into map.
     */
    private Map<String, Long> toMap(List<Object[]> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }
}