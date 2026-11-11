const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { Client } = require('pg');
const express = require('express');
const pool = require('../src/db');
const { VERSION } = require('../src/terms');
const sql = file => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
const databaseUrl = process.env.TEST_DATABASE_URL;

test('published Android document and API agree on the immutable version', () => {
  const android = path.join(__dirname, '../../app/src/main/res');
  assert.ok(fs.readFileSync(path.join(android, 'raw/student_terms_2026_11_11.txt'), 'utf8').includes(`Versão ${VERSION}`));
  assert.ok(fs.readFileSync(path.join(android, 'values/student_terms.xml'), 'utf8').includes(`>${VERSION}</string>`));
});

test('PostgreSQL + HTTP: public student acceptance, timestamp, migration reruns and internal exemptions',
  { skip: !databaseUrl }, async () => {
    assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(new URL(databaseUrl).hostname));
    const client = new Client({ connectionString: databaseUrl, ssl: false });
    const schema = `student_terms_test_${process.pid}`;
    const original = pool.query;
    let server;
    await client.connect();
    try {
      await client.query(`CREATE SCHEMA ${schema}`);
      await client.query(`SET search_path TO ${schema}`);
      for (const file of ['schema.sql', 'migrations/2026-11-08_profile_passwords.sql',
        'migrations/2026-11-10_password_recovery_codes.sql']) await client.query(sql(file));
      // Exercise the migration against a pre-terms database, including an existing account.
      await client.query('ALTER TABLE users DROP COLUMN terms_version CASCADE, DROP COLUMN terms_accepted_at');
      await client.query("INSERT INTO users (id, name, role) VALUES ('existing', 'Existing', 'student')");
      const migration = sql('migrations/2026-11-11_student_terms.sql');
      await client.query(migration);
      await client.query(migration);
      pool.query = (...args) => client.query(...args);
      process.env.JWT_SECRET = 'local-terms-tests-only';
      const app = express(); app.use(express.json());
      app.use('/api/auth', require('../src/routes/auth'));
      app.use('/api', require('../src/routes/users'));
      app.use(require('../src/middleware/errorHandler'));
      server = await new Promise(resolve => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
      const body = { name: 'Aluno Teste', email: 'student@example.com', password: 'Password123',
        role: 'student', termsAccepted: true, termsVersion: VERSION };
      async function request(route, value, token) {
        const res = await fetch(`http://127.0.0.1:${server.address().port}/api${route}`, {
          method: 'POST', headers: { 'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(value),
        });
        return { status: res.status, body: await res.json() };
      }
      for (const change of [{ termsAccepted: false }, { termsAccepted: 'true' },
        { termsVersion: 'old' }, { role: 'admin' }, { role: 'configurator' }, { termsAccepted: null }]) {
        await client.query('DELETE FROM auth_rate_limits');
        assert.equal((await request('/auth/register', { ...body, ...change })).status, 400);
      }
      assert.equal((await client.query('SELECT count(*)::int AS n FROM users')).rows[0].n, 1);
      await client.query('DELETE FROM auth_rate_limits');
      const before = (await client.query('SELECT NOW() AS time')).rows[0].time;
      const created = await request('/auth/register', { ...body, termsAcceptedAt: '2000-01-01' });
      assert.equal(created.status, 201); assert.equal(created.body.user.role, 'student');
      assert.ok(created.body.token); assert.equal(created.body.user.password_hash, undefined);
      const row = (await client.query('SELECT * FROM users WHERE id = $1', [created.body.user.id])).rows[0];
      assert.equal(row.terms_version, VERSION); assert.ok(row.terms_accepted_at >= before);
      assert.ok(await require('bcryptjs').compare(body.password, row.password_hash));
      assert.equal((await request('/auth/register', body)).status, 409);
      await client.query(migration);
      assert.equal((await client.query('SELECT terms_accepted_at FROM users WHERE id = $1', [row.id])).rows[0].terms_accepted_at.toISOString(), row.terms_accepted_at.toISOString());
      assert.deepEqual((await client.query("SELECT terms_version, terms_accepted_at FROM users WHERE id = 'existing'")).rows[0],
        { terms_version: null, terms_accepted_at: null });
      await assert.rejects(client.query("INSERT INTO users (id, name, terms_version) VALUES ('invalid', 'Invalid', 'x')"), { code: '23514' });
      await client.query("INSERT INTO users (id, name, role) VALUES ('staff', 'Staff', 'configurator')");
      const token = require('jsonwebtoken').sign({ sub: 'staff', tokenVersion: 0 }, process.env.JWT_SECRET);
      for (const role of ['admin', 'configurator']) {
        const internal = await request('/configurator/users', { name: 'Internal', email: `${role}@example.com`, password: 'Password123', role }, token);
        assert.equal(internal.status, 201); assert.equal(internal.body.role, role);
        assert.equal((await client.query('SELECT terms_version FROM users WHERE id = $1', [internal.body.id])).rows[0].terms_version, null);
      }
    } finally {
      if (server) await new Promise(resolve => server.close(resolve));
      pool.query = original;
      await client.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`);
      await client.end();
    }
  });
