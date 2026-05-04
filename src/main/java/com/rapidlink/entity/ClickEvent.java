package com.rapidlink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "click_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "short_url_id", nullable = false)
    private ShortUrl shortUrl;

    @CreationTimestamp
    @Column(name = "clicked_at", nullable = false, updatable = false)
    private LocalDateTime clickedAt;

    // Privacy-safe hashed IP for unique visitor estimation.
    @Column(name = "ip_hash", length = 128, updatable = false)
    private String ipHash;

    @Column(length = 100)
    private String country;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    /**
     * Referrer/source of traffic.
     * Example: Google, Twitter, Direct
     */
    @Column(columnDefinition = "TEXT")
    private String referrer;
}