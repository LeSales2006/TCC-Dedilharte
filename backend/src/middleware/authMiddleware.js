const jwt = require('jsonwebtoken');
const pool = require('../db');

function jwtSecret() {
  return process.env.JWT_SECRET;
}

async function authenticateToken(req, res, next) {
  const header = req.headers.authorization || '';
  const [scheme, token] = header.split(' ');

  if (scheme !== 'Bearer' || !token) {
    return res.status(401).json({ error: 'Token de autenticacao ausente.' });
  }

  const secret = jwtSecret();
  if (!secret) {
    return res.status(500).json({ error: 'JWT_SECRET nao configurado.' });
  }

  let payload;
  try {
    payload = jwt.verify(token, secret, { algorithms: ['HS256'] });
    if (typeof payload.sub !== 'string' || !payload.sub.trim()) {
      return res.status(401).json({ error: 'Token de autenticacao invalido.' });
    }
  } catch (error) {
    return res.status(401).json({ error: 'Token de autenticacao invalido.' });
  }
  try {
    const result = await pool.query(
      `SELECT id, role, account_status, token_version
       FROM users
       WHERE id = $1`,
      [payload.sub]
    );
    if (result.rowCount === 0) {
      return res.status(401).json({ error: 'Sessao invalida.' });
    }
    const row = result.rows[0];
    if (row.account_status === 'blocked') {
      return res.status(403).json({ error: 'Conta bloqueada.' });
    }
    const tokenVersion = Number(payload.tokenVersion || payload.token_version || 0);
    if (Number(row.token_version || 0) !== tokenVersion) {
      return res.status(401).json({ error: 'Sessao expirada.' });
    }
    req.user = {
      id: row.id,
      role: row.role,
      tokenVersion: Number(row.token_version || 0),
    };
    return next();
  } catch (error) {
    // Database outages must not invalidate an otherwise valid session.
    return next(error);
  }
}

function requireSelfOrAdmin(paramName) {
  return (req, res, next) => {
    const requestedUserId = req.params[paramName];
    if (!req.user || !req.user.id) {
      return res.status(401).json({ error: 'Token de autenticacao ausente.' });
    }
    if (req.user.role === 'admin' || req.user.id === requestedUserId) {
      return next();
    }
    return res.status(403).json({ error: 'Acesso negado.' });
  };
}

// Configurators only manage accounts and their own settings; student content is off limits.
function requireStudentOrAdmin(req, res, next) {
  if (!req.user || !['student', 'admin'].includes(req.user.role)) {
    return res.status(403).json({ error: 'Acesso restrito a alunos e administradores.' });
  }
  return next();
}

function requireAdmin(req, res, next) {
  if (!req.user || req.user.role !== 'admin') {
    return res.status(403).json({ error: 'Acesso restrito a administradores.' });
  }
  return next();
}

function requireStaff(req, res, next) {
  if (!req.user || req.user.role !== 'admin') {
    return res.status(403).json({ error: 'Acesso restrito a administradores.' });
  }
  return next();
}

function requireConfigurator(req, res, next) {
  if (!req.user || req.user.role !== 'configurator') {
    return res.status(403).json({ error: 'Acesso restrito a configuradores.' });
  }
  return next();
}

module.exports = {
  authenticateToken,
  requireSelfOrAdmin,
  requireAdmin,
  requireStaff,
  requireConfigurator,
  requireStudentOrAdmin,
};
