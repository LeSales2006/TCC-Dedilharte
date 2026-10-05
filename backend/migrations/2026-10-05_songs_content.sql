CREATE TABLE IF NOT EXISTS songs (
    id VARCHAR PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    artist VARCHAR(180) NOT NULL DEFAULT '',
    difficulty VARCHAR(20) NOT NULL DEFAULT 'easy',
    recommended_bpm INTEGER NOT NULL DEFAULT 60,
    tablature TEXT NOT NULL DEFAULT '',
    queue_order INTEGER NOT NULL DEFAULT 0,
    in_queue BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'rascunho',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_songs_difficulty CHECK (difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_songs_status CHECK (status IN ('rascunho', 'publicada', 'arquivada'))
);

CREATE INDEX IF NOT EXISTS idx_songs_public_queue
    ON songs(status, in_queue, queue_order, created_at);
