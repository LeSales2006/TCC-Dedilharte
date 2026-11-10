BEGIN;
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS initial_level TEXT NOT NULL DEFAULT 'beginner',
  ADD COLUMN IF NOT EXISTS song_goal INTEGER NOT NULL DEFAULT 10,
  ADD COLUMN IF NOT EXISTS notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'users'::regclass AND conname = 'users_initial_level_check') THEN
    ALTER TABLE users ADD CONSTRAINT users_initial_level_check CHECK (initial_level IN ('beginner', 'intermediate'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'users'::regclass AND conname = 'users_song_goal_check') THEN
    ALTER TABLE users ADD CONSTRAINT users_song_goal_check CHECK (song_goal > 0);
  END IF;
END $$;
CREATE TABLE IF NOT EXISTS auth_rate_limits (
  bucket_hash CHAR(64) PRIMARY KEY,
  attempts INTEGER NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS auth_rate_limits_expiry_idx ON auth_rate_limits(expires_at);
COMMIT;
