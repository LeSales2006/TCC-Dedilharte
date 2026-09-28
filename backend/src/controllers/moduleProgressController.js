const pool = require('../db');

function toProgress(row) {
  return {
    user_id: row.user_id,
    item_id: row.item_id,
    completed: row.completed,
    correct: row.correct,
    selectedIndex: row.selected_index,
    completed_at: row.completed_at,
    updated_at: row.updated_at,
  };
}

async function listMyProgress(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT user_id, item_id, completed, correct, selected_index, completed_at, updated_at
       FROM learning_module_progress
       WHERE user_id = $1
       ORDER BY updated_at DESC`,
      [req.user.id]
    );
    return res.json({ items: result.rows.map(toProgress) });
  } catch (error) {
    return next(error);
  }
}

async function upsertMyProgress(req, res, next) {
  try {
    const itemId = typeof req.params.id === 'string' ? req.params.id.trim() : '';
    const completed = Boolean(req.body.completed);
    const selectedIndex = req.body.selectedIndex ?? req.body.selected_index;

    const itemResult = await pool.query(
      `SELECT i.id, i.item_type, mc.correct_index
       FROM learning_module_items i
       LEFT JOIN learning_multiple_choice mc ON mc.item_id = i.id
       WHERE i.id = $1 AND i.active = TRUE`,
      [itemId]
    );
    if (itemResult.rowCount === 0) {
      return res.status(404).json({ error: 'Item nao encontrado.' });
    }
    const item = itemResult.rows[0];
    let correct = null;
    let selected = null;
    let finalCompleted = completed;
    if (item.item_type === 'multiple_choice') {
      selected = Number(selectedIndex);
      if (!Number.isInteger(selected) || selected < 0) {
        return res.status(400).json({ error: 'selected_index invalido.' });
      }
      correct = selected === item.correct_index;
      finalCompleted = true;
    }

    const result = await pool.query(
      `INSERT INTO learning_module_progress
       (user_id, item_id, completed, correct, selected_index, completed_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, CASE WHEN $3 THEN NOW() ELSE NULL END, NOW())
       ON CONFLICT (user_id, item_id) DO UPDATE
       SET completed = EXCLUDED.completed,
           correct = EXCLUDED.correct,
           selected_index = EXCLUDED.selected_index,
           completed_at = CASE
               WHEN EXCLUDED.completed THEN COALESCE(learning_module_progress.completed_at, NOW())
               ELSE NULL
           END,
           updated_at = NOW()
       RETURNING user_id, item_id, completed, correct, selected_index, completed_at, updated_at`,
      [req.user.id, itemId, finalCompleted, correct, selected]
    );
    return res.json(toProgress(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  listMyProgress,
  upsertMyProgress,
};
