package com.rapidlink.repository;

import com.rapidlink.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Query(value = "SELECT nextval('short_urls_seq_id_seq')", nativeQuery = true)
    Long nextSeqId();

    /**
     * Increments the click count of a ShortUrl by 1 for the given shortCode.
     *
     * This update only happens if:
     * - The link is active (isActive = true)
     * - The link is not expired (expiresAt is null OR in the future)
     *
     * Return: 1 = success, 0 = no update
     */
    @Modifying
    @Query("""
        UPDATE ShortUrl s 
        SET s.clickCount = s.clickCount + 1 
        WHERE s.shortCode = :shortCode
          AND s.isActive = true
          AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP)
    """)
    int incrementClickCountIfActive(String shortCode);
}
