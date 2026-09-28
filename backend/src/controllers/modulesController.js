const crypto = require('crypto');
const pool = require('../db');

const MODULE_TYPES = new Set(['trivia', 'exercises']);
const ITEM_TYPES = new Set(['song', 'multiple_choice']);

function text(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function bool(value, fallback = true) {
  return value === undefined ? fallback : Boolean(value);
}

function int(value, fallback = 0) {
  return Number.isInteger(value) ? value : fallback;
}

function toPublicItem(row) {
  const item = {
    id: row.id,
    moduleId: row.module_id,
    itemType: row.item_type,
    songId: row.song_id,
    title: row.title,
    instructions: row.instructions,
    sortOrder: row.sort_order,
    active: row.active,
    created_at: row.created_at,
    updated_at: row.updated_at,
  };
  if (row.item_type === 'multiple_choice') {
    item.multipleChoice = {
      question: row.question,
      options: row.options,
      explanation: row.explanation,
    };
  }
  return item;
}

function toStaffItem(row) {
  const item = toPublicItem(row);
  if (row.item_type === 'multiple_choice' && item.multipleChoice) {
    item.multipleChoice.correctIndex = row.correct_index;
  }
  return item;
}

function toModule(row, items = []) {
  return {
    id: row.id,
    name: row.name,
    moduleType: row.module_type,
    description: row.description,
    sortOrder: row.sort_order,
    active: row.active,
    createdBy: row.created_by,
    created_at: row.created_at,
    updated_at: row.updated_at,
    items,
  };
}

function validateModule(body, partial = false) {
  const name = text(body.name);
  const moduleType = text(body.module_type || body.moduleType);
  const description = text(body.description);
  const sortOrder = int(body.sort_order ?? body.sortOrder, 0);
  const active = bool(body.active, true);
  if (!partial || name) {
    if (!name) return { error: 'name e obrigatorio.' };
  }
  if (!partial || moduleType) {
    if (!MODULE_TYPES.has(moduleType)) return { error: 'module_type deve ser trivia ou exercises.' };
  }
  return { name, moduleType, description, sortOrder, active };
}

function validateOptions(options, correctIndex) {
  const parsed = Array.isArray(options) ? options.map(text).filter(Boolean) : [];
  if (parsed.length < 2 || parsed.length > 5) {
    return { error: 'options deve ter entre 2 e 5 alternativas.' };
  }
  if (!Number.isInteger(correctIndex) || correctIndex < 0 || correctIndex >= parsed.length) {
    return { error: 'correct_index invalido.' };
  }
  return { options: parsed };
}

function validateItem(body, partial = false) {
  const itemType = text(body.item_type || body.itemType);
  const songId = text(body.song_id || body.songId);
  const title = text(body.title);
  const instructions = text(body.instructions);
  const sortOrder = int(body.sort_order ?? body.sortOrder, 0);
  const active = bool(body.active, true);
  const question = text(body.question);
  const explanation = text(body.explanation);
  const correctIndex = Number(body.correct_index ?? body.correctIndex);

  if (!partial || itemType) {
    if (!ITEM_TYPES.has(itemType)) return { error: 'item_type deve ser song ou multiple_choice.' };
  }
  if ((itemType === 'song' || (!partial && itemType === 'song')) && !songId) {
    return { error: 'song_id e obrigatorio para item song.' };
  }
  if (itemType === 'multiple_choice' || (!partial && body.options !== undefined)) {
    if (!question) return { error: 'question e obrigatoria.' };
    const parsed = validateOptions(body.options, correctIndex);
    if (parsed.error) return parsed;
    return {
      itemType,
      songId: null,
      title,
      instructions,
      sortOrder,
      active,
      question,
      options: parsed.options,
      correctIndex,
      explanation,
    };
  }
  return { itemType, songId, title, instructions, sortOrder, active };
}

async function listPublic(req, res, next) {
  try {
    const moduleResult = await pool.query(
      `SELECT id, name, module_type, description, sort_order, active, created_by, created_at, updated_at
       FROM learning_modules
       WHERE active = TRUE
       ORDER BY sort_order ASC, created_at ASC`
    );
    if (moduleResult.rowCount === 0) {
      return res.json({ items: [] });
    }
    const ids = moduleResult.rows.map((row) => row.id);
    const itemResult = await pool.query(
      `SELECT i.id, i.module_id, i.item_type, i.song_id, i.title, i.instructions,
              i.sort_order, i.active, i.created_at, i.updated_at,
              mc.question, mc.options, mc.explanation
       FROM learning_module_items i
       LEFT JOIN learning_multiple_choice mc ON mc.item_id = i.id
       WHERE i.module_id = ANY($1) AND i.active = TRUE
       ORDER BY i.module_id ASC, i.sort_order ASC, i.created_at ASC`,
      [ids]
    );
    const byModule = new Map();
    for (const item of itemResult.rows) {
      const list = byModule.get(item.module_id) || [];
      list.push(toPublicItem(item));
      byModule.set(item.module_id, list);
    }
    return res.json({
      items: moduleResult.rows.map((row) => toModule(row, byModule.get(row.id) || [])),
    });
  } catch (error) {
    return next(error);
  }
}

async function listStaff(req, res, next) {
  try {
    const moduleResult = await pool.query(
      `SELECT id, name, module_type, description, sort_order, active, created_by, created_at, updated_at
       FROM learning_modules
       ORDER BY sort_order ASC, created_at ASC`
    );
    if (moduleResult.rowCount === 0) {
      return res.json({ items: [] });
    }
    const ids = moduleResult.rows.map((row) => row.id);
    const itemResult = await pool.query(
      `SELECT i.id, i.module_id, i.item_type, i.song_id, i.title, i.instructions,
              i.sort_order, i.active, i.created_at, i.updated_at,
              mc.question, mc.options, mc.correct_index, mc.explanation
       FROM learning_module_items i
       LEFT JOIN learning_multiple_choice mc ON mc.item_id = i.id
       WHERE i.module_id = ANY($1)
       ORDER BY i.module_id ASC, i.sort_order ASC, i.created_at ASC`,
      [ids]
    );
    const byModule = new Map();
    for (const item of itemResult.rows) {
      const list = byModule.get(item.module_id) || [];
      list.push(toStaffItem(item));
      byModule.set(item.module_id, list);
    }
    return res.json({
      items: moduleResult.rows.map((row) => toModule(row, byModule.get(row.id) || [])),
    });
  } catch (error) {
    return next(error);
  }
}

async function createModule(req, res, next) {
  try {
    const payload = validateModule(req.body);
    if (payload.error) return res.status(400).json({ error: payload.error });
    const id = text(req.body.id) || `mod_${crypto.randomUUID()}`;
    const result = await pool.query(
      `INSERT INTO learning_modules
       (id, name, module_type, description, sort_order, active, created_by, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())
       RETURNING id, name, module_type, description, sort_order, active, created_by, created_at, updated_at`,
      [id, payload.name, payload.moduleType, payload.description, payload.sortOrder, payload.active, req.user.id]
    );
    return res.status(201).json(toModule(result.rows[0], []));
  } catch (error) {
    return next(error);
  }
}

async function updateModule(req, res, next) {
  try {
    const payload = validateModule(req.body);
    if (payload.error) return res.status(400).json({ error: payload.error });
    const result = await pool.query(
      `UPDATE learning_modules
       SET name = $2, module_type = $3, description = $4, sort_order = $5, active = $6, updated_at = NOW()
       WHERE id = $1
       RETURNING id, name, module_type, description, sort_order, active, created_by, created_at, updated_at`,
      [req.params.id, payload.name, payload.moduleType, payload.description, payload.sortOrder, payload.active]
    );
    if (result.rowCount === 0) return res.status(404).json({ error: 'Modulo nao encontrado.' });
    return res.json(toModule(result.rows[0], []));
  } catch (error) {
    return next(error);
  }
}

async function createItem(req, res, next) {
  const client = await pool.connect();
  try {
    const payload = validateItem(req.body);
    if (payload.error) return res.status(400).json({ error: payload.error });
    await client.query('BEGIN');
    const itemResult = await client.query(
      `INSERT INTO learning_module_items
       (id, module_id, item_type, song_id, title, instructions, sort_order, active, created_by, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
       RETURNING id, module_id, item_type, song_id, title, instructions, sort_order, active, created_at, updated_at`,
      [
        text(req.body.id) || `item_${crypto.randomUUID()}`,
        req.params.id,
        payload.itemType,
        payload.songId,
        payload.title,
        payload.instructions,
        payload.sortOrder,
        payload.active,
        req.user.id,
      ]
    );
    if (payload.itemType === 'multiple_choice') {
      await client.query(
        `INSERT INTO learning_multiple_choice
         (item_id, question, options, correct_index, explanation, updated_at)
         VALUES ($1, $2, $3::jsonb, $4, $5, NOW())`,
        [itemResult.rows[0].id, payload.question, JSON.stringify(payload.options), payload.correctIndex, payload.explanation]
      );
    }
    await client.query('COMMIT');
    return res.status(201).json(toPublicItem(itemResult.rows[0]));
  } catch (error) {
    await client.query('ROLLBACK');
    return next(error);
  } finally {
    client.release();
  }
}

async function updateItem(req, res, next) {
  const client = await pool.connect();
  try {
    const payload = validateItem(req.body);
    if (payload.error) return res.status(400).json({ error: payload.error });
    await client.query('BEGIN');
    const itemResult = await client.query(
      `UPDATE learning_module_items
       SET item_type = $2, song_id = $3, title = $4, instructions = $5,
           sort_order = $6, active = $7, updated_at = NOW()
       WHERE id = $1
       RETURNING id, module_id, item_type, song_id, title, instructions, sort_order, active, created_at, updated_at`,
      [req.params.id, payload.itemType, payload.songId, payload.title, payload.instructions, payload.sortOrder, payload.active]
    );
    if (itemResult.rowCount === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Item nao encontrado.' });
    }
    await client.query('DELETE FROM learning_multiple_choice WHERE item_id = $1', [req.params.id]);
    if (payload.itemType === 'multiple_choice') {
      await client.query(
        `INSERT INTO learning_multiple_choice
         (item_id, question, options, correct_index, explanation, updated_at)
         VALUES ($1, $2, $3::jsonb, $4, $5, NOW())`,
        [req.params.id, payload.question, JSON.stringify(payload.options), payload.correctIndex, payload.explanation]
      );
    }
    await client.query('COMMIT');
    return res.json(toPublicItem(itemResult.rows[0]));
  } catch (error) {
    await client.query('ROLLBACK');
    return next(error);
  } finally {
    client.release();
  }
}

async function updateItemOrder(req, res, next) {
  try {
    const sortOrder = int(req.body.sort_order ?? req.body.sortOrder, null);
    if (sortOrder === null) return res.status(400).json({ error: 'sort_order e obrigatorio.' });
    const result = await pool.query(
      `UPDATE learning_module_items
       SET sort_order = $2, updated_at = NOW()
       WHERE id = $1
       RETURNING id, module_id, item_type, song_id, title, instructions, sort_order, active, created_at, updated_at`,
      [req.params.id, sortOrder]
    );
    if (result.rowCount === 0) return res.status(404).json({ error: 'Item nao encontrado.' });
    return res.json(toPublicItem(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function deleteItem(req, res, next) {
  try {
    const result = await pool.query(
      'DELETE FROM learning_module_items WHERE id = $1 RETURNING id',
      [req.params.id]
    );
    if (result.rowCount === 0) return res.status(404).json({ error: 'Item nao encontrado.' });
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  listPublic,
  listStaff,
  createModule,
  updateModule,
  createItem,
  updateItem,
  updateItemOrder,
  deleteItem,
};
