package com.rapidlink.repository;

import com.rapidlink.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Query(value = "SELECT nextval('short_urls_seq_id_seq')", nativeQuery = true)
    Long nextSeqId();

    long countByExpiresAtAfterOrExpiresAtIsNull(LocalDateTime now);

}
