CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT,
    password_hash TEXT,
    role TEXT NOT NULL DEFAULT 'student',
    weekly_goal SMALLINT NOT NULL DEFAULT 3,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_role CHECK (role IN ('student', 'admin')),
    CONSTRAINT chk_users_weekly_goal CHECK (weekly_goal BETWEEN 1 AND 7)
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email TEXT;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash TEXT;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'student';

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
            ADD CONSTRAINT chk_users_role CHECK (role IN ('student', 'admin'));
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

