
-- ============================================================
-- Update Users Table for Authentication
-- ============================================================

-- Replace full_name with first_name and last_name
ALTER TABLE users
ADD COLUMN first_name VARCHAR(100),
ADD COLUMN last_name VARCHAR(100);

-- Migrate existing data
UPDATE users
SET first_name = full_name,
    last_name = '';

-- Make columns mandatory
ALTER TABLE users
ALTER COLUMN first_name SET NOT NULL,
ALTER COLUMN last_name SET NOT NULL;

-- Remove old column
ALTER TABLE users
DROP COLUMN full_name;


-- User role
ALTER TABLE users
ADD COLUMN user_role VARCHAR(20) NOT NULL DEFAULT 'USER'
CHECK (user_role IN ('USER', 'ADMIN'));

-- Email verification status
ALTER TABLE users
ADD COLUMN is_email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Account enabled status
ALTER TABLE users
ADD COLUMN is_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Account locked status
ALTER TABLE users
ADD COLUMN is_account_locked BOOLEAN NOT NULL DEFAULT FALSE;

-- Last updated timestamp
ALTER TABLE users
ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;