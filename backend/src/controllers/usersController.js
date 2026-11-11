const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const pool = require('../db');
const profile = require('../profilePreferences');

const MANAGED_ROLES = new Set(['student', 'admin', 'configurator']);
const STATUS = new Set(['active', 'blocked']);
const PHOTO_MIMES = new Set(['image/jpeg', 'image/png']);
const MAX_PHOTO_BYTES = 1024 * 1024;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeEmail(value) {
  return typeof value === 'string' ? value.trim().toLowerCase() : '';
}

function parseUpdatedAtMillis(value) {
  const millis = Number(value);
  return Number.isFinite(millis) && millis > 0 ? new Date(millis) : new Date();
}

function parseWeeklyGoal(value) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= 7 ? parsed : null;
}

function toUser(row) {
  return {
    id: row.id,
    name: row.name,
    nickname: row.name,
    email: row.email,
    role: row.role,
    weeklyGoal: row.weekly_goal || 3,
    ...profile.fields(row),
    accountStatus: row.account_status || 'active',
    mustChangePassword: Boolean(row.must_change_password),
    primaryConfigurator: Boolean(row.is_primary_configurator),
    hasPhoto: Boolean(row.has_photo),
    profilePhotoUpdatedAt: row.profile_photo_updated_at || null,
    created_at: row.created_at,
    updated_at: row.updated_at,
    updatedAtMillis: row.updated_at ? new Date(row.updated_at).getTime() : Date.now(),
  };
}

function protectedAccountError(res) {
  return res.status(403).json({ error: 'A conta principal do configurador e protegida e nao pode ser alterada, bloqueada ou excluida.' });
}

async function upsertUser(req, res, next) {
  try {
    const id = normalizeText(req.body.id);
    const name = normalizeText(req.body.name);
    const isConfigurator = req.user && req.user.role === 'configurator';
    const weeklyGoal = req.body.weeklyGoal === undefined || isConfigurator ? null : parseWeeklyGoal(req.body.weeklyGoal);
    const updatedAt = parseUpdatedAtMillis(req.body.updatedAtMillis);

    if (!id || !name) {
      return res.status(400).json({ error: 'id e name sao obrigatorios.' });
    }
    if (req.body.weeklyGoal !== undefined && !isConfigurator && weeklyGoal === null) {
      return res.status(400).json({ error: 'weeklyGoal deve estar entre 1 e 7.' });
    }
    if (!req.user || req.user.id !== id) {
      return res.status(403).json({ error: 'Acesso negado.' });
    }

    const result = await pool.query(
      `INSERT INTO users (id, name, weekly_goal, updated_at)
       VALUES ($1, $2, COALESCE($3, 3), $4)
       ON CONFLICT (id) DO UPDATE
       SET name = CASE WHEN EXCLUDED.updated_at >= users.updated_at THEN EXCLUDED.name ELSE users.name END,
           weekly_goal = CASE WHEN EXCLUDED.updated_at >= users.updated_at THEN EXCLUDED.weekly_goal ELSE users.weekly_goal END,
           updated_at = GREATEST(users.updated_at, EXCLUDED.updated_at)
       RETURNING id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled, account_status, must_change_password,
                 is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo, profile_photo_updated_at, created_at, updated_at`,
      [id, name, weeklyGoal, updatedAt]
    );

    return res.json(toUser(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function getUser(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled, account_status, must_change_password,
              is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo, profile_photo_updated_at, created_at, updated_at
       FROM users
       WHERE id = $1`,
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
    const name = normalizeText(req.body.name);
    const isConfigurator = req.user && req.user.role === 'configurator';
    const weeklyGoal = req.body.weeklyGoal === undefined || isConfigurator ? null : parseWeeklyGoal(req.body.weeklyGoal);
    const updatedAt = parseUpdatedAtMillis(req.body.updatedAtMillis);

    if (!name) {
      return res.status(400).json({ error: 'name e obrigatorio.' });
    }
    if (req.body.weeklyGoal !== undefined && !isConfigurator && weeklyGoal === null) {
      return res.status(400).json({ error: 'weeklyGoal deve estar entre 1 e 7.' });
    }

    const result = await pool.query(
      `UPDATE users
       SET name = CASE WHEN $4 >= updated_at THEN $2 ELSE name END,
           weekly_goal = CASE WHEN $4 >= updated_at THEN COALESCE($3, weekly_goal) ELSE weekly_goal END,
           updated_at = GREATEST(updated_at, $4)
       WHERE id = $1
       RETURNING id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled, account_status, must_change_password,
                 is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo, profile_photo_updated_at, created_at, updated_at`,
      [req.params.id, name, weeklyGoal, updatedAt]
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
    const userResult = await pool.query(
      'SELECT id, role, account_status, is_primary_configurator FROM users WHERE id = $1',
      [req.params.id]
    );
    if (userResult.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario nao encontrado.' });
    }
    const target = userResult.rows[0];
    if (target.is_primary_configurator) {
      return protectedAccountError(res);
    }
    if (req.user.id === req.params.id && req.user.role === 'configurator') {
      return res.status(403).json({ error: 'Use a exclusao da propria conta com confirmacao de senha.' });
    }
    if (req.user.id !== req.params.id && req.user.role !== 'configurator') {
      return res.status(403).json({ error: 'Acesso negado.' });
    }
    if (req.user.role === 'configurator' && target.account_status !== 'blocked') {
      return res.status(409).json({ error: 'Bloqueie o usuario antes de excluir.' });
    }
    await anonymizeUser(req.params.id);
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

async function getProfile(req, res, next) {
  req.params.id = req.user.id;
  return getUser(req, res, next);
}

async function updateProfile(req, res, next) {
  try {
    const body = req.body || {};
    const name = normalizeText(body.name);
    if (name.length < 2 || name.length > 180) {
      return res.status(400).json({ error: 'Nome deve ter entre 2 e 180 caracteres.' });
    }

    let email = null;
    if (body.email !== undefined) {
      email = normalizeEmail(body.email);
      if (!email || email.length > 254 || !EMAIL_REGEX.test(email)) {
        return res.status(400).json({ error: 'E-mail inválido.' });
      }
    }

    // Configurators only manage their own name/e-mail here. Student preferences are not theirs.
    if (req.user.role === 'configurator') {
      const result = await pool.query(
        `UPDATE users SET name = $2, email = COALESCE($3, email), updated_at = NOW()
         WHERE id = $1
         RETURNING id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled, account_status, must_change_password,
                   is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo, profile_photo_updated_at, created_at, updated_at`,
        [req.user.id, name, email]
      );
      if (!result.rowCount) return res.status(404).json({ error: 'Usuário não encontrado.' });
      return res.json(toUser(result.rows[0]));
    }

    const options = profile.preferences(body);
    if (options.error) return res.status(400).json({ error: options.error });
    const result = await pool.query(
      `UPDATE users SET name = $2,
       email = COALESCE($3, email),
       weekly_goal = COALESCE($4, weekly_goal), initial_level = COALESCE($5, initial_level),
       song_goal = COALESCE($6, song_goal), notifications_enabled = COALESCE($7, notifications_enabled), updated_at = NOW()
       WHERE id = $1 RETURNING id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled,
       account_status, must_change_password, is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo,
       profile_photo_updated_at, created_at, updated_at`,
      [req.user.id, name, email, options.weeklyGoal, options.initialLevel, options.songGoal, options.notificationsEnabled]
    );
    if (!result.rowCount) return res.status(404).json({ error: 'Usuário não encontrado.' });
    return res.json(toUser(result.rows[0]));
  } catch (error) {
    if (error.code === '23505') {
      return res.status(409).json({ error: 'Este e-mail já está cadastrado.' });
    }
    return next(error);
  }
}

async function uploadMyPhoto(req, res, next) {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'Arquivo de foto obrigatorio.' });
    }
    if (!PHOTO_MIMES.has(req.file.mimetype)) {
      return res.status(400).json({ error: 'Use imagem JPEG ou PNG.' });
    }
    if (req.file.size > MAX_PHOTO_BYTES) {
      return res.status(413).json({ error: 'Foto deve ter no maximo 1 MB.' });
    }
    const result = await pool.query(
      `UPDATE users
       SET profile_photo = $2,
           profile_photo_mime = $3,
           profile_photo_updated_at = NOW(),
           updated_at = NOW()
       WHERE id = $1
       RETURNING id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled, account_status, must_change_password,
                 is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo, profile_photo_updated_at, created_at, updated_at`,
      [req.user.id, req.file.buffer, req.file.mimetype]
    );
    return res.json(toUser(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function getMyPhoto(req, res, next) {
  try {
    const result = await pool.query(
      'SELECT profile_photo, profile_photo_mime FROM users WHERE id = $1 AND profile_photo IS NOT NULL',
      [req.user.id]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Foto nao encontrada.' });
    }
    res.set('Content-Type', result.rows[0].profile_photo_mime || 'application/octet-stream');
    return res.send(result.rows[0].profile_photo);
  } catch (error) {
    return next(error);
  }
}

async function deleteMyPhoto(req, res, next) {
  try {
    await pool.query(
      `UPDATE users
       SET profile_photo = NULL,
           profile_photo_mime = NULL,
           profile_photo_updated_at = NOW(),
           updated_at = NOW()
       WHERE id = $1`,
      [req.user.id]
    );
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

async function listManagedUsers(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled, account_status, must_change_password,
              is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo,
              profile_photo_updated_at, created_at, updated_at
       FROM users
       WHERE email IS NOT NULL
       ORDER BY LOWER(name), LOWER(email)`
    );
    return res.json({ items: result.rows.map(toUser) });
  } catch (error) {
    return next(error);
  }
}

async function createManagedUser(req, res, next) {
  try {
    const name = normalizeText(req.body.name || req.body.nome);
    const email = normalizeEmail(req.body.email);
    const password = req.body.password || req.body.temporaryPassword || req.body.senhaTemporaria;
    const role = normalizeText(req.body.role).toLowerCase();
    if (!name || !email || typeof password !== 'string' || password.length < 8) {
      return res.status(400).json({ error: 'Nome, e-mail e senha temporaria valida sao obrigatorios.' });
    }
    if (!EMAIL_REGEX.test(email) || email.length > 254) {
      return res.status(400).json({ error: 'E-mail invalido.' });
    }
    if (Buffer.byteLength(password, 'utf8') > 72) {
      return res.status(400).json({ error: 'Senha deve ter no maximo 72 bytes.' });
    }
    if (!MANAGED_ROLES.has(role)) {
      return res.status(400).json({ error: 'Role permitida: student, admin ou configurator.' });
    }
    const passwordHash = await bcrypt.hash(password, 12);
    const result = await pool.query(
      `INSERT INTO users (id, name, email, password_hash, role, must_change_password, updated_at)
       VALUES ($1, $2, $3, $4, $5, TRUE, NOW())
       RETURNING id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled, account_status, must_change_password,
                 is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo,
                 profile_photo_updated_at, created_at, updated_at`,
      [crypto.randomUUID(), name, email, passwordHash, role]
    );
    return res.status(201).json(toUser(result.rows[0]));
  } catch (error) {
    if (error.code === '23505') {
      return res.status(409).json({ error: 'Este e-mail ja esta cadastrado.' });
    }
    return next(error);
  }
}

async function updateManagedRole(req, res, next) {
  try {
    const role = normalizeText(req.body.role).toLowerCase();
    if (!MANAGED_ROLES.has(role)) {
      return res.status(400).json({ error: 'Role permitida: student, admin ou configurator.' });
    }
    if (req.user.id === req.params.id) {
      return res.status(403).json({ error: 'Voce nao pode alterar sua propria funcao.' });
    }
    const target = await pool.query(
      'SELECT id, is_primary_configurator FROM users WHERE id = $1',
      [req.params.id]
    );
    if (target.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario nao encontrado.' });
    }
    if (target.rows[0].is_primary_configurator) {
      return protectedAccountError(res);
    }
    const result = await pool.query(
      `UPDATE users
       SET role = $2, token_version = token_version + 1, updated_at = NOW()
       WHERE id = $1 AND is_primary_configurator = FALSE
       RETURNING id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled, account_status, must_change_password,
                 is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo,
                 profile_photo_updated_at, created_at, updated_at`,
      [req.params.id, role]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario nao encontrado.' });
    }
    return res.json(toUser(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function updateManagedStatus(req, res, next) {
  try {
    const accountStatus = normalizeText(req.body.account_status || req.body.accountStatus || req.body.status);
    if (!STATUS.has(accountStatus)) {
      return res.status(400).json({ error: 'Status deve ser active ou blocked.' });
    }
    const target = await pool.query(
      'SELECT id, role, is_primary_configurator FROM users WHERE id = $1',
      [req.params.id]
    );
    if (target.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario gerenciavel nao encontrado.' });
    }
    if (target.rows[0].is_primary_configurator) {
      return protectedAccountError(res);
    }
    if (!['student', 'admin'].includes(target.rows[0].role)) {
      return res.status(404).json({ error: 'Usuario gerenciavel nao encontrado.' });
    }
    const result = await pool.query(
      `UPDATE users
       SET account_status = $2,
           blocked_at = CASE WHEN $2 = 'blocked' THEN NOW() ELSE NULL END,
           token_version = token_version + 1,
           updated_at = NOW()
       WHERE id = $1 AND role IN ('student', 'admin') AND is_primary_configurator = FALSE
       RETURNING id, name, email, role, weekly_goal, initial_level, song_goal, notifications_enabled, account_status, must_change_password,
                 is_primary_configurator, (profile_photo IS NOT NULL) AS has_photo, profile_photo_updated_at, created_at, updated_at`,
      [req.params.id, accountStatus]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario gerenciavel nao encontrado.' });
    }
    return res.json(toUser(result.rows[0]));
  } catch (error) {
    return next(error);
  }
}

async function deleteManagedUser(req, res, next) {
  try {
    const result = await pool.query(
      `SELECT id, account_status, role, is_primary_configurator
       FROM users
       WHERE id = $1`,
      [req.params.id]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario gerenciavel nao encontrado.' });
    }
    const target = result.rows[0];
    if (target.is_primary_configurator) {
      return protectedAccountError(res);
    }
    if (!['student', 'admin'].includes(target.role)) {
      return res.status(404).json({ error: 'Usuario gerenciavel nao encontrado.' });
    }
    if (target.account_status !== 'blocked') {
      return res.status(409).json({ error: 'Bloqueie o usuario antes de excluir.' });
    }
    await anonymizeUser(req.params.id);
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

async function deleteOwnAccount(req, res, next) {
  try {
    const password = req.body && (req.body.password || req.body.currentPassword);
    if (typeof password !== 'string' || password.length === 0 || Buffer.byteLength(password, 'utf8') > 72) {
      return res.status(400).json({ error: 'Informe sua senha atual para confirmar a exclusao.' });
    }
    const result = await pool.query(
      'SELECT id, password_hash, is_primary_configurator FROM users WHERE id = $1',
      [req.user.id]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Usuario nao encontrado.' });
    }
    const target = result.rows[0];
    if (target.is_primary_configurator) {
      return protectedAccountError(res);
    }
    if (!target.password_hash || !await bcrypt.compare(password, target.password_hash)) {
      return res.status(400).json({ error: 'Senha incorreta.' });
    }
    await anonymizeUser(req.user.id);
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

async function anonymizeUser(userId) {
  const anonymousName = `Usuario removido ${String(userId).slice(0, 8)}`;
  await pool.query(
    `UPDATE users
     SET name = $2,
         email = NULL,
         password_hash = NULL,
         profile_photo = NULL,
         profile_photo_mime = NULL,
         profile_photo_updated_at = NOW(),
         account_status = 'blocked',
         token_version = token_version + 1,
         updated_at = NOW()
     WHERE id = $1 AND is_primary_configurator = FALSE`,
    [userId, anonymousName]
  );
}

module.exports = {
  upsertUser,
  getUser,
  updateUser,
  deleteUser,
  getProfile,
  updateProfile,
  uploadMyPhoto,
  getMyPhoto,
  deleteMyPhoto,
  listManagedUsers,
  createManagedUser,
  updateManagedRole,
  updateManagedStatus,
  deleteManagedUser,
  deleteOwnAccount,
};
