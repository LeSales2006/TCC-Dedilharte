-- Fase 1 funcional Dedilharte.
-- Executar manualmente no PostgreSQL/Aiven. Nao remove tabelas nem apaga progresso existente.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email TEXT,
    ADD COLUMN IF NOT EXISTS password_hash TEXT,
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'student',
    ADD COLUMN IF NOT EXISTS weekly_goal SMALLINT NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS account_status TEXT NOT NULL DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS blocked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS profile_photo BYTEA,
    ADD COLUMN IF NOT EXISTS profile_photo_mime TEXT,
    ADD COLUMN IF NOT EXISTS profile_photo_updated_at TIMESTAMPTZ;

DO $$
BEGIN
    ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
    ALTER TABLE users
        ADD CONSTRAINT chk_users_role CHECK (role IN ('student', 'admin', 'configurator'));

    ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_weekly_goal;
    ALTER TABLE users
        ADD CONSTRAINT chk_users_weekly_goal CHECK (weekly_goal BETWEEN 1 AND 7);

    ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_account_status;
    ALTER TABLE users
        ADD CONSTRAINT chk_users_account_status CHECK (account_status IN ('active', 'blocked'));

    ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_photo_mime;
    ALTER TABLE users
        ADD CONSTRAINT chk_users_photo_mime CHECK (
            profile_photo_mime IS NULL OR profile_photo_mime IN ('image/jpeg', 'image/png')
        );
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email
    ON users (LOWER(email))
    WHERE email IS NOT NULL;

ALTER TABLE user_song_progress
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE TABLE IF NOT EXISTS learning_modules (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    module_type TEXT NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_learning_modules_type CHECK (module_type IN ('trivia', 'exercises'))
);

CREATE INDEX IF NOT EXISTS idx_learning_modules_active_order
    ON learning_modules(active, sort_order, created_at);

CREATE TABLE IF NOT EXISTS learning_module_items (
    id TEXT PRIMARY KEY,
    module_id TEXT NOT NULL REFERENCES learning_modules(id) ON DELETE CASCADE,
    item_type TEXT NOT NULL,
    song_id TEXT,
    title TEXT,
    instructions TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_learning_module_items_type CHECK (item_type IN ('song', 'multiple_choice')),
    CONSTRAINT chk_learning_module_items_song CHECK (
        (item_type = 'song' AND song_id IS NOT NULL)
        OR (item_type = 'multiple_choice' AND song_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_learning_module_items_module_order
    ON learning_module_items(module_id, active, sort_order, created_at);

CREATE TABLE IF NOT EXISTS learning_multiple_choice (
    item_id TEXT PRIMARY KEY REFERENCES learning_module_items(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    options JSONB NOT NULL,
    correct_index INTEGER NOT NULL,
    explanation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_learning_mc_options CHECK (jsonb_typeof(options) = 'array' AND jsonb_array_length(options) BETWEEN 2 AND 5),
    CONSTRAINT chk_learning_mc_correct CHECK (correct_index >= 0 AND correct_index < jsonb_array_length(options))
);

CREATE TABLE IF NOT EXISTS learning_module_progress (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id TEXT NOT NULL REFERENCES learning_module_items(id) ON DELETE CASCADE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    correct BOOLEAN,
    selected_index INTEGER,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT learning_module_progress_user_item_unique UNIQUE (user_id, item_id),
    CONSTRAINT chk_learning_module_progress_selected CHECK (selected_index IS NULL OR selected_index >= 0)
);

CREATE INDEX IF NOT EXISTS idx_learning_module_progress_user
    ON learning_module_progress(user_id, updated_at DESC);

-- Modulos iniciais equivalentes aos botoes antigos, para aparecerem sem recompilar.
INSERT INTO learning_modules (id, name, module_type, description, sort_order, active)
VALUES
    ('module_trivia_beginner', 'Trivia Iniciante', 'trivia', 'Perguntas iniciais de dedilhado.', 1, TRUE),
    ('module_trivia_intermediate', 'Trivia Intermediario', 'trivia', 'Perguntas intermediarias de dedilhado.', 2, TRUE)
ON CONFLICT (id) DO NOTHING;
