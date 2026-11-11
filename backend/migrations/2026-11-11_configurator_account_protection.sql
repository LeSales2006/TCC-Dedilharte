-- Configurator account protection and removal of the legacy plaintext password column.
-- Apply explicitly after reviewing the target database. Never run on API startup.
BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_primary_configurator BOOLEAN NOT NULL DEFAULT FALSE;

-- Needed by the protection trigger; normally added by 2026-11-04.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_status TEXT NOT NULL DEFAULT 'active';

-- Passwords are stored only as bcrypt hashes in password_hash.
ALTER TABLE users
    DROP COLUMN IF EXISTS managed_password;

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

COMMIT;
