const crypto = require('crypto');
const pool = require('../db');

const DIFFICULTIES = new Set(['easy', 'medium', 'hard']);
const COLUMNS = 'id, title, artist, difficulty, bpm, tablature, content, active, sort_order, created_by';
const FIELDS = {
  title: 'title', artist: 'artist', difficulty: 'difficulty', bpm: 'bpm',
  tablature: 'tablature', content: 'content', active: 'active', sortOrder: 'sort_order',
};

function toSong(row) {
  return {
    id: row.id, title: row.title, artist: row.artist, difficulty: row.difficulty,
    bpm: row.bpm, tablature: row.tablature, content: row.content,
    active: row.active, sortOrder: row.sort_order, createdBy: row.created_by,
  };
}

function validate(body, creating) {
  const values = creating
    ? { artist: '', difficulty: 'easy', bpm: 60, tablature: '', active: false, sortOrder: 0 }
    : {};
  for (const field of Object.keys(FIELDS)) {
    if (Object.hasOwn(body, field)) values[field] = body[field];
  }
  if (creating || Object.hasOwn(values, 'title')) {
    if (typeof values.title !== 'string' || !values.title.trim() || values.title.trim().length > 180) {
      return { error: 'title deve ter entre 1 e 180 caracteres.' };
    }
    values.title = values.title.trim();
  }
  if (Object.hasOwn(values, 'artist')) {
    if (typeof values.artist !== 'string' || values.artist.trim().length > 180) return { error: 'artist invalido.' };
    values.artist = values.artist.trim();
  }
  if (Object.hasOwn(values, 'difficulty') && !DIFFICULTIES.has(values.difficulty)) return { error: 'difficulty invalida.' };
  if (Object.hasOwn(values, 'bpm') && (!Number.isInteger(values.bpm) || values.bpm <= 0 || values.bpm > 2147483647)) {
    return { error: 'bpm deve ser um inteiro positivo.' };
  }
  if (Object.hasOwn(values, 'tablature') && typeof values.tablature !== 'string') return { error: 'tablature invalida.' };
  if (Object.hasOwn(values, 'active') && typeof values.active !== 'boolean') return { error: 'active deve ser booleano.' };
  if (Object.hasOwn(values, 'sortOrder') && (!Number.isInteger(values.sortOrder) || values.sortOrder < 0 || values.sortOrder > 2147483647)) {
    return { error: 'sortOrder deve ser um inteiro nao negativo.' };
  }
  if (!Object.keys(values).length) return { error: 'Informe os campos da musica.' };
  return { values };
}

async function listPublic(req, res, next) {
  try {
    const result = await pool.query('SELECT ' + COLUMNS + ' FROM songs WHERE active = TRUE ORDER BY sort_order ASC, id ASC');
    return res.json({ items: result.rows.map(toSong) });
  } catch (error) { return next(error); }
}

async function listAdmin(req, res, next) {
  try {
    const result = await pool.query('SELECT ' + COLUMNS + ' FROM songs ORDER BY sort_order ASC, id ASC');
    return res.json({ items: result.rows.map(toSong) });
  } catch (error) { return next(error); }
}

async function createAdmin(req, res, next) {
  try {
    const parsed = validate(req.body || {}, true);
    if (parsed.error) return res.status(400).json({ error: parsed.error });
    const fields = Object.keys(parsed.values);
    const columns = ['id', ...fields.map(field => FIELDS[field]), 'created_by'];
    const params = [crypto.randomUUID(), ...fields.map(field => parsed.values[field]), req.user.id];
    const placeholders = params.map((_, index) => '$' + (index + 1));
    const result = await pool.query(
      'INSERT INTO songs (' + columns.join(', ') + ') VALUES (' + placeholders.join(', ') + ') RETURNING ' + COLUMNS, params
    );
    return res.status(201).json(toSong(result.rows[0]));
  } catch (error) { return next(error); }
}

async function updateAdmin(req, res, next) {
  try {
    const parsed = validate(req.body || {}, false);
    if (parsed.error) return res.status(400).json({ error: parsed.error });
    // Only supplied fields change: editing tablature preserves content and authorship.
    const fields = Object.keys(parsed.values);
    const assignments = fields.map((field, index) => FIELDS[field] + ' = $' + (index + 2));
    const result = await pool.query(
      'UPDATE songs SET ' + assignments.join(', ') + ' WHERE id = $1 RETURNING ' + COLUMNS,
      [req.params.id, ...fields.map(field => parsed.values[field])]
    );
    if (!result.rowCount) return res.status(404).json({ error: 'Musica nao encontrada.' });
    return res.json(toSong(result.rows[0]));
  } catch (error) { return next(error); }
}

async function deleteAdmin(req, res, next) {
  try {
    const result = await pool.query('DELETE FROM songs WHERE id = $1 RETURNING id', [req.params.id]);
    if (!result.rowCount) return res.status(404).json({ error: 'Musica nao encontrada.' });
    return res.status(204).send();
  } catch (error) {
    if (error.code === '23503' || error.code === '23001') {
      return res.status(409).json({ error: 'Esta musica esta vinculada a outros conteudos. Remova os vinculos ou arquive a musica.' });
    }
    return next(error);
  }
}

module.exports = { listPublic, listAdmin, createAdmin, updateAdmin, deleteAdmin };
