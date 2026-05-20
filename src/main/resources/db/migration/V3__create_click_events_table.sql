
-- ============================================================
-- Click Events Table
-- Stores analytics data for each short URL click.
-- Phase 1 focuses on basic traffic insights:
-- - Total historical clicks
-- - Unique visitor estimation (via hashed IP)
-- - Country analytics
-- - Device type analytics
-- - Referrer/source tracking
--
-- This table is designed for async event ingestion and future
-- analytics expansion without impacting redirect performance.
-- ============================================================

CREATE TABLE click_events (

    -- Unique identifier for each click event
    id BIGSERIAL PRIMARY KEY,

    -- Reference to the related short URL
    short_url_id UUID NOT NULL,

    -- Timestamp when the click occurred
    clicked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Hashed visitor IP for privacy-safe unique visitor tracking
    ip_hash VARCHAR(128),

    -- Visitor country (basic geo analytics)
    country VARCHAR(100),

    -- Visitor device type (mobile, desktop, tablet)
    device_type VARCHAR(50),

    -- Traffic source (Google, Twitter, direct, etc.)
    referrer TEXT,

    -- Foreign key to short_urls table
    CONSTRAINT fk_click_events_short_url
        FOREIGN KEY (short_url_id)
        REFERENCES short_urls(id)
        ON DELETE CASCADE
);

-- ============================================================
-- Performance Indexes
-- ============================================================

-- Fast analytics lookup by short URL
CREATE INDEX idx_click_events_short_url_id
ON click_events(short_url_id);

-- Supports time-based analytics queries
CREATE INDEX idx_click_events_clicked_at
ON click_events(clicked_at);

-- Improves country-level analytics queries
CREATE INDEX idx_click_events_country
ON click_events(country);

-- Improves device-type analytics queries
CREATE INDEX idx_click_events_device_type
ON click_events(device_type);