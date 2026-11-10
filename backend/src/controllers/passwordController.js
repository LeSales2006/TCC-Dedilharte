const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const pool = require('../db');
const email = require('../services/resetEmail');
const { validatePassword } = require('./authController');
const GENERIC = 'Se houver uma conta com esse e-mail, enviaremos um link para redefinir a senha.';
const hash = token => crypto.createHash('sha256').update(token).digest('hex');
const invalid = res => res.status(400).json({ error: 'Link inválido, expirado ou já utilizado. Solicite outro.' });

async function forgot(req, res, next) {
  const address = typeof req.body?.email === 'string' ? req.body.email.trim().toLowerCase() : '';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address) || address.length > 254) return res.status(400).json({ error: 'E-mail inválido.' });
  try { email.configuration(); }
  catch (_) { return res.status(503).json({ error: 'Recuperação temporariamente indisponível. Tente mais tarde.' }); }
  let tokenHash;
  try {
    const token = crypto.randomBytes(32).toString('hex'); tokenHash = hash(token);
    const result = await pool.query(`INSERT INTO password_reset_tokens (token_hash, user_id, token_version)
      SELECT $1, id, token_version FROM users WHERE LOWER(email) = $2 AND account_status = 'active'
      RETURNING user_id`, [tokenHash, address]);
    // Exercise the same provider path for every address. An absent account receives
    // a neutral notice without a token; timing/provider failures cannot identify it.
    await email.send(address, result.rowCount ? token : null);
    // Only acknowledge once storage and the provider have accepted the request.
    return res.status(202).json({ message: GENERIC });
  } catch (_) {
    // Do not log address, token, request body, password or provider response.
    console.error('Falha no envio de recuperação de senha');
    if (tokenHash) await pool.query('DELETE FROM password_reset_tokens WHERE token_hash = $1', [tokenHash]).catch(() => {});
    return res.status(503).json({ error: 'Recuperação temporariamente indisponível. Tente mais tarde.' });
  }
}

function passwordError(body) {
  return validatePassword(body.newPassword) || (body.newPassword !== body.confirmPassword ? 'As senhas devem ser iguais.' : null);
}

async function reset(req, res, next) {
  const body = req.body || {};
  if (typeof body.token !== 'string' || !/^[a-f0-9]{64}$/.test(body.token)) return invalid(res);
  const error = passwordError(body);
  if (error) return res.status(400).json({ error });
  const client = await pool.connect().catch(next);
  if (!client) return;
  try {
    await client.query('BEGIN');
    // Lock the user before consuming tokens; concurrent resets/changes serialize on this row.
    const result = await client.query(`SELECT u.id, u.token_version, t.token_version AS issued_version
      FROM users u JOIN password_reset_tokens t ON t.user_id = u.id
      WHERE t.token_hash = $1 AND t.used_at IS NULL AND t.expires_at > NOW()
        AND u.account_status = 'active' FOR UPDATE OF u`, [hash(body.token)]);
    const row = result.rows[0];
    if (!row || row.token_version !== row.issued_version) { await client.query('ROLLBACK'); return invalid(res); }
    const consumed = await client.query(`UPDATE password_reset_tokens SET used_at = NOW()
      WHERE token_hash = $1 AND used_at IS NULL AND expires_at > NOW() RETURNING user_id`, [hash(body.token)]);
    if (!consumed.rowCount) { await client.query('ROLLBACK'); return invalid(res); }
    await replacePassword(client, row.id, body.newPassword);
    await client.query('COMMIT');
    return res.json({ message: 'Senha alterada. Entre novamente no aplicativo.' });
  } catch (error) { await client.query('ROLLBACK'); return next(error); }
  finally { client.release(); }
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
    const result = await client.query(`SELECT password_hash, token_version, account_status FROM users WHERE id = $1 FOR UPDATE`, [req.user.id]);
    const row = result.rows[0];
    if (!row || row.account_status !== 'active' || row.token_version !== req.user.tokenVersion) {
      await client.query('ROLLBACK'); return res.status(401).json({ error: 'Sessão expirada. Entre novamente.' });
    }
    if (!row.password_hash || !await bcrypt.compare(body.currentPassword, row.password_hash)) {
      await client.query('ROLLBACK'); return res.status(400).json({ error: 'Senha atual incorreta.' });
    }
    await replacePassword(client, req.user.id, body.newPassword);
    await client.query('COMMIT');
    return res.json({ message: 'Senha alterada. Entre novamente em seus dispositivos.' });
  } catch (error) { await client.query('ROLLBACK'); return next(error); }
  finally { client.release(); }
}
async function replacePassword(client, id, password) {
  await client.query(`UPDATE users SET password_hash = $2, managed_password = NULL,
    must_change_password = FALSE, token_version = token_version + 1, updated_at = NOW() WHERE id = $1`,
  [id, await bcrypt.hash(password, 12)]);
  await client.query('UPDATE password_reset_tokens SET used_at = NOW() WHERE user_id = $1 AND used_at IS NULL', [id]);
}
module.exports = { forgot, reset, change };
