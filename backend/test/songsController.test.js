const assert = require('node:assert/strict');
const test = require('node:test');
const express = require('express');
const jwt = require('jsonwebtoken');
const pool = require('../src/db');
const controller = require('../src/controllers/songsController');
const router = require('../src/routes/users');

for (const name of ['listPublic', 'listAdmin']) {
  test(name + ': empty database returns no songs and never seeds', async () => {
    let calls = 0;
    pool.query = async sql => {
      calls++;
      assert.match(sql, /^SELECT /);
      assert.doesNotMatch(sql, /recommended_bpm|queue_order|in_queue|status|created_at|updated_at/);
      if (name === 'listPublic') assert.match(sql, /WHERE active = TRUE/);
      else assert.doesNotMatch(sql, /WHERE/);
      return { rows: [], rowCount: 0 };
    };
    let body;
    await controller[name]({}, { json(value) { body = value; } }, error => { throw error; });
    assert.deepEqual(body, { items: [] });
    assert.equal(calls, 1);
  });
}

// Real HTTP routes/JWT, simulated database; no production records are created.
test('songs HTTP: admin CRUD, archive/restore, schema, content preservation and JWT authorization', async t => {
  process.env.JWT_SECRET = 'songs-test-secret';
  const rows = new Map();
  let songQueries = 0;
  let role = 'admin';
  const result = rows => ({ rows, rowCount: rows.length });
  pool.query = async (sql, params = []) => {
    if (sql.includes('FROM users')) return result([{ id: 'admin-test', role, account_status: 'active', token_version: 0 }]);
    songQueries++;
    assert.doesNotMatch(sql, /recommended_bpm|queue_order|in_queue|status|created_at|updated_at/);
    if (sql.startsWith('SELECT ')) {
      assert.match(sql, /ORDER BY sort_order ASC, id ASC/);
      return result([...rows.values()].filter(row => !sql.includes('WHERE') || row.active));
    }
    if (sql.startsWith('INSERT INTO songs')) {
      const columns = sql.slice(sql.indexOf('(') + 1, sql.indexOf(')')).split(', ');
      const row = Object.fromEntries(columns.map((column, index) => [column, params[index]]));
      row.content ??= { preserved: true };
      rows.set(row.id, row);
      return result([row]);
    }
    const row = rows.get(params[0]);
    if (!row) return result([]);
    if (sql.startsWith('UPDATE songs')) {
      const assignments = sql.split(' SET ')[1].split(' WHERE ')[0].split(', ');
      assignments.forEach((assignment, index) => { row[assignment.split(' = ')[0]] = params[index + 1]; });
      return result([row]);
    }
    if (sql.startsWith('DELETE FROM songs')) { rows.delete(row.id); return result([row]); }
    throw new Error('Unexpected SQL: ' + sql);
  };
  const app = express();
  app.use(express.json());
  app.use('/api', router);
  app.use((error, req, res, next) => res.status(500).json({ error: error.message }));
  const server = await new Promise(resolve => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = 'http://127.0.0.1:' + server.address().port + '/api';
  // The signed role is deliberately wrong: authorization must use the current database role.
  const token = jwt.sign({ sub: 'admin-test', role: 'admin' }, process.env.JWT_SECRET);
  const request = async (method, path, body, auth = token) => {
    const response = await fetch(base + path, { method, headers: {
      'Content-Type': 'application/json', ...(auth ? { Authorization: 'Bearer ' + auth } : {}),
    }, ...(body === undefined || method === 'GET' ? {} : { body: JSON.stringify(body) }) });
    return { status: response.status, body: response.status === 204 ? null : await response.json() };
  };
  for (const auth of [null, 'invalid', jwt.sign({ sub: 'admin-test', exp: 1 }, process.env.JWT_SECRET)]) {
    assert.equal((await request('GET', '/admin/songs', undefined, auth)).status, 401);
  }
  for (role of ['student', 'configurator']) {
    for (const [method, path] of [['GET', '/admin/songs'], ['POST', '/admin/songs'], ['PUT', '/admin/songs/x'], ['DELETE', '/admin/songs/x']]) {
      assert.equal((await request(method, path, { title: 'Rejected' })).status, 403);
    }
  }
  assert.equal(songQueries, 0);
  role = 'admin';
  assert.deepEqual((await request('GET', '/songs')).body, { items: [] });
  for (const body of [{}, { title: ' ' }, { title: 'Invalid', active: 'false' }, { title: 'Invalid', bpm: -1 }, { title: 'Invalid', sortOrder: -1 }]) {
    assert.equal((await request('POST', '/admin/songs', body)).status, 400);
  }
  const tablature = 'e|--0--|';
  const created = await request('POST', '/admin/songs', { title: 'Fixture', artist: 'Test', bpm: 92,
    tablature, difficulty: 'medium', active: true, sortOrder: 4, createdBy: 'spoofed' });
  assert.equal(created.status, 201);
  const id = created.body.id;
  assert.equal(created.body.createdBy, 'admin-test');
  assert.equal(created.body.bpm, 92);
  assert.equal(created.body.sortOrder, 4);
  assert.equal((await request('GET', '/songs')).body.items[0].id, id);
  const path = '/admin/songs/' + id;
  assert.equal((await request('PUT', path, { active: false })).body.active, false);
  assert.deepEqual((await request('GET', '/songs')).body.items, []);
  assert.equal((await request('GET', '/admin/songs')).body.items.length, 1);
  assert.equal((await request('PUT', path, { active: true })).body.active, true);
  assert.equal((await request('GET', '/songs')).body.items.length, 1);
  const edited = await request('PUT', path, { title: 'Edited', bpm: 100, sortOrder: 2 });
  assert.equal(edited.body.title, 'Edited');
  assert.equal(edited.body.tablature, tablature);
  assert.deepEqual(edited.body.content, { preserved: true });
  assert.equal(edited.body.createdBy, 'admin-test');
  assert.equal((await request('PUT', path, { active: 'false' })).status, 400);
  assert.equal((await request('PUT', '/admin/songs/missing', { active: false })).status, 404);
  assert.equal((await request('DELETE', path)).status, 204);
  assert.equal((await request('DELETE', path)).status, 404);
  assert.deepEqual((await request('GET', '/songs')).body.items, []);
});

test('delete preserves educational references when PostgreSQL rejects a linked song', async () => {
  pool.query = async () => { throw Object.assign(new Error('foreign key'), { code: '23503' }); };
  let status;
  let body;
  const res = { status(code) { status = code; return this; }, json(value) { body = value; return this; } };
  await controller.deleteAdmin({ params: { id: 'linked' } }, res, error => { throw error; });
  assert.equal(status, 409);
  assert.match(body.error, /arquive/);
});
