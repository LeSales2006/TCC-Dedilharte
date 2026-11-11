CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT,
    password_hash TEXT,
    role TEXT NOT NULL DEFAULT 'student',
    is_primary_configurator BOOLEAN NOT NULL DEFAULT FALSE,
    weekly_goal SMALLINT NOT NULL DEFAULT 3,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_role CHECK (role IN ('student', 'admin', 'configurator')),
    CONSTRAINT chk_users_weekly_goal CHECK (weekly_goal BETWEEN 1 AND 7)
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email TEXT;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash TEXT;

ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_version TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMPTZ;
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

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'student';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_primary_configurator BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_status TEXT NOT NULL DEFAULT 'active';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS weekly_goal SMALLINT NOT NULL DEFAULT 3;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_users_role'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_role CHECK (role IN ('student', 'admin', 'configurator'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_users_weekly_goal'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_weekly_goal CHECK (weekly_goal BETWEEN 1 AND 7);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'users'::regclass AND conname = 'chk_users_primary_configurator'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_primary_configurator
            CHECK (NOT is_primary_configurator OR role = 'configurator');
    END IF;
END $$;

-- Only one primary configurator account can exist. The immutable flag is the
-- account identity; display names and e-mails may change without weakening it.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_primary_configurator
    ON users (is_primary_configurator)
    WHERE is_primary_configurator;

CREATE OR REPLACE FUNCTION protect_primary_configurator_account()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.is_primary_configurator THEN
            RAISE EXCEPTION 'A conta principal do configurador nao pode ser excluida.';
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.is_primary_configurator THEN
        IF NEW.is_primary_configurator IS DISTINCT FROM TRUE
           OR NEW.role IS DISTINCT FROM OLD.role
           OR NEW.account_status IS DISTINCT FROM OLD.account_status
           OR NEW.email IS NULL
           OR NEW.password_hash IS NULL THEN
            RAISE EXCEPTION 'A conta principal do configurador e protegida.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_protect_primary_configurator ON users;
CREATE TRIGGER trg_protect_primary_configurator
    BEFORE UPDATE OR DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION protect_primary_configurator_account();

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email
    ON users (LOWER(email))
    WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS lesson_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id TEXT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    score INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT lesson_progress_user_lesson_unique UNIQUE (user_id, lesson_id)
);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_user_id
    ON lesson_progress(user_id);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_user_updated_at
    ON lesson_progress(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS user_activity (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT user_activity_user_date_unique UNIQUE (user_id, access_date)
);

CREATE INDEX IF NOT EXISTS idx_user_activity_user_date
    ON user_activity(user_id, access_date DESC);

CREATE TABLE IF NOT EXISTS user_song_progress (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    song_id TEXT NOT NULL,
    learned BOOLEAN NOT NULL DEFAULT FALSE,
    learned_at TIMESTAMPTZ,
    CONSTRAINT user_song_progress_user_song_unique UNIQUE (user_id, song_id)
);

CREATE INDEX IF NOT EXISTS idx_user_song_progress_user
    ON user_song_progress(user_id);

CREATE TABLE IF NOT EXISTS songs (
    id VARCHAR PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    artist VARCHAR(180) NOT NULL DEFAULT '',
    difficulty VARCHAR(20) NOT NULL DEFAULT 'easy',
    bpm INTEGER NOT NULL DEFAULT 60,
    tablature TEXT NOT NULL DEFAULT '',
    content JSONB,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by TEXT,
    CONSTRAINT chk_songs_difficulty CHECK (difficulty IN ('easy', 'medium', 'hard'))
);

-- Also upgrades an existing legacy table without replacing its data.
-- Apply explicitly after reviewing the target database. Never run on API startup.
BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email TEXT,
    ADD COLUMN IF NOT EXISTS password_hash TEXT,
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'student',
    ADD COLUMN IF NOT EXISTS is_primary_configurator BOOLEAN NOT NULL DEFAULT FALSE,
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

CREATE TABLE IF NOT EXISTS arpeggios (
    id VARCHAR PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    chord VARCHAR(50),
    notes JSONB NOT NULL,
    difficulty VARCHAR(20) NOT NULL DEFAULT 'easy',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_arpeggios_difficulty CHECK (difficulty IN ('easy', 'medium', 'hard'))
);

CREATE INDEX IF NOT EXISTS idx_arpeggios_active_order
    ON arpeggios(active, sort_order, created_at);

INSERT INTO arpeggios (id, title, chord, notes, difficulty, active, sort_order)
VALUES
    ('arp_c_major', 'Dó maior (C)', 'C', '[{"string":5,"fret":3},{"string":3,"fret":0},{"string":2,"fret":1},{"string":1,"fret":0}]'::jsonb, 'easy', TRUE, 1),
    ('arp_d_major', 'Ré maior (D)', 'D', '[{"string":4,"fret":0},{"string":3,"fret":2},{"string":2,"fret":3},{"string":1,"fret":2}]'::jsonb, 'easy', TRUE, 2),
    ('arp_e_major', 'Mi maior (E)', 'E', '[{"string":6,"fret":0},{"string":3,"fret":1},{"string":2,"fret":0},{"string":1,"fret":0}]'::jsonb, 'easy', TRUE, 3),
    ('arp_f_major', 'Fá maior (F)', 'F', '[{"string":6,"fret":1},{"string":3,"fret":2},{"string":2,"fret":1},{"string":1,"fret":1}]'::jsonb, 'easy', TRUE, 4),
    ('arp_g_major', 'Sol maior (G)', 'G', '[{"string":6,"fret":3},{"string":3,"fret":0},{"string":2,"fret":0},{"string":1,"fret":3}]'::jsonb, 'easy', TRUE, 5),
    ('arp_a_major', 'Lá maior (A)', 'A', '[{"string":5,"fret":0},{"string":3,"fret":2},{"string":2,"fret":2},{"string":1,"fret":0}]'::jsonb, 'easy', TRUE, 6),
    ('arp_b_major', 'Si maior (B)', 'B', '[{"string":5,"fret":2},{"string":3,"fret":4},{"string":2,"fret":4},{"string":1,"fret":2}]'::jsonb, 'easy', TRUE, 7)
ON CONFLICT (id) DO NOTHING;
