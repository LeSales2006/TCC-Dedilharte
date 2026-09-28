const pool = require('../db');

function normalizeSongId(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function toSongProgress(row) {
  return {
    user_id: row.user_id,
    song_id: row.song_id,
    learned: row.learned,
    learned_at: row.learned_at,
  };
}

async function listSongProgress(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT user_id, song_id, learned, learned_at
       FROM user_song_progress
       WHERE user_id = $1
       ORDER BY song_id ASC`,
      [req.user.id]
    );

    return res.json({ items: result.rows.map(toSongProgress) });
  } catch (error) {
    return next(error);
  }
}

async function upsertSongProgress(req, res, next) {
  try {
    const songId = normalizeSongId(req.body.song_id || req.body.songId);
    const learned = Boolean(req.body.learned);

    if (!songId) {
      return res.status(400).json({ error: 'song_id e obrigatorio.' });
    }

    const result = await pool.query(
      `INSERT INTO user_song_progress (user_id, song_id, learned, learned_at)
       VALUES ($1, $2, $3, CASE WHEN $3 THEN NOW() ELSE NULL END)
       ON CONFLICT (user_id, song_id) DO UPDATE
       SET learned = EXCLUDED.learned,
           learned_at = CASE
               WHEN EXCLUDED.learned THEN COALESCE(user_song_progress.learned_at, NOW())
               ELSE NULL
           END
       RETURNING user_id, song_id, learned, learned_at`,
      [req.user.id, songId, learned]
    );

    return res.json(toSongProgress(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  listSongProgress,
  upsertSongProgress,
};
