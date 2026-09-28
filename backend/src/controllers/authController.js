const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const pool = require('../db');

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_LETTER_REGEX = /[A-Za-z]/;
const PASSWORD_NUMBER_REGEX = /\d/;

function normalizeName(name) {
  return typeof name === 'string' ? name.trim() : '';
}

function normalizeEmail(email) {
  return typeof email === 'string' ? email.trim().toLowerCase() : '';
}

function validateName(name) {
  if (!name || name.length < 2) {
    return 'Nome deve ter pelo menos 2 caracteres.';
  }
  return null;
}

function validateEmail(email) {
  if (!email || !EMAIL_REGEX.test(email)) {
    return 'E-mail invalido.';
  }
  return null;
}

function validatePassword(password) {
  if (typeof password !== 'string' || password.length < 8) {
    return 'Senha deve ter pelo menos 8 caracteres.';
  }
  if (!PASSWORD_LETTER_REGEX.test(password) || !PASSWORD_NUMBER_REGEX.test(password)) {
    return 'Senha deve conter pelo menos uma letra e um numero.';
  }
  return null;
}

function toAuthenticatedUser(row) {
  return {
    id: row.id,
    name: row.name,
    email: row.email,
    role: row.role,
    weeklyGoal: row.weekly_goal || 3,
    mustChangePassword: Boolean(row.must_change_password),
    hasPhoto: Boolean(row.has_photo),
    profilePhotoUpdatedAt: row.profile_photo_updated_at || null,
  };
}

function signToken(user) {
  if (!process.env.JWT_SECRET) {
    throw new Error('JWT_SECRET nao configurado.');
  }
  return jwt.sign(
    {
      role: user.role,
      tokenVersion: Number(user.tokenVersion || user.token_version || 0),
    },
    process.env.JWT_SECRET,
    {
      subject: user.id,
      expiresIn: '7d',
    }
  );
}

function ensureJwtSecret(res) {
  if (process.env.JWT_SECRET) {
    return true;
  }
  res.status(500).json({ error: 'JWT_SECRET nao configurado.' });
  return false;
}

async function register(req, res, next) {
  try {
    const name = normalizeName(req.body.name);
    const email = normalizeEmail(req.body.email);
    const password = req.body.password;

    const validationError = validateName(name) || validateEmail(email) || validatePassword(password);
    if (validationError) {
      return res.status(400).json({ error: validationError });
    }
    if (!ensureJwtSecret(res)) {
      return undefined;
    }

    const existingUser = await pool.query(
      'SELECT id FROM users WHERE LOWER(email) = LOWER($1)',
      [email]
    );
    if (existingUser.rowCount > 0) {
      return res.status(409).json({ error: 'Este e-mail já está cadastrado.' });
    }

    const id = crypto.randomUUID();
    const passwordHash = await bcrypt.hash(password, 12);
    const result = await pool.query(
      `INSERT INTO users (id, name, email, password_hash, role, updated_at)
       VALUES ($1, $2, $3, $4, 'student', NOW())
       RETURNING id, name, email, role, weekly_goal, token_version, must_change_password,
                 (profile_photo IS NOT NULL) AS has_photo, profile_photo_updated_at`,
      [id, name, email, passwordHash]
    );

    const user = toAuthenticatedUser(result.rows[0]);
    user.tokenVersion = Number(result.rows[0].token_version || 0);
    return res.status(201).json({
      token: signToken(user),
      user,
    });
  } catch (error) {
    return next(error);
  }
}

async function login(req, res, next) {
  try {
    const email = normalizeEmail(req.body.email);
    const password = req.body.password;

    if (validateEmail(email) || typeof password !== 'string' || password.length === 0) {
      return res.status(400).json({ error: 'Dados invalidos.' });
    }
    if (!ensureJwtSecret(res)) {
      return undefined;
    }

    const result = await pool.query(
      `SELECT id, name, email, password_hash, role, weekly_goal, account_status,
              token_version, must_change_password,
              (profile_photo IS NOT NULL) AS has_photo, profile_photo_updated_at
       FROM users
       WHERE LOWER(email) = LOWER($1)`,
      [email]
    );

    const row = result.rows[0];
    const passwordMatches = row && row.password_hash
      ? await bcrypt.compare(password, row.password_hash)
      : false;

    if (!row || !passwordMatches) {
      return res.status(401).json({ error: 'E-mail ou senha inválidos.' });
    }

    if (row.account_status === 'blocked') {
      return res.status(403).json({ error: 'Conta bloqueada.' });
    }

    const user = toAuthenticatedUser(row);
    user.tokenVersion = Number(row.token_version || 0);
    return res.json({
      token: signToken(user),
      user,
    });
  } catch (error) {
    return next(error);
  }
}

async function me(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT id, name, email, role, weekly_goal, must_change_password,
              (profile_photo IS NOT NULL) AS has_photo, profile_photo_updated_at
       FROM users
       WHERE id = $1`,
      [req.user.id]
    );
    if (result.rowCount === 0) {
      return res.status(401).json({ error: 'Sessao invalida.' });
    }
    return res.json({ user: toAuthenticatedUser(result.rows[0]) });
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  register,
  login,
  me,
};
