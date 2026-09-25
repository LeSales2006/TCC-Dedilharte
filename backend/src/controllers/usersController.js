const pool = require('../db');

function normalizeName(name) {
  return typeof name === 'string' ? name.trim() : '';
}

function parseUpdatedAtMillis(value) {
  const millis = Number(value);
  if (!Number.isFinite(millis) || millis <= 0) {
    return new Date();
  }
  return new Date(millis);
}

function toUser(row) {
  return {
    id: row.id,
    name: row.name,
    created_at: row.created_at,
    updated_at: row.updated_at,
    updatedAtMillis: row.updated_at ? new Date(row.updated_at).getTime() : Date.now(),
  };
}

async function upsertUser(req, res, next) {
  try {
    const id = typeof req.body.id === 'string' ? req.body.id.trim() : '';
    const name = normalizeName(req.body.name);
    const updatedAt = parseUpdatedAtMillis(req.body.updatedAtMillis);

    if (!id || !name) {
      return res.status(400).json({ error: 'id e name sao obrigatorios.' });
    }

    const result = await pool.query(
      `INSERT INTO users (id, name, updated_at)
       VALUES ($1, $2, $3)
       ON CONFLICT (id) DO UPDATE
       SET name = CASE
               WHEN EXCLUDED.updated_at >= users.updated_at THEN EXCLUDED.name
               ELSE users.name
           END,
           updated_at = GREATEST(users.updated_at, EXCLUDED.updated_at)
       RETURNING id, name, created_at, updated_at`,
      [id, name, updatedAt]
    );

    return res.status(200).json(toUser(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function getUser(req, res, next) {
  try {
    const result = await pool.query(
      'SELECT id, name, created_at, updated_at FROM users WHERE id = $1',
      [req.params.id]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario nao encontrado.' });
    }
    return res.json(toUser(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function updateUser(req, res, next) {
  try {
    const name = normalizeName(req.body.name);
    const updatedAt = parseUpdatedAtMillis(req.body.updatedAtMillis);

    if (!name) {
      return res.status(400).json({ error: 'name e obrigatorio.' });
    }

    const result = await pool.query(
      `UPDATE users
       SET name = CASE
               WHEN $3 >= updated_at THEN $2
               ELSE name
           END,
           updated_at = GREATEST(updated_at, $3)
       WHERE id = $1
       RETURNING id, name, created_at, updated_at`,
      [req.params.id, name, updatedAt]
    );

    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario nao encontrado.' });
    }
    return res.json(toUser(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function deleteUser(req, res, next) {
  try {
    const result = await pool.query('DELETE FROM users WHERE id = $1', [req.params.id]);
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario nao encontrado.' });
    }
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  upsertUser,
  getUser,
  updateUser,
  deleteUser,
};
