const crypto = require('crypto');
const pool = require('../db');

// Shared across Render instances; keys never contain plain emails, IPs or reset tokens.
function rateLimit(scope, maximum, seconds, identity = req => req.ip) {
  return async (req, res, next) => {
    try {
      if (!process.env.JWT_SECRET) return res.status(503).json({ error: 'Serviço indisponível.' });
      const bucket = crypto.createHmac('sha256', process.env.JWT_SECRET)
        .update(scope + ':' + String(identity(req)).slice(0, 320)).digest('hex');
      const result = await pool.query(`INSERT INTO auth_rate_limits (bucket_hash, attempts, expires_at)
        VALUES ($1, 1, NOW() + $2 * INTERVAL '1 second')
        ON CONFLICT (bucket_hash) DO UPDATE SET
          attempts = CASE WHEN auth_rate_limits.expires_at <= NOW() THEN 1 ELSE auth_rate_limits.attempts + 1 END,
          expires_at = CASE WHEN auth_rate_limits.expires_at <= NOW() THEN EXCLUDED.expires_at ELSE auth_rate_limits.expires_at END
        RETURNING attempts, GREATEST(1, CEIL(EXTRACT(EPOCH FROM expires_at - NOW())))::int AS retry`, [bucket, seconds]);
      if (result.rows[0].attempts > maximum) {
        res.set('Retry-After', String(result.rows[0].retry));
        return res.status(429).json({ error: 'Muitas tentativas. Aguarde e tente novamente.' });
      }
      return next();
    } catch (error) { return next(error); }
  };
}
const emailKey = req => typeof req.body?.email === 'string' ? req.body.email.trim().toLowerCase() : '';
module.exports = { rateLimit, emailKey };
