CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT,
    password_hash TEXT,
    role TEXT NOT NULL DEFAULT 'student',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_role CHECK (role IN ('student', 'admin'))
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email TEXT;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash TEXT;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'student';

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
