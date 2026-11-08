-- Apply explicitly after reviewing the target database. Never run on API startup.
BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email TEXT,
    ADD COLUMN IF NOT EXISTS password_hash TEXT,
    ADD COLUMN IF NOT EXISTS managed_password TEXT,
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'student',
    ADD COLUMN IF NOT EXISTS weekly_goal SMALLINT NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS account_status TEXT NOT NULL DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS blocked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS profile_photo BYTEA,
    ADD COLUMN IF NOT EXISTS profile_photo_mime TEXT,
    ADD COLUMN IF NOT EXISTS profile_photo_updated_at TIMESTAMPTZ;

-- The migration must enforce the same registration invariant as schema.sql.
-- Existing duplicate emails abort the transaction; never merge/delete accounts here.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email
    ON users (LOWER(email)) WHERE email IS NOT NULL;

-- Keep the legacy columns and all IDs, tablatures, content and publication decisions.
-- Only initialize a canonical column when it does not already exist.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'songs' AND column_name = 'bpm') THEN
        ALTER TABLE songs ADD COLUMN bpm INTEGER NOT NULL DEFAULT 60;
        UPDATE songs s SET bpm = COALESCE((to_jsonb(s)->>'recommended_bpm')::integer, 60);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'songs' AND column_name = 'sort_order') THEN
        ALTER TABLE songs ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
        UPDATE songs s SET sort_order = COALESCE((to_jsonb(s)->>'queue_order')::integer, 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'songs' AND column_name = 'active') THEN
        ALTER TABLE songs ADD COLUMN active BOOLEAN NOT NULL DEFAULT FALSE;
        UPDATE songs s SET active = COALESCE(to_jsonb(s)->>'status' = 'publicada', FALSE)
            AND COALESCE((to_jsonb(s)->>'in_queue')::boolean, TRUE);
    END IF;
END $$;

ALTER TABLE songs
    ADD COLUMN IF NOT EXISTS content JSONB,
    ADD COLUMN IF NOT EXISTS created_by TEXT;

CREATE INDEX IF NOT EXISTS idx_songs_active_order ON songs(active, sort_order, id);

DO $$
BEGIN
    IF to_regclass('learning_module_items') IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conrelid = to_regclass('learning_module_items')
            AND conname = 'fk_learning_item_song'
    ) THEN
        -- Existing orphan links remain available for an administrator to repair.
        -- New orphan links/deletion of linked songs are rejected immediately.
        ALTER TABLE learning_module_items ADD CONSTRAINT fk_learning_item_song
            FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE RESTRICT NOT VALID;
    END IF;
END $$;

COMMIT;
