-- ============================================================
-- This migration adds a new column `seq_id` to short_urls.

-- Why we need this:
-- - To generate a unique number for each row
-- - This number will later be converted to a short code (Base62)
-- - Avoids collisions and race conditions from random generation
--
-- Note:
-- - Existing rows don’t have seq_id yet, so we fill them manually
-- ============================================================


-- Step 1: Add seq_id column (initially allows NULL)
ALTER TABLE short_urls
ADD COLUMN seq_id BIGINT;


-- Step 2: Create a sequence (auto-increment generator)
-- Starts from 1000 to avoid very short codes like "1", "2"
CREATE SEQUENCE short_urls_seq_id_seq
START WITH 1000
INCREMENT BY 1
OWNED BY short_urls.seq_id;


-- Step 3: Set default value for new rows
-- Every new insert will automatically get next seq_id
ALTER TABLE short_urls
ALTER COLUMN seq_id SET DEFAULT nextval('short_urls_seq_id_seq');


-- Step 4: Fill seq_id for existing rows
-- Assigns a unique value to rows that already exist
UPDATE short_urls
SET seq_id = nextval('short_urls_seq_id_seq')
WHERE seq_id IS NULL;


-- Step 5: Make seq_id mandatory (no NULL allowed)
ALTER TABLE short_urls
ALTER COLUMN seq_id SET NOT NULL;


-- Step 6: Ensure all seq_id values are unique
ALTER TABLE short_urls
ADD CONSTRAINT uq_short_urls_seq_id UNIQUE (seq_id);


-- Step 7: Add index for faster queries (optional)
CREATE INDEX idx_short_urls_seq_id ON short_urls(seq_id);