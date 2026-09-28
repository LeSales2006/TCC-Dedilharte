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

  try {
    const payload = jwt.verify(token, secret);
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
    return res.status(401).json({ error: 'Token de autenticacao invalido.' });
  }
}

function requireSelfOrAdmin(paramName) {
  return (req, res, next) => {
    const requestedUserId = req.params[paramName];
    if (!req.user || !req.user.id) {
      return res.status(401).json({ error: 'Token de autenticacao ausente.' });
    }
    if (req.user.role === 'admin' || req.user.role === 'configurator' || req.user.id === requestedUserId) {
      return next();
    }
    return res.status(403).json({ error: 'Acesso negado.' });
  };
}

function requireAdmin(req, res, next) {
  if (!req.user || req.user.role !== 'admin') {
    return res.status(403).json({ error: 'Acesso restrito a administradores.' });
  }
  return next();
}

function requireStaff(req, res, next) {
  if (!req.user || !['admin', 'configurator'].includes(req.user.role)) {
    return res.status(403).json({ error: 'Acesso restrito a equipe.' });
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
};
