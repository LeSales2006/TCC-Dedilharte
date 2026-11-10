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
const digest = token => crypto.createHash('sha256').update(token).digest('hex');

test('PostgreSQL and HTTP: profile, recovery and password lifecycle', { skip: !databaseUrl }, async t => {
  assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(new URL(databaseUrl).hostname));
  const setup = new Client({ connectionString: databaseUrl, ssl: false }); await setup.connect();
  const schema = `password_test_${process.pid}`;
  await setup.query(`CREATE SCHEMA ${schema}`);
  const isolated = new Pool({ connectionString: databaseUrl, ssl: false, options: `-c search_path=${schema}` });
  const original = { query: pool.query, connect: pool.connect, send: email.send };
  let server;
  try {
    for (const file of ['schema.sql', 'migrations/2026-09-28_phase1_functional.sql', 'migrations/2026-11-04_auth_songs_compat.sql', 'migrations/2026-11-08_profile_passwords.sql']) await isolated.query(sql(file));
    pool.query = (...args) => isolated.query(...args); pool.connect = () => isolated.connect();
    process.env.JWT_SECRET = 'isolated-password-tests-only';
    process.env.RESEND_API_KEY = 'fake-key-never-sent'; process.env.MAIL_FROM = 'test@example.com';
    process.env.PASSWORD_RESET_BASE_URL = 'https://example.com/api/auth/reset-password';
    const deliveries = []; email.send = async (address, token) => deliveries.push({ address, token });
    const app = express(); app.use(express.json()); app.use('/api/auth', require('../src/routes/auth')); app.use('/api', require('../src/routes/users'));
    app.use(require('../src/middleware/errorHandler'));
    server = await new Promise(resolve => { const running = app.listen(0, '127.0.0.1', () => resolve(running)); });
    const base = `http://127.0.0.1:${server.address().port}`;
    async function request(route, body, token, method = body ? 'POST' : 'GET') {
      const response = await fetch(base + '/api' + route, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
      return { status: response.status, body: await response.json(), headers: response.headers };
    }
    async function forgot(address) {
      const res = { status(code) { this.code = code; return this; }, json(body) { this.body = body; return this; } };
      await passwords.forgot({ body: { email: address } }, res, e => { throw e; }); return res;
    }
    let user, session, token;
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
    await t.test('generic response, email delivery, hash-only storage and 30 minute expiry', async () => {
      const known = await forgot(user.email), unknown = await forgot('absent@example.com');
      assert.equal(known.code, 202); assert.equal(known.code, unknown.code); assert.deepEqual(known.body, unknown.body); assert.equal(deliveries.length, 2);
      assert.equal(deliveries[1].token, null);
      token = deliveries[0].token; assert.match(token, /^[a-f0-9]{64}$/);
      const row = (await isolated.query('SELECT * FROM password_reset_tokens')).rows[0];
      assert.equal(row.token_hash, digest(token)); assert.notEqual(row.token_hash, token);
      assert.equal(row.expires_at - row.created_at, 30 * 60 * 1000);
      const page = await fetch(base + '/api/auth/reset-password'); const html = await page.text();
      assert.equal(page.status, 200); assert.match(page.headers.get('content-security-policy'), /frame-ancestors 'none'/);
      assert.match(html, /location.hash/); assert.match(html, /history.replaceState/); assert.equal(page.headers.get('referrer-policy'), 'no-referrer');
      const response = await request('/auth/forgot-password', { email: 'absent@example.com' }); assert.equal(response.status, 202); assert.deepEqual(response.body, known.body);
      assert.equal((await request('/auth/forgot-password', { email: 'invalid' }, 'invalid-jwt')).status, 400);
      assert.equal((await request('/auth/missing-route', {})).status, 404);
    });
    await t.test('expired/reused tokens rejected, reset revokes sessions and simultaneous reuse is atomic', async () => {
      assert.equal((await request('/auth/reset-password', { token: 'f'.repeat(64), newPassword: 'NewPassword123', confirmPassword: 'NewPassword123' })).status, 400);
      assert.equal((await request('/auth/reset-password', { token, newPassword: 'NewPassword123', confirmPassword: 'Different123' })).status, 400);
      await isolated.query("UPDATE password_reset_tokens SET expires_at = NOW() - INTERVAL '1 second'");
      assert.equal((await request('/auth/reset-password', { token, newPassword: 'NewPassword123', confirmPassword: 'NewPassword123' })).status, 400);
      await forgot(user.email); token = deliveries.at(-1).token;
      const responses = await Promise.all([1, 2].map(() => request('/auth/reset-password', { token, newPassword: 'NewPassword123', confirmPassword: 'NewPassword123' })));
      assert.deepEqual(responses.map(r => r.status).sort(), [200, 400]);
      assert.equal((await request('/auth/me', null, session)).status, 401);
      assert.equal((await request('/auth/login', { email: user.email, password: 'Password123' })).status, 401);
      const login = await request('/auth/login', { email: user.email, password: 'NewPassword123' }); assert.equal(login.status, 200); session = login.body.token;
      assert.equal((await request('/auth/reset-password', { token, newPassword: 'Another123', confirmPassword: 'Another123' })).status, 400);
    });
    await t.test('change requires current password, revokes every session and outstanding links', async () => {
      await forgot(user.email); const outstanding = deliveries.at(-1).token;
      const secondSession = (await request('/auth/login', { email: user.email, password: 'NewPassword123' })).body.token;
      const body = { currentPassword: 'Wrong123', newPassword: 'Changed123', confirmPassword: 'Changed123' };
      assert.equal((await request('/auth/change-password', body)).status, 401);
      assert.equal((await request('/auth/change-password', body, session)).status, 400);
      body.currentPassword = 'NewPassword123'; assert.equal((await request('/auth/change-password', body, session)).status, 200);
      assert.equal((await request('/auth/me', null, session)).status, 401); assert.equal((await request('/auth/me', null, secondSession)).status, 401);
      assert.equal((await request('/auth/reset-password', { token: outstanding, newPassword: 'Third1234', confirmPassword: 'Third1234' })).status, 400);
      assert.equal((await request('/auth/login', { email: user.email, password: 'Changed123' })).status, 200);
      const row = (await isolated.query('SELECT password_hash, token_version FROM users WHERE id = $1', [user.id])).rows[0];
      assert.match(row.password_hash, /^\$2[aby]\$12\$/); assert.equal(row.token_version, 2);
    });
    await t.test('provider failure invalidates link, missing configuration and persistent rate limits', async () => {
      const count = Number((await isolated.query('SELECT COUNT(*) FROM password_reset_tokens')).rows[0].count);
      email.send = async () => { throw Error('simulated provider failure'); };
      assert.equal((await forgot(user.email)).code, 503);
      assert.equal((await forgot('absent@example.com')).code, 503);
      assert.equal(Number((await isolated.query('SELECT COUNT(*) FROM password_reset_tokens')).rows[0].count), count);
      delete process.env.RESEND_API_KEY; assert.equal((await forgot(user.email)).code, 503); assert.equal((await forgot('absent@example.com')).code, 503);
      process.env.RESEND_API_KEY = 'fake';
      email.send = async (address, token) => deliveries.push({ address, token });
      for (let i = 0; i < 2; i++) assert.equal((await request('/auth/forgot-password', { email: 'absent@example.com' })).status, 202);
      const limited = await request('/auth/forgot-password', { email: 'absent@example.com' }); assert.equal(limited.status, 429); assert.ok(Number(limited.headers.get('retry-after')) > 0);
      const rows = (await isolated.query('SELECT bucket_hash FROM auth_rate_limits')).rows; assert.ok(rows.length); rows.forEach(r => assert.match(r.bucket_hash, /^[a-f0-9]{64}$/));
    });
    await t.test('response waits for provider acceptance', async () => {
      const send = email.send;
      let release, started;
      const entered = new Promise(resolve => { started = resolve; });
      email.send = () => { started(); return new Promise(resolve => { release = resolve; }); };
      const res = { status(code) { this.code = code; return this; }, json(body) { this.body = body; return this; } };
      const pending = passwords.forgot({ body: { email: user.email } }, res);
      try {
        await entered;
        assert.equal(res.code, undefined);
        release(); await pending;
        assert.equal(res.code, 202);
      } finally { release?.(); email.send = send; }
    });
    await t.test('database failure is not acknowledged as successful recovery', async () => {
      const query = pool.query;
      pool.query = async () => { throw new Error('database unavailable fixture'); };
      try {
        for (const address of [user.email, 'absent@example.com']) {
          const failed = await forgot(address);
          assert.equal(failed.code, 503); assert.ok(failed.body.error); assert.equal(failed.body.message, undefined);
        }
      } finally { pool.query = query; }
    });
  } finally {
    email.send = original.send; pool.query = original.query; pool.connect = original.connect;
    if (server) await new Promise(resolve => server.close(resolve));
    await isolated.end(); await setup.query(`DROP SCHEMA ${schema} CASCADE`); await setup.end();
  }
});

test('Resend adapter uses environment, fragment link and sanitized failure', async () => {
  process.env.RESEND_API_KEY = 'fake-test-only'; process.env.MAIL_FROM = 'test@example.com'; process.env.PASSWORD_RESET_BASE_URL = 'https://example.com/api/auth/reset-password';
  const originalFetch = global.fetch;
  try {
    let captured;
    global.fetch = async (url, options) => { captured = { url, options }; return { ok: true, json: async () => ({ id: 'delivery-fixture' }) }; };
    await email.send('recipient@example.com', 'a'.repeat(64));
    assert.equal(captured.url, 'https://api.resend.com/emails'); assert.equal(captured.options.headers.Authorization, 'Bearer fake-test-only');
    const message = JSON.parse(captured.options.body); assert.match(message.text, /reset-password#token=a{64}/); assert.deepEqual(message.to, ['recipient@example.com']);
    await email.send('absent@example.com', null);
    assert.doesNotMatch(JSON.parse(captured.options.body).text, /token=|reset-password/);
    global.fetch = async () => ({ ok: true, json: async () => ({}) }); await assert.rejects(email.send('recipient@example.com', 'b'.repeat(64)), /RESET_EMAIL_SEND_FAILED/);
    global.fetch = async () => ({ ok: false }); await assert.rejects(email.send('recipient@example.com', 'b'.repeat(64)), /RESET_EMAIL_SEND_FAILED/);
    process.env.PASSWORD_RESET_BASE_URL = 'http://insecure.example.com/reset'; assert.throws(email.configuration);
  } finally { global.fetch = originalFetch; }
});
