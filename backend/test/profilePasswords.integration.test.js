const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool, Client } = require('pg');
const express = require('express');
const pool = require('../src/db');
const email = require('../src/services/resetEmail');
const passwords = require('../src/controllers/passwordController');
const databaseUrl = process.env.TEST_DATABASE_URL;
const sql = file => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
const migration = 'migrations/2026-11-10_password_recovery_codes.sql';
const digest = token => crypto.createHash('sha256').update(token).digest('hex');

test('PostgreSQL + HTTP: additive migration, profile, recovery codes and password lifecycle', { skip: !databaseUrl }, async t => {
  assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(new URL(databaseUrl).hostname));
  const setup = new Client({ connectionString: databaseUrl, ssl: false }); await setup.connect();
  const schema = `password_test_${process.pid}`;
  await setup.query(`CREATE SCHEMA ${schema}`);
  const isolated = new Pool({ connectionString: databaseUrl, ssl: false, options: `-c search_path=${schema}` });
  const original = { query: pool.query, connect: pool.connect, send: email.send };
  let server;
  try {
    for (const file of ['schema.sql', 'migrations/2026-09-28_phase1_functional.sql', 'migrations/2026-11-04_auth_songs_compat.sql']) await isolated.query(sql(file));
    await isolated.query("INSERT INTO users (id, name, email, password_hash) VALUES ('preserved', 'Existing', 'existing@example.com', 'unchanged-hash')");
    await isolated.query("INSERT INTO songs (id, title) VALUES ('preserved-song', 'Existing song')");
    await isolated.query("INSERT INTO lesson_progress (user_id, lesson_id, completed, score) VALUES ('preserved', 'existing-lesson', TRUE, 10)");
    await t.test('Aiven-shaped schema gains only additive fields; reruns preserve users/passwords/content/progress', async () => {
      assert.equal((await isolated.query("SELECT to_regclass('auth_rate_limits') AS name")).rows[0].name, null);
      for (let i = 0; i < 2; i++) await isolated.query(sql(migration));
      const row = (await isolated.query("SELECT * FROM users WHERE id = 'preserved'")).rows[0];
      assert.equal(row.password_hash, 'unchanged-hash'); assert.equal(row.initial_level, 'beginner'); assert.equal(row.song_goal, 10); assert.equal(row.notifications_enabled, false);
      assert.equal((await isolated.query("SELECT title FROM songs WHERE id = 'preserved-song'")).rows[0].title, 'Existing song');
      assert.equal((await isolated.query("SELECT score FROM lesson_progress WHERE user_id = 'preserved'")).rows[0].score, 10);
    });
    pool.query = (...args) => isolated.query(...args); pool.connect = () => isolated.connect();
    process.env.JWT_SECRET = 'isolated-password-tests-only';
    process.env.PASSWORD_RECOVERY_SECRET = 'isolated-recovery-hmac-secret-at-least-32-bytes';
    process.env.RESEND_API_KEY = 'fake-key-never-sent'; process.env.MAIL_FROM = 'test@example.com';
    const deliveries = [];
    const deliver = async (address, code, requestId) => deliveries.push({ address, code, requestId });
    email.send = deliver;
    const app = express(); app.use(express.json()); app.use('/api/auth', require('../src/routes/auth')); app.use('/api', require('../src/routes/users'));
    app.use(require('../src/middleware/errorHandler'));
    server = await new Promise(resolve => { const running = app.listen(0, '127.0.0.1', () => resolve(running)); });
    const base = `http://127.0.0.1:${server.address().port}`;
    async function request(route, body, token, method = body ? 'POST' : 'GET') {
      const response = await fetch(base + '/api' + route, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
      return { status: response.status, body: await response.json(), headers: response.headers };
    }
    async function issue(address) {
      const res = { status(code) { this.status = code; return this; }, json(body) { this.body = body; return this; } };
      await passwords.requestCode({ body: { email: address } }, res); return res;
    }
    const verify = (requestId, code) => request('/auth/password-recovery/verify', { requestId, code });
    const reset = (requestId, authorization, newPassword = 'NewPassword123', confirmPassword = newPassword) => request('/auth/password-recovery/reset', { requestId, authorization, newPassword, confirmPassword });
    const clearLimits = () => isolated.query('DELETE FROM auth_rate_limits');
    const rowFor = async id => (await isolated.query('SELECT * FROM password_recovery_requests WHERE id = $1', [id])).rows[0];
    const newCode = async (address = user.email) => { const result = await issue(address); assert.equal(result.status, 202); return deliveries.at(-1); };
    let user, session;
    await t.test('registration, strict preferences, authenticated profile persistence and isolation', async () => {
      const created = await request('/auth/register', { name: 'Aluno Teste', email: 'student@example.com', password: 'Password123', initialLevel: 'intermediate', weeklyGoal: 7, songGoal: 25, notificationsEnabled: true, role: 'admin' });
      assert.equal(created.status, 201); user = created.body.user; session = created.body.token;
      assert.equal(user.role, 'student'); assert.equal(user.songGoal, 25); assert.equal(user.initialLevel, 'intermediate'); assert.equal(user.notificationsEnabled, true);
      assert.equal((await request('/profile')).status, 401);
      for (const value of [0, -1, 1.5, '12']) assert.equal((await request('/profile', { name: 'Aluno Teste', songGoal: value }, session, 'PUT')).status, 400);
      for (const invalid of [{ weeklyGoal: 8 }, { initialLevel: 'advanced' }, { notificationsEnabled: 'false' }]) assert.equal((await request('/profile', { name: 'Aluno Teste', ...invalid }, session, 'PUT')).status, 400);
      await isolated.query("INSERT INTO users (id, name) VALUES ('other', 'Other')");
      const updated = await request('/profile', { id: 'other', name: 'Administrador', weeklyGoal: 2, songGoal: 4, initialLevel: 'beginner', notificationsEnabled: false, role: 'admin' }, session, 'PUT');
      assert.equal(updated.status, 200); assert.equal(updated.body.id, user.id); assert.equal(updated.body.role, 'student');
      assert.equal((await isolated.query("SELECT name FROM users WHERE id = 'other'")).rows[0].name, 'Other');
      const profile = await request('/profile', null, session); assert.equal(profile.body.songGoal, 4); assert.equal(profile.body.weeklyGoal, 2);
      assert.equal((await request('/auth/login', { email: user.email, password: 'Password123' })).body.user.songGoal, 4);
      await isolated.query(sql('migrations/2026-11-08_profile_passwords.sql'));
      assert.equal((await request('/profile', null, session)).body.name, 'Administrador');
      assert.equal((await request('/auth/login', { email: user.email, password: 'Password123' })).body.user.role, 'student');
      assert.equal((await request('/activity/summary', null, session)).body.weeklyGoal, 2);
    });

    await t.test('same responses and email codes for known, absent and blocked addresses; hash-only storage and 10 minutes', async () => {
      await clearLimits();
      await isolated.query("INSERT INTO users (id, name, email, account_status) VALUES ('blocked', 'Blocked', 'blocked@example.com', 'blocked')");
      const responses = [];
      for (const address of [user.email, 'absent@example.com', 'blocked@example.com']) {
        const result = await request('/auth/password-recovery/request', { email: address }, 'invalid-jwt-ignored');
        assert.equal(result.status, 202); assert.equal(result.headers.get('cache-control'), 'no-store');
        const { requestId, ...publicBody } = result.body; responses.push(publicBody);
        const delivered = deliveries.at(-1); assert.equal(delivered.requestId, requestId); assert.match(delivered.code, /^\d{6}$/);
        const row = await rowFor(requestId);
        assert.equal(row.expires_at - row.created_at, 600000);
        assert.equal(row.code_hash, crypto.createHmac('sha256', process.env.PASSWORD_RECOVERY_SECRET).update(requestId + ':' + delivered.code).digest('hex'));
        assert.notEqual(row.code_hash, delivered.code); assert.notEqual(row.code_hash, digest(delivered.code));
        assert.equal(row.email_hash.includes(address), false);
        const checked = await verify(requestId, delivered.code); assert.equal(checked.status, 200);
        assert.match(checked.body.authorization, /^[a-f0-9]{64}$/);
        if (address !== user.email) {
          assert.equal(row.user_id, null);
          assert.equal((await reset(requestId, checked.body.authorization)).status, 200);
        }
      }
      assert.deepEqual(responses[0], responses[1]); assert.deepEqual(responses[0], responses[2]);
      assert.equal((await request('/auth/missing-route', {})).status, 404);
      assert.equal((await request('/auth/password-recovery/request')).status, 404);
    });
    await t.test('wrong/malformed/expired codes, five persistent attempts and parallel guessing are rejected', async () => {
      await clearLimits(); const first = await newCode();
      const wrong = first.code === '000000' ? '111111' : '000000';
      for (const value of [123456, '12345', '1234567', null]) assert.equal((await verify(first.requestId, value)).status, 400);
      const attempts = await Promise.all(Array.from({ length: 7 }, () => verify(first.requestId, wrong)));
      assert.ok(attempts.every(r => r.status === 400)); assert.equal((await rowFor(first.requestId)).attempts, 5);
      assert.equal((await verify(first.requestId, first.code)).status, 400);
      const expired = await newCode();
      await isolated.query("UPDATE password_recovery_requests SET expires_at = NOW() - INTERVAL '1 second' WHERE id = $1", [expired.requestId]);
      assert.equal((await verify(expired.requestId, expired.code)).status, 400);
    });
    await t.test('resend revokes the previous code and verified authorization, including concurrent sends', async () => {
      await clearLimits(); const old = await newCode(); const fresh = await newCode();
      assert.equal((await verify(old.requestId, old.code)).status, 400);
      const grant = (await verify(fresh.requestId, fresh.code)).body.authorization;
      await Promise.all([issue(user.email), issue(user.email)]);
      assert.equal((await reset(fresh.requestId, grant)).status, 400);
      const active = await isolated.query('SELECT id FROM password_recovery_requests WHERE user_id = $1 AND used_at IS NULL', [user.id]);
      assert.equal(active.rowCount, 1); assert.equal(active.rows[0].id, deliveries.at(-1).requestId);
    });
    await t.test('authorization is request-bound, expiring, single-use and cannot be replaced with the code', async () => {
      await clearLimits(); const item = await newCode();
      assert.equal((await reset(item.requestId, item.code)).status, 400);
      const verified = await Promise.all([verify(item.requestId, item.code), verify(item.requestId, item.code)]);
      assert.deepEqual(verified.map(r => r.status).sort(), [200, 400]);
      const grant = verified.find(r => r.status === 200).body.authorization;
      const row = await rowFor(item.requestId); assert.equal(row.code_hash, null); assert.equal(row.authorization_hash, digest(grant));
      assert.equal(row.authorization_expires_at - row.verified_at, 300000);
      assert.equal((await reset(crypto.randomUUID(), grant)).status, 400);
      assert.equal((await reset(item.requestId, 'f'.repeat(64))).status, 400);
      for (const bad of ['short1', 'abcdefgh', '12345678', 'é'.repeat(37) + '1a']) assert.equal((await reset(item.requestId, grant, bad)).status, 400);
      assert.equal((await reset(item.requestId, grant, 'Valid1234', 'Different123')).status, 400);
      await isolated.query("UPDATE password_recovery_requests SET authorization_expires_at = NOW() - INTERVAL '1 second' WHERE id = $1", [item.requestId]);
      assert.equal((await reset(item.requestId, grant)).status, 400);
    });
    await t.test('successful concurrent reset changes bcrypt password once and revokes JWTs; new login works', async () => {
      await clearLimits(); const item = await newCode(); const grant = (await verify(item.requestId, item.code)).body.authorization;
      const responses = await Promise.all([reset(item.requestId, grant), reset(item.requestId, grant)]);
      assert.deepEqual(responses.map(r => r.status).sort(), [200, 400]);
      assert.equal((await request('/auth/me', null, session)).status, 401);
      assert.equal((await request('/auth/login', { email: user.email, password: 'Password123' })).status, 401);
      const login = await request('/auth/login', { email: user.email, password: 'NewPassword123' }); assert.equal(login.status, 200); session = login.body.token;
      assert.equal((await request('/auth/me', null, session)).status, 200);
      assert.equal((await reset(item.requestId, grant)).status, 400);
      assert.equal((await rowFor(item.requestId)).authorization_hash, null);
      const row = (await isolated.query('SELECT password_hash, token_version FROM users WHERE id = $1', [user.id])).rows[0];
      assert.match(row.password_hash, /^\$2[aby]\$12\$/); assert.equal(row.token_version, 1);
    });
    await t.test('authenticated change verifies current password and invalidates all recovery grants and sessions', async () => {
      await clearLimits(); const item = await newCode(); const grant = (await verify(item.requestId, item.code)).body.authorization;
      const second = (await request('/auth/login', { email: user.email, password: 'NewPassword123' })).body.token;
      const body = { currentPassword: 'Wrong123', newPassword: 'Changed123', confirmPassword: 'Changed123' };
      assert.equal((await request('/auth/change-password', body)).status, 401);
      assert.equal((await request('/auth/change-password', body, session)).status, 400);
      body.currentPassword = 'NewPassword123'; assert.equal((await request('/auth/change-password', body, session)).status, 200);
      assert.equal((await request('/auth/me', null, second)).status, 401);
      assert.equal((await reset(item.requestId, grant)).status, 400);
      assert.equal((await request('/auth/login', { email: user.email, password: 'Changed123' })).status, 200);
    });
    await t.test('rate limits survive requests, normalize emails, enforce cooldown/hour/IP and expose Retry-After', async () => {
      await clearLimits(); const address = 'ratelimit@example.com';
      const send = () => request('/auth/password-recovery/request', { email: address });
      assert.equal((await send()).status, 202);
      const cooldown = await request('/auth/password-recovery/request', { email: ' RATELIMIT@EXAMPLE.COM ' });
      assert.equal(cooldown.status, 429); assert.ok(Number(cooldown.headers.get('retry-after')) > 0);
      const bucket = crypto.createHmac('sha256', process.env.JWT_SECRET).update('recovery-cooldown:' + address).digest('hex');
      for (let i = 0; i < 3; i++) {
        await isolated.query("UPDATE auth_rate_limits SET expires_at = NOW() - INTERVAL '1 second' WHERE bucket_hash = $1", [bucket]);
        assert.equal((await send()).status, i < 2 ? 202 : 429);
      }
      assert.equal((await request('/auth/password-recovery/request', { email: 'another@example.com' })).status, 429);
      await clearLimits();
      const responses = await Promise.all(Array.from({ length: 31 }, () => verify(crypto.randomUUID(), '123456')));
      assert.equal(responses.filter(r => r.status === 429).length, 1);
    });
    await t.test('provider/database/configuration failures never acknowledge success or invalidate a previous valid code', async () => {
      await clearLimits(); const active = await newCode();
      const count = (await isolated.query('SELECT COUNT(*) FROM password_recovery_requests')).rows[0].count;
      email.send = async () => { throw new Error('simulated provider failure'); };
      for (const address of [user.email, 'absent@example.com']) assert.equal((await issue(address)).status, 503);
      assert.equal((await isolated.query('SELECT COUNT(*) FROM password_recovery_requests')).rows[0].count, count);
      assert.equal((await verify(active.requestId, active.code)).status, 200);
      email.send = deliver;
      const connect = pool.connect;
      pool.connect = async () => { throw new Error('test database failure'); };
      try { assert.equal((await issue(user.email)).status, 503); } finally { pool.connect = connect; }
      for (const key of ['RESEND_API_KEY', 'PASSWORD_RECOVERY_SECRET']) {
        const value = process.env[key]; delete process.env[key];
        for (const address of [user.email, 'absent@example.com']) assert.equal((await issue(address)).status, 503);
        process.env[key] = value;
      }
    });
    await t.test('success waits for provider acceptance and committed storage', async () => {
      let release, entered;
      const started = new Promise(resolve => { entered = resolve; });
      email.send = () => { entered(); return new Promise(resolve => { release = resolve; }); };
      let completed = false; const pending = issue(user.email).then(result => { completed = true; return result; });
      try { await started; assert.equal(completed, false); release(); assert.equal((await pending).status, 202); }
      finally { release?.(); email.send = deliver; }
    });
  } finally {
    email.send = original.send; pool.query = original.query; pool.connect = original.connect;
    if (server) await new Promise(resolve => server.close(resolve));
    await isolated.end(); await setup.query(`DROP SCHEMA ${schema} CASCADE`); await setup.end();
  }
});

test('Resend sends only the six-digit code, uses idempotency and requires provider acceptance', async () => {
  process.env.RESEND_API_KEY = 'fake-test-only'; process.env.MAIL_FROM = 'test@example.com';
  const originalFetch = global.fetch;
  try {
    let captured;
    global.fetch = async (url, options) => { captured = { url, options }; return { ok: true, json: async () => ({ id: 'delivery-fixture' }) }; };
    await email.send('recipient@example.com', '000012', 'request-fixture');
    assert.equal(captured.url, 'https://api.resend.com/emails'); assert.equal(captured.options.headers.Authorization, 'Bearer fake-test-only');
    assert.equal(captured.options.headers['Idempotency-Key'], 'recovery/request-fixture');
    const message = JSON.parse(captured.options.body); assert.match(message.text, /000012/); assert.match(message.text, /10 minutos/);
    assert.doesNotMatch(message.text, /https?:|token=|<html/i); assert.deepEqual(message.to, ['recipient@example.com']);
    global.fetch = async () => ({ ok: true, json: async () => ({}) }); await assert.rejects(email.send('recipient@example.com', '123456', 'id'), /RESET_EMAIL_SEND_FAILED/);
    global.fetch = async () => ({ ok: false }); await assert.rejects(email.send('recipient@example.com', '123456', 'id'), /RESET_EMAIL_SEND_FAILED/);
    delete process.env.RESEND_API_KEY; assert.throws(email.configuration);
  } finally { global.fetch = originalFetch; }
});
