ALTER TABLE users
    ADD COLUMN IF NOT EXISTS weekly_goal SMALLINT NOT NULL DEFAULT 3;

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
