-- Read-only checks to run BEFORE the compatibility migration, including legacy schemas.
-- Only schema metadata and aggregate counts are returned, never emails/passwords/tokens.
BEGIN READ ONLY;

SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('users', 'songs', 'learning_modules', 'learning_module_items')
ORDER BY table_name, ordinal_position;

SELECT count(*) AS duplicate_email_groups, coalesce(sum(accounts), 0) AS affected_accounts
FROM (
    SELECT count(*) AS accounts
    FROM users u
    WHERE to_jsonb(u)->>'email' IS NOT NULL
    GROUP BY lower(to_jsonb(u)->>'email')
    HAVING count(*) > 1
) duplicates;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema() AND tablename IN ('users', 'songs')
ORDER BY tablename, indexname;

ROLLBACK;
