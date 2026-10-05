const crypto = require('crypto');
const pool = require('../db');

const DIFFICULTIES = new Set(['easy', 'medium', 'hard']);
const STATUSES = new Set(['rascunho', 'publicada', 'arquivada']);

function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeDifficulty(value) {
  return DIFFICULTIES.has(value) ? value : 'easy';
}

function normalizeStatus(value) {
  return STATUSES.has(value) ? value : 'rascunho';
}

function normalizeBpm(value) {
  const bpm = Number(value);
  return Number.isInteger(bpm) && bpm > 0 ? bpm : 60;
}

function normalizeOrder(value) {
  const order = Number(value);
  return Number.isInteger(order) && order > 0 ? order : 0;
}

function toSong(row) {
  return {
    id: row.id,
    title: row.title,
    artist: row.artist,
    difficulty: row.difficulty,
    recommendedBpm: row.recommended_bpm,
    tablature: row.tablature,
    queueOrder: row.queue_order,
    inQueue: row.in_queue,
    status: row.status,
    createdAt: row.created_at_millis,
    updatedAt: row.updated_at_millis,
  };
}

function songColumns() {
  return `id,
          title,
          artist,
          difficulty,
          recommended_bpm,
          tablature,
          queue_order,
          in_queue,
          status,
          FLOOR(EXTRACT(EPOCH FROM created_at) * 1000)::BIGINT AS created_at_millis,
          FLOOR(EXTRACT(EPOCH FROM updated_at) * 1000)::BIGINT AS updated_at_millis`;
}

async function listPublic(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT ${songColumns()}
       FROM songs
       WHERE status = 'publicada' AND in_queue = TRUE
       ORDER BY queue_order ASC, created_at ASC`
    );
    return res.json({ items: result.rows.map(toSong) });
  } catch (error) {
    return next(error);
  }
}

async function listAdmin(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT ${songColumns()}
       FROM songs
       ORDER BY queue_order ASC, created_at ASC`
    );
    return res.json({ items: result.rows.map(toSong) });
  } catch (error) {
    return next(error);
  }
}

async function createAdmin(req, res, next) {
  try {
    const title = normalizeText(req.body.title);
    if (!title) {
      return res.status(400).json({ error: 'title e obrigatorio.' });
    }

    const id = normalizeText(req.body.id) || `song_${crypto.randomUUID()}`;
    const result = await pool.query(
      `INSERT INTO songs (
           id, title, artist, difficulty, recommended_bpm, tablature,
           queue_order, in_queue, status, updated_at
       )
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
       RETURNING ${songColumns()}`,
      [
        id,
        title,
        normalizeText(req.body.artist),
        normalizeDifficulty(req.body.difficulty),
        normalizeBpm(req.body.recommendedBpm),
        typeof req.body.tablature === 'string' ? req.body.tablature : '',
        normalizeOrder(req.body.queueOrder),
        req.body.inQueue === undefined ? true : Boolean(req.body.inQueue),
        normalizeStatus(req.body.status),
      ]
    );
    return res.status(201).json(toSong(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function updateAdmin(req, res, next) {
  try {
    const title = normalizeText(req.body.title);
    if (!title) {
      return res.status(400).json({ error: 'title e obrigatorio.' });
    }

    const result = await pool.query(
      `UPDATE songs
       SET title = $2,
           artist = $3,
           difficulty = $4,
           recommended_bpm = $5,
           tablature = $6,
           queue_order = $7,
           in_queue = $8,
           status = $9,
           updated_at = NOW()
       WHERE id = $1
       RETURNING ${songColumns()}`,
      [
        req.params.id,
        title,
        normalizeText(req.body.artist),
        normalizeDifficulty(req.body.difficulty),
        normalizeBpm(req.body.recommendedBpm),
        typeof req.body.tablature === 'string' ? req.body.tablature : '',
        normalizeOrder(req.body.queueOrder),
        req.body.inQueue === undefined ? true : Boolean(req.body.inQueue),
        normalizeStatus(req.body.status),
      ]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Musica nao encontrada.' });
    }
    return res.json(toSong(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function deleteAdmin(req, res, next) {
  try {
    const result = await pool.query('DELETE FROM songs WHERE id = $1 RETURNING id', [req.params.id]);
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Musica nao encontrada.' });
    }
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  listPublic,
  listAdmin,
  createAdmin,
  updateAdmin,
  deleteAdmin,
};
