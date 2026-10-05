const crypto = require('crypto');
const pool = require('../db');

const DIFFICULTIES = new Set(['easy', 'medium', 'hard']);
const DEFAULT_ARPEGGIOS = [
  {
    id: 'arp_c_major',
    title: 'Dó maior (C)',
    chord: 'C',
    notes: [{ string: 5, fret: 3 }, { string: 3, fret: 0 }, { string: 2, fret: 1 }, { string: 1, fret: 0 }],
    difficulty: 'easy',
    sortOrder: 1,
  },
  {
    id: 'arp_d_major',
    title: 'Ré maior (D)',
    chord: 'D',
    notes: [{ string: 4, fret: 0 }, { string: 3, fret: 2 }, { string: 2, fret: 3 }, { string: 1, fret: 2 }],
    difficulty: 'easy',
    sortOrder: 2,
  },
  {
    id: 'arp_e_major',
    title: 'Mi maior (E)',
    chord: 'E',
    notes: [{ string: 6, fret: 0 }, { string: 3, fret: 1 }, { string: 2, fret: 0 }, { string: 1, fret: 0 }],
    difficulty: 'easy',
    sortOrder: 3,
  },
  {
    id: 'arp_f_major',
    title: 'Fá maior (F)',
    chord: 'F',
    notes: [{ string: 6, fret: 1 }, { string: 3, fret: 2 }, { string: 2, fret: 1 }, { string: 1, fret: 1 }],
    difficulty: 'easy',
    sortOrder: 4,
  },
  {
    id: 'arp_g_major',
    title: 'Sol maior (G)',
    chord: 'G',
    notes: [{ string: 6, fret: 3 }, { string: 3, fret: 0 }, { string: 2, fret: 0 }, { string: 1, fret: 3 }],
    difficulty: 'easy',
    sortOrder: 5,
  },
  {
    id: 'arp_a_major',
    title: 'Lá maior (A)',
    chord: 'A',
    notes: [{ string: 5, fret: 0 }, { string: 3, fret: 2 }, { string: 2, fret: 2 }, { string: 1, fret: 0 }],
    difficulty: 'easy',
    sortOrder: 6,
  },
  {
    id: 'arp_b_major',
    title: 'Si maior (B)',
    chord: 'B',
    notes: [{ string: 5, fret: 2 }, { string: 3, fret: 4 }, { string: 2, fret: 4 }, { string: 1, fret: 2 }],
    difficulty: 'easy',
    sortOrder: 7,
  },
];

function normalizeDifficulty(value) {
  return DIFFICULTIES.has(value) ? value : 'easy';
}

function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeId(value) {
  const id = normalizeText(value);
  if (!id || id === 'undefined' || id === 'null') {
    return '';
  }
  return id;
}

function parseNotes(value) {
  const notes = Array.isArray(value) ? value : [];
  if (notes.length === 0) {
    return { error: 'notes deve conter pelo menos uma nota.' };
  }
  const parsed = [];
  for (const note of notes) {
    const stringNumber = Number(note.string);
    const fret = Number(note.fret);
    if (!Number.isInteger(stringNumber) || stringNumber < 1 || stringNumber > 6) {
      return { error: 'Cada nota deve ter string entre 1 e 6.' };
    }
    if (!Number.isInteger(fret) || fret < 0) {
      return { error: 'Cada nota deve ter fret maior ou igual a 0.' };
    }
    parsed.push({ string: stringNumber, fret });
  }
  return { notes: parsed };
}

function toArpeggio(row) {
  return {
    id: row.id,
    title: row.title,
    chord: row.chord,
    notes: row.notes,
    difficulty: row.difficulty,
    active: row.active,
    sortOrder: row.sort_order,
    created_at: row.created_at,
    updated_at: row.updated_at,
  };
}

async function ensureDefaultArpeggiosWhenEmpty() {
  const count = await pool.query('SELECT COUNT(*)::INT AS total FROM arpeggios WHERE active = TRUE');
  if (Number(count.rows[0]?.total || 0) > 0) {
    return;
  }

  for (const arpeggio of DEFAULT_ARPEGGIOS) {
    await pool.query(
      `INSERT INTO arpeggios (id, title, chord, notes, difficulty, active, sort_order, updated_at)
       VALUES ($1, $2, $3, $4::jsonb, $5, TRUE, $6, NOW())
       ON CONFLICT (id) DO UPDATE
       SET title = EXCLUDED.title,
           chord = EXCLUDED.chord,
           notes = EXCLUDED.notes,
           difficulty = EXCLUDED.difficulty,
           active = TRUE,
           sort_order = EXCLUDED.sort_order,
           updated_at = NOW()`,
      [
        arpeggio.id,
        arpeggio.title,
        arpeggio.chord,
        JSON.stringify(arpeggio.notes),
        arpeggio.difficulty,
        arpeggio.sortOrder,
      ]
    );
  }
}

async function listPublic(req, res, next) {
  try {
    await ensureDefaultArpeggiosWhenEmpty();
    const result = await pool.query(
      `SELECT id, title, chord, notes, difficulty, active, sort_order, created_at, updated_at
       FROM arpeggios
       WHERE active = TRUE
       ORDER BY sort_order ASC, created_at ASC`
    );
    return res.json({ items: result.rows.map(toArpeggio) });
  } catch (error) {
    return next(error);
  }
}

async function getPublic(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT id, title, chord, notes, difficulty, active, sort_order, created_at, updated_at
       FROM arpeggios
       WHERE id = $1 AND active = TRUE`,
      [req.params.id]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Arpejo nao encontrado.' });
    }
    return res.json(toArpeggio(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function listAdmin(req, res, next) {
  try {
    await ensureDefaultArpeggiosWhenEmpty();
    const result = await pool.query(
      `SELECT id, title, chord, notes, difficulty, active, sort_order, created_at, updated_at
       FROM arpeggios
       WHERE active = TRUE
       ORDER BY sort_order ASC, created_at ASC`
    );
    return res.json({ items: result.rows.map(toArpeggio) });
  } catch (error) {
    return next(error);
  }
}

async function createAdmin(req, res, next) {
  try {
    const title = normalizeText(req.body.title);
    const chord = normalizeText(req.body.chord);
    const parsed = parseNotes(req.body.notes);
    const difficulty = normalizeDifficulty(req.body.difficulty);
    const active = req.body.active === undefined ? true : Boolean(req.body.active);
    const sortOrder = Number.isInteger(req.body.sortOrder) ? req.body.sortOrder : 0;

    if (!title) {
      return res.status(400).json({ error: 'title e obrigatorio.' });
    }
    if (parsed.error) {
      return res.status(400).json({ error: parsed.error });
    }

    const id = normalizeText(req.body.id) || `arp_${crypto.randomUUID()}`;
    const result = await pool.query(
      `INSERT INTO arpeggios (id, title, chord, notes, difficulty, active, sort_order, updated_at)
       VALUES ($1, $2, $3, $4::jsonb, $5, $6, $7, NOW())
       RETURNING id, title, chord, notes, difficulty, active, sort_order, created_at, updated_at`,
      [id, title, chord, JSON.stringify(parsed.notes), difficulty, active, sortOrder]
    );
    return res.status(201).json(toArpeggio(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function updateAdmin(req, res, next) {
  try {
    const title = normalizeText(req.body.title);
    const chord = normalizeText(req.body.chord);
    const parsed = parseNotes(req.body.notes);
    const difficulty = normalizeDifficulty(req.body.difficulty);
    const active = req.body.active === undefined ? true : Boolean(req.body.active);
    const sortOrder = Number.isInteger(req.body.sortOrder) ? req.body.sortOrder : 0;

    if (!title) {
      return res.status(400).json({ error: 'title e obrigatorio.' });
    }
    if (parsed.error) {
      return res.status(400).json({ error: parsed.error });
    }

    const result = await pool.query(
      `UPDATE arpeggios
       SET title = $2,
           chord = $3,
           notes = $4::jsonb,
           difficulty = $5,
           active = $6,
           sort_order = $7,
           updated_at = NOW()
       WHERE id = $1
       RETURNING id, title, chord, notes, difficulty, active, sort_order, created_at, updated_at`,
      [req.params.id, title, chord, JSON.stringify(parsed.notes), difficulty, active, sortOrder]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Arpejo nao encontrado.' });
    }
    return res.json(toArpeggio(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function deleteAdmin(req, res, next) {
  try {
    const id = normalizeId(req.params.id);
    if (!id) {
      return res.status(400).json({ error: 'ID do arpejo e obrigatorio.' });
    }

    const result = await pool.query(
      `UPDATE arpeggios
       SET active = FALSE, updated_at = NOW()
       WHERE id = $1 AND active = TRUE
       RETURNING id`,
      [id]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Arpejo nao encontrado.' });
    }
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  listPublic,
  getPublic,
  listAdmin,
  createAdmin,
  updateAdmin,
  deleteAdmin,
};
