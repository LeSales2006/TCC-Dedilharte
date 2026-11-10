const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const pool = require('../db');
const email = require('../services/resetEmail');
const { validatePassword } = require('./authController');
const GENERIC = 'Enviamos um código de 6 dígitos. Confira sua caixa de entrada e o spam.';
const unavailable = res => res.status(503).json({ error: 'Recuperação temporariamente indisponível. Tente mais tarde.' });
const invalidCode = res => res.status(400).json({ error: 'Código incorreto, expirado ou bloqueado. Solicite outro código.' });
const invalidGrant = res => res.status(400).json({ error: 'Autorização inválida, expirada ou já utilizada. Solicite outro código.' });
const digest = value => crypto.createHash('sha256').update(value).digest('hex');
const uuid = value => typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(value);
function secret() {
  const key = process.env.PASSWORD_RECOVERY_SECRET;
  if (!key || Buffer.byteLength(key) < 32) throw new Error('RECOVERY_NOT_CONFIGURED');
  return key;
}
// A keyed hash prevents offline enumeration of the one-million-code space after a DB leak.
const keyedHash = value => crypto.createHmac('sha256', secret()).update(value).digest('hex');
const equalHash = (a, b) => typeof a === 'string' && a.length === b.length
  && crypto.timingSafeEqual(Buffer.from(a), Buffer.from(b));

async function requestCode(req, res) {
  const address = typeof req.body?.email === 'string' ? req.body.email.trim().toLowerCase() : '';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address) || address.length > 254) return res.status(400).json({ error: 'E-mail inválido.' });
  let client;
  try {
    secret(); email.configuration();
    const id = crypto.randomUUID();
    const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
    const addressHash = keyedHash('email:' + address);
    client = await pool.connect();
    await client.query('BEGIN');
    // Serialize sends across instances; user-first locks match verify/change/reset.
    await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1, 0))', [addressHash]);
    const result = await client.query("SELECT id, token_version FROM users WHERE LOWER(email) = $1 AND account_status = 'active' FOR UPDATE", [address]);
    const user = result.rows[0];
    await client.query(`UPDATE password_recovery_requests SET used_at = clock_timestamp(), code_hash = NULL, authorization_hash = NULL
      WHERE used_at IS NULL AND (email_hash = $1 OR user_id = $2)`, [addressHash, user?.id || null]);
    await client.query(`INSERT INTO password_recovery_requests (id, email_hash, user_id, token_version, code_hash)
      VALUES ($1, $2, $3, $4, $5)`, [id, addressHash, user?.id || null, user?.token_version ?? null, keyedHash(id + ':' + code)]);
    // Decoys have the same storage/email/verification paths. Failures never report success.
    await email.send(address, code, id);
    await client.query('COMMIT');
    return res.status(202).json({ message: GENERIC, requestId: id, expiresIn: 600, resendAfter: 60 });
  } catch (_) {
    if (client) await client.query('ROLLBACK').catch(() => {});
    return unavailable(res);
  } finally { client?.release(); }
}

async function lockedRequest(client, id) {
  const initial = (await client.query('SELECT user_id FROM password_recovery_requests WHERE id = $1', [id])).rows[0];
  if (!initial) return null;
  let user;
  if (initial.user_id) user = (await client.query('SELECT id, token_version, account_status FROM users WHERE id = $1 FOR UPDATE', [initial.user_id])).rows[0];
  const row = (await client.query(`SELECT *, expires_at > clock_timestamp() AS code_active,
    authorization_expires_at > clock_timestamp() AS authorization_active
    FROM password_recovery_requests WHERE id = $1 FOR UPDATE`, [id])).rows[0];
  if (!row || row.used_at || (row.user_id && (!user || user.account_status !== 'active' || user.token_version !== row.token_version))) return null;
  return row;
}

async function verifyCode(req, res) {
  const { requestId, code } = req.body || {};
  if (!uuid(requestId) || typeof code !== 'string' || !/^\d{6}$/.test(code)) return invalidCode(res);
  let client;
  try {
    const submitted = keyedHash(requestId + ':' + code);
    client = await pool.connect(); await client.query('BEGIN');
    const row = await lockedRequest(client, requestId);
    if (!row || !row.code_active || row.verified_at || row.attempts >= 5) {
      await client.query('ROLLBACK'); return invalidCode(res);
    }
    if (!equalHash(row.code_hash, submitted)) {
      await client.query('UPDATE password_recovery_requests SET attempts = attempts + 1 WHERE id = $1', [requestId]);
      await client.query('COMMIT'); return invalidCode(res);
    }
    const authorization = crypto.randomBytes(32).toString('hex');
    await client.query(`UPDATE password_recovery_requests SET code_hash = NULL, verified_at = clock_timestamp(),
      authorization_hash = $2, authorization_expires_at = clock_timestamp() + INTERVAL '5 minutes' WHERE id = $1`, [requestId, digest(authorization)]);
    await client.query('COMMIT');
    return res.json({ authorization, expiresIn: 300 });
  } catch (_) {
    if (client) await client.query('ROLLBACK').catch(() => {});
    return unavailable(res);
  } finally { client?.release(); }
}

function passwordError(body) {
  return validatePassword(body.newPassword) || (body.newPassword !== body.confirmPassword ? 'As senhas devem ser iguais.' : null);
}

async function reset(req, res, next) {
  const body = req.body || {};
  if (!uuid(body.requestId) || typeof body.authorization !== 'string' || !/^[a-f0-9]{64}$/.test(body.authorization)) return invalidGrant(res);
  const error = passwordError(body);
  if (error) return res.status(400).json({ error });
  let client;
  try {
    client = await pool.connect(); await client.query('BEGIN');
    const row = await lockedRequest(client, body.requestId);
    if (!row || !row.verified_at || !row.authorization_active || !equalHash(row.authorization_hash, digest(body.authorization))) {
      await client.query('ROLLBACK'); return invalidGrant(res);
    }
    // Do the same expensive hash for decoys; no account existence signal in the response.
    const passwordHash = await bcrypt.hash(body.newPassword, 12);
    if (row.user_id) await replacePassword(client, row.user_id, passwordHash);
    await client.query(`UPDATE password_recovery_requests SET used_at = clock_timestamp(), authorization_hash = NULL,
      code_hash = NULL WHERE id = $1`, [body.requestId]);
    await client.query('COMMIT');
    return res.json({ message: 'Se houver uma conta elegível, a senha foi atualizada. Volte ao login.' });
  } catch (error) {
    if (client) await client.query('ROLLBACK').catch(() => {});
    return next(error);
  } finally { client?.release(); }
}

async function change(req, res, next) {
  const body = req.body || {};
  const error = passwordError(body);
  if (error || typeof body.currentPassword !== 'string' || Buffer.byteLength(body.currentPassword) > 72) {
    return res.status(400).json({ error: error || 'Informe a senha atual.' });
  }
  const client = await pool.connect().catch(next);
  if (!client) return;
  try {
    await client.query('BEGIN');
    const result = await client.query('SELECT password_hash, token_version, account_status FROM users WHERE id = $1 FOR UPDATE', [req.user.id]);
    const row = result.rows[0];
    if (!row || row.account_status !== 'active' || row.token_version !== req.user.tokenVersion) {
      await client.query('ROLLBACK'); return res.status(401).json({ error: 'Sessão expirada. Entre novamente.' });
    }
    if (!row.password_hash || !await bcrypt.compare(body.currentPassword, row.password_hash)) {
      await client.query('ROLLBACK'); return res.status(400).json({ error: 'Senha atual incorreta.' });
    }
    await replacePassword(client, req.user.id, await bcrypt.hash(body.newPassword, 12));
    await client.query('COMMIT');
    return res.json({ message: 'Senha alterada. Entre novamente em seus dispositivos.' });
  } catch (error) { await client.query('ROLLBACK'); return next(error); }
  finally { client.release(); }
}
async function replacePassword(client, id, passwordHash) {
  await client.query(`UPDATE users SET password_hash = $2, managed_password = NULL,
    must_change_password = FALSE, token_version = token_version + 1, updated_at = NOW() WHERE id = $1`, [id, passwordHash]);
  await client.query(`UPDATE password_recovery_requests SET used_at = clock_timestamp(), code_hash = NULL,
    authorization_hash = NULL WHERE user_id = $1 AND used_at IS NULL`, [id]);
}
module.exports = { requestCode, verifyCode, reset, change };
