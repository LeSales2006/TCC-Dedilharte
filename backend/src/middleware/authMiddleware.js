const jwt = require('jsonwebtoken');

function jwtSecret() {
  return process.env.JWT_SECRET;
}

function authenticateToken(req, res, next) {
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
    req.user = {
      id: payload.sub,
      role: payload.role,
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
    if (req.user.role === 'admin' || req.user.id === requestedUserId) {
      return next();
    }
    return res.status(403).json({ error: 'Acesso negado.' });
  };
}

function requireAdmin(req, res, next) {
  if (!req.user || req.user.role !== 'admin') {
    return res.status(403).json({ error: 'Acesso negado.' });
  }
  return next();
}

module.exports = {
  authenticateToken,
  requireSelfOrAdmin,
  requireAdmin,
};
