const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { Client } = require('pg');
const bcrypt = require('bcryptjs');
const pool = require('../src/db');
const auth = require('../src/controllers/authController');
const songs = require('../src/controllers/songsController');
const modules = require('../src/controllers/modulesController');
const progress = require('../src/controllers/moduleProgressController');
const { authenticateToken } = require('../src/middleware/authMiddleware');

const databaseUrl = process.env.TEST_DATABASE_URL;
const sql = file => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
const migration = sql('migrations/2026-11-04_auth_songs_compat.sql');
async function run(handler, req = {}) {
  const res = { statusCode: 200, status(n) { this.statusCode = n; return this; },
    json(body) { this.body = body; return this; }, send() { return this; } };
  let failure;
  await handler(req, res, error => { failure = error; });
  if (failure) throw failure;
  return res;
}

for (const legacy of [false, true]) {
  test(`PostgreSQL: ${legacy ? 'legacy upgrade' : 'fresh schema'}, login, six exercises, publication and referential integrity`,
    { skip: !databaseUrl }, async () => {
      const target = new URL(databaseUrl);
      assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(target.hostname), 'Integration tests require a local database');
      const client = new Client({ connectionString: databaseUrl, ssl: false });
      await client.connect();
      const schema = `repair_test_${legacy ? 'legacy' : 'fresh'}_${process.pid}`;
      const originalQuery = pool.query;
      const originalConnect = pool.connect;
      try {
        await client.query(`CREATE SCHEMA ${schema}`);
        await client.query(`SET search_path TO ${schema}`);
        if (legacy) {
          await client.query(sql('migrations/2026-10-05_songs_content.sql'));
          await client.query(`INSERT INTO songs (id, title, recommended_bpm, queue_order, status, in_queue, tablature)
            VALUES ('old-published', 'Existing published', 92, 4, 'publicada', true, 'existing-tab'),
            ('old-draft', 'Existing draft', 70, 5, 'rascunho', true, 'draft-tab'),
            ('old-off-queue', 'Existing off queue', 80, 6, 'publicada', false, 'off-tab')`);
        }
        await client.query(sql('schema.sql'));
        await client.query(sql('migrations/2026-09-28_phase1_functional.sql'));
        await client.query(migration);
        if (legacy) {
          const rows = (await client.query('SELECT id, bpm, sort_order, active, tablature FROM songs ORDER BY id')).rows;
          assert.deepEqual(rows.map(r => r.active), [false, false, true]);
          assert.equal(rows[2].bpm, 92);
          assert.equal(rows[2].sort_order, 4);
          assert.equal(rows[2].tablature, 'existing-tab');
          await client.query("UPDATE songs SET active = false, bpm = 96 WHERE id = 'old-published'");
          await client.query(migration);
          const old = (await client.query("SELECT active, bpm FROM songs WHERE id = 'old-published'")).rows[0];
          assert.deepEqual(old, { active: false, bpm: 96 });
        }
        pool.query = (...args) => client.query(...args);
        pool.connect = async () => ({ query: (...args) => client.query(...args), release() {} });
        process.env.JWT_SECRET = 'local-postgres-test-only';
        await client.query(`INSERT INTO users (id, name, email, password_hash, role)
          VALUES ('student', 'Local Test', 'test@example.com', $1, 'student')`, [await bcrypt.hash('Local1234', 4)]);
        const logged = await run(auth.login, { body: { email: ' TEST@EXAMPLE.COM ', password: 'Local1234' } });
        assert.equal(logged.statusCode, 200);
        assert.ok(logged.body.token);
        const req = { headers: { authorization: `Bearer ${logged.body.token}` } };
        await run(authenticateToken, req);
        assert.equal(req.user.id, 'student');
        assert.equal((await run(auth.me, req)).body.user.email, 'test@example.com');

        const tab = 'e|--0--2--|\nB|--------|\nG|--------|\nD|--------|\nA|--------|\nE|--------|';
        for (let i = 0; i < 6; i++) {
          const created = await run(songs.createAdmin, { user: { id: 'student' }, body: {
            title: `Exercise fixture ${i + 1}`, bpm: 60 + i * 5, tablature: tab,
            active: true, sortOrder: i, content: { preserved: i },
          } });
          const moduleId = i < 3 ? 'module_trivia_beginner' : 'module_trivia_intermediate';
          await run(modules.createItem, { params: { id: moduleId }, user: { id: 'student' },
            body: { id: `exercise-${i}`, itemType: 'song', songId: created.body.id, title: `Exercise ${i}`, active: true } });
        }
        const list = await run(modules.listPublic);
        assert.deepEqual(list.body.items.map(m => m.items.length), [3, 3]);
        for (const module of list.body.items) for (const item of module.items) {
          assert.equal(item.songId, item.song.id);
          assert.equal(item.song.active, true);
          assert.equal(item.song.tablature.split('\n').length, 6);
        }
        assert.equal((await run(songs.listPublic)).body.items.length, 6);
        const first = list.body.items[0].items[0];
        assert.equal((await run(songs.deleteAdmin, { params: { id: first.songId } })).statusCode, 409);
        const trained = await run(progress.upsertMyProgress, { params: { id: first.id }, user: { id: 'student' }, body: { completed: true } });
        assert.equal(trained.body.completed, true);
        const archived = await run(songs.updateAdmin, { params: { id: first.songId }, body: { active: false } });
        assert.deepEqual(archived.body.content, { preserved: 0 });
        assert.equal((await run(modules.listPublic)).body.items[0].items.length, 2);
        assert.equal((await run(progress.upsertMyProgress, { params: { id: first.id }, user: { id: 'student' }, body: { completed: true } })).statusCode, 404);
        assert.equal((await run(modules.listStaff)).body.items[0].items.length, 3);
        await assert.rejects(client.query(`INSERT INTO learning_module_items
          (id, module_id, item_type, song_id) VALUES ('orphan', 'module_trivia_beginner', 'song', 'missing')`), { code: '23503' });
        await client.query(migration); // Repeated migration preserves all fixtures and progress.
        assert.equal((await client.query('SELECT count(*)::int AS n FROM learning_module_items')).rows[0].n, 6);
        assert.equal((await client.query('SELECT count(*)::int AS n FROM learning_module_progress')).rows[0].n, 1);
        await client.query(sql('diagnostics/validate_content.sql'));
      } finally {
        pool.query = originalQuery;
        pool.connect = originalConnect;
        await client.query('ROLLBACK');
        await client.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`);
        await client.end();
      }
    });
}

test('PostgreSQL: compatibility migration alone upgrades legacy auth and preserves songs',
  { skip: !databaseUrl }, async () => {
    const target = new URL(databaseUrl);
    assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(target.hostname), 'Integration tests require a local database');
    const client = new Client({ connectionString: databaseUrl, ssl: false });
    const schema = `repair_test_standalone_${process.pid}`;
    const originalQuery = pool.query;
    const oldSecret = process.env.JWT_SECRET;
    await client.connect();
    try {
      await client.query(`CREATE SCHEMA ${schema}`);
      await client.query(`SET search_path TO ${schema}`);
      // Do not apply schema.sql or phase1: they would conceal missing migration columns/indexes.
      await client.query(`CREATE TABLE users (id TEXT PRIMARY KEY, name TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW())`);
      await client.query(sql('migrations/2026-10-05_songs_content.sql'));
      await client.query(`INSERT INTO users (id, name) VALUES ('existing', 'Existing user');
        INSERT INTO songs (id, title, recommended_bpm, queue_order, status, tablature)
        VALUES ('existing-song', 'Existing song', 92, 4, 'publicada', 'preserved-tab')`);
      await client.query(sql('diagnostics/preflight_compat.sql'));
      await client.query(migration);
      pool.query = (...args) => client.query(...args);
      process.env.JWT_SECRET = 'standalone-local-migration-test';
      const registered = await run(auth.register, { body: {
        name: 'Migration Test', email: 'new@example.com', password: 'Local1234',
      } });
      assert.equal(registered.statusCode, 201);
      const logged = await run(auth.login, { body: { email: 'NEW@example.com', password: 'Local1234' } });
      assert.equal(logged.statusCode, 200);
      assert.ok(logged.body.token);
      // Registration's existence check alone cannot prevent concurrent duplicate accounts.
      await assert.rejects(client.query(`INSERT INTO users (id, name, email)
        VALUES ('duplicate', 'Duplicate', 'NEW@example.com')`), { code: '23505' });
      assert.equal((await client.query("SELECT managed_password FROM users WHERE id = 'existing'")).rows[0].managed_password, null);
      const before = (await client.query('SELECT id, bpm, sort_order, active, tablature FROM songs')).rows;
      assert.deepEqual(before, [{ id: 'existing-song', bpm: 92, sort_order: 4, active: true, tablature: 'preserved-tab' }]);
      await client.query(migration);
      assert.deepEqual((await client.query('SELECT id, bpm, sort_order, active, tablature FROM songs')).rows, before);
      assert.equal((await client.query("SELECT name FROM users WHERE id = 'existing'")).rows[0].name, 'Existing user');
    } finally {
      pool.query = originalQuery;
      if (oldSecret === undefined) delete process.env.JWT_SECRET;
      else process.env.JWT_SECRET = oldSecret;
      await client.query('ROLLBACK');
      await client.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`);
      await client.end();
    }
  });

test('PostgreSQL: duplicate legacy emails abort compatibility migration without changing accounts',
  { skip: !databaseUrl }, async () => {
    const target = new URL(databaseUrl);
    assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(target.hostname), 'Integration tests require a local database');
    const client = new Client({ connectionString: databaseUrl, ssl: false });
    const schema = `repair_test_duplicates_${process.pid}`;
    await client.connect();
    try {
      await client.query(`CREATE SCHEMA ${schema}`);
      await client.query(`SET search_path TO ${schema}`);
      await client.query(`CREATE TABLE users (id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT);
        INSERT INTO users VALUES ('one', 'First', 'same@example.com'), ('two', 'Second', 'SAME@example.com')`);
      await client.query(sql('migrations/2026-10-05_songs_content.sql'));
      const preflight = await client.query(sql('diagnostics/preflight_compat.sql'));
      assert.deepEqual(preflight[2].rows, [{ duplicate_email_groups: '1', affected_accounts: '2' }]);
      await assert.rejects(client.query(migration), { code: '23505' });
      await client.query('ROLLBACK');
      assert.deepEqual((await client.query('SELECT id, name, email FROM users ORDER BY id')).rows, [
        { id: 'one', name: 'First', email: 'same@example.com' },
        { id: 'two', name: 'Second', email: 'SAME@example.com' },
      ]);
      assert.equal((await client.query(`SELECT count(*)::int AS n FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'managed_password'`)).rows[0].n, 0);
    } finally {
      await client.query('ROLLBACK');
      await client.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`);
      await client.end();
    }
  });
