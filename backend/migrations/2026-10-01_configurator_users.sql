ALTER TABLE users
    ADD COLUMN IF NOT EXISTS managed_password TEXT;
