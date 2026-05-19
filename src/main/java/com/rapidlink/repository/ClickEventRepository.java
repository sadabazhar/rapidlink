package com.rapidlink.repository;

import com.rapidlink.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    /**
     * Fetch all click events for a short URL.
     * Useful for raw event history.
     */
    List<ClickEvent> findByShortUrlId(UUID shortUrlId);

    /**
     * Counts unique visitors using hashed IP.
     */
    @Query("""
        SELECT COUNT(DISTINCT c.ipHash)
        FROM ClickEvent c
        WHERE c.shortUrl.id = :shortUrlId
    """)
    long countDistinctVisitors(@Param("shortUrlId") UUID shortUrlId);

    /**
     * Country analytics breakdown.
     */
    @Query("""
        SELECT COALESCE(c.country, 'UNKNOWN'), COUNT(c)
        FROM ClickEvent c
        WHERE c.shortUrl.id = :shortUrlId
        GROUP BY COALESCE(c.country, 'UNKNOWN')
    """)
    List<Object[]> getCountryBreakdown(@Param("shortUrlId") UUID shortUrlId);

    /**
     * Device analytics breakdown.
     */
    @Query("""
        SELECT COALESCE(c.deviceType, 'UNKNOWN'), COUNT(c)
        FROM ClickEvent c
        WHERE c.shortUrl.id = :shortUrlId
        GROUP BY COALESCE(c.deviceType, 'UNKNOWN')
    """)
    List<Object[]> getDeviceBreakdown(@Param("shortUrlId") UUID shortUrlId);

    /**
     * Referrer/source analytics breakdown.
     */
    @Query("""
        SELECT COALESCE(c.referrer, 'Direct'), COUNT(c)
        FROM ClickEvent c
        WHERE c.shortUrl.id = :shortUrlId
        GROUP BY COALESCE(c.referrer, 'Direct')
    """)
    List<Object[]> getReferrerBreakdown(@Param("shortUrlId") UUID shortUrlId);
}
