const pool = require('../db');

function parseUpdatedAtMillis(value) {
  const millis = Number(value);
  if (!Number.isFinite(millis) || millis <= 0) {
    return new Date();
  }
  return new Date(millis);
}

function toProgress(row) {
  return {
    id: row.id,
    user_id: row.user_id,
    lesson_id: row.lesson_id,
    completed: row.completed,
    score: row.score,
    updated_at: row.updated_at,
    updatedAtMillis: row.updated_at ? new Date(row.updated_at).getTime() : Date.now(),
  };
}

async function getProgress(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT id, user_id, lesson_id, completed, score, updated_at
       FROM lesson_progress
       WHERE user_id = $1
       ORDER BY lesson_id ASC`,
      [req.params.userId]
    );
    return res.json({ items: result.rows.map(toProgress) });
  } catch (error) {
    return next(error);
  }
}

async function upsertProgress(req, res, next) {
  try {
    const lessonId = typeof req.body.lesson_id === 'string'
      ? req.body.lesson_id.trim()
      : typeof req.body.lessonId === 'string'
        ? req.body.lessonId.trim()
        : '';
    const completed = Boolean(req.body.completed);
    const score = Number.isInteger(req.body.score) ? req.body.score : 0;
    const updatedAt = parseUpdatedAtMillis(req.body.updatedAtMillis);

    if (!lessonId) {
      return res.status(400).json({ error: 'lesson_id e obrigatorio.' });
    }
    if (score < 0) {
      return res.status(400).json({ error: 'score nao pode ser negativo.' });
    }

    const user = await pool.query('SELECT id FROM users WHERE id = $1', [req.params.userId]);
    if (user.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario nao encontrado.' });
    }

    const result = await pool.query(
      `INSERT INTO lesson_progress (user_id, lesson_id, completed, score, updated_at)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (user_id, lesson_id) DO UPDATE
       SET completed = CASE
               WHEN EXCLUDED.updated_at >= lesson_progress.updated_at THEN EXCLUDED.completed
               ELSE lesson_progress.completed
           END,
           score = CASE
               WHEN EXCLUDED.updated_at >= lesson_progress.updated_at THEN EXCLUDED.score
               ELSE lesson_progress.score
           END,
           updated_at = GREATEST(lesson_progress.updated_at, EXCLUDED.updated_at)
       RETURNING id, user_id, lesson_id, completed, score, updated_at`,
      [req.params.userId, lessonId, completed, score, updatedAt]
    );

    return res.status(200).json(toProgress(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  getProgress,
  upsertProgress,
};
