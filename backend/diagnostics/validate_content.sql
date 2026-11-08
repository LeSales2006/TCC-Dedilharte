-- Read-only audit. Does not print passwords, tokens, connection strings or user records.
BEGIN READ ONLY;

SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('users', 'songs', 'learning_modules', 'learning_module_items')
ORDER BY table_name, ordinal_position;

SELECT m.id AS module_id, m.active AS module_active,
       count(i.id) AS registered_items,
       count(i.id) FILTER (WHERE i.active) AS active_items
FROM learning_modules m
LEFT JOIN learning_module_items i ON i.module_id = m.id
WHERE m.id IN ('module_trivia_beginner', 'module_trivia_intermediate')
GROUP BY m.id, m.active ORDER BY m.id;

SELECT i.id, i.module_id, i.item_type, i.song_id, i.active AS item_active,
       s.active AS song_published, s.bpm,
       CASE WHEN i.item_type <> 'song' THEN 'question'
            WHEN s.id IS NULL THEN 'missing_song'
            WHEN NOT s.active THEN 'unpublished_song'
            WHEN s.bpm IS NULL OR s.bpm <= 0 THEN 'invalid_bpm'
            WHEN s.tablature IS NULL OR btrim(s.tablature) = '' THEN 'missing_tablature'
            ELSE 'check_six_strings_alignment_and_frets_in_app' END AS validation,
       s.tablature
FROM learning_module_items i
LEFT JOIN songs s ON s.id = i.song_id
WHERE i.module_id IN ('module_trivia_beginner', 'module_trivia_intermediate')
ORDER BY i.module_id, i.sort_order, i.id;

ROLLBACK;
