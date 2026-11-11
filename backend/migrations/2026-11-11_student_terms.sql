BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_version TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMPTZ;
-- Legacy accounts and internal creations must not receive a fabricated acceptance.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
        WHERE conrelid = 'users'::regclass AND conname = 'chk_users_terms_acceptance') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_terms_acceptance CHECK (
            (terms_version IS NULL AND terms_accepted_at IS NULL) OR
            (terms_version IS NOT NULL AND length(trim(terms_version)) > 0 AND terms_accepted_at IS NOT NULL)
        );
    END IF;
END $$;
COMMIT;
