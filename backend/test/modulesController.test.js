const assert = require('node:assert/strict');
const test = require('node:test');

const pool = require('../src/db');
const modulesController = require('../src/controllers/modulesController');
const songsController = require('../src/controllers/songsController');

function response() {
  return {
    statusCode: 200,
    body: undefined,
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(payload) {
      this.body = payload;
      return this;
    },
    send() { return this; },
  };
}

async function run(handler, req) {
  const res = response();
  let nextError;
  await handler(req, res, (error) => {
    nextError = error;
  });
  if (nextError) {
    throw nextError;
  }
  return res;
}

test('listStaff lista somente as duas trivias fixas', async () => {
  let moduleSelectParams;
  pool.query = async (sql, params) => {
    if (sql.includes('FROM learning_modules')) {
      moduleSelectParams = params;
      assert.match(sql, /WHERE id = ANY\(\$1\)/);
      return {
        rowCount: 2,
        rows: [
          { id: 'module_trivia_beginner', name: 'Trivia Iniciante', module_type: 'trivia', description: '', sort_order: 1, active: true },
          { id: 'module_trivia_intermediate', name: 'Trivia Intermediário', module_type: 'trivia', description: '', sort_order: 2, active: true },
        ],
      };
    }
    if (sql.includes('FROM learning_module_items')) {
      assert.deepEqual(params, [['module_trivia_beginner', 'module_trivia_intermediate']]);
      return { rowCount: 0, rows: [] };
    }
    throw new Error(`Query inesperada: ${sql}`);
  };

  const res = await run(modulesController.listStaff, { params: {} });

  assert.deepEqual(moduleSelectParams, [['module_trivia_beginner', 'module_trivia_intermediate']]);
  assert.deepEqual(res.body.items.map((item) => item.id), ['module_trivia_beginner', 'module_trivia_intermediate']);
});

test('createModule bloqueia criacao de novas trivias/modulos', async () => {
  let queryCalled = false;
  pool.query = async () => {
    queryCalled = true;
    return { rowCount: 0, rows: [] };
  };

  const res = await run(modulesController.createModule, {
    user: { id: 'admin-1' },
    body: { name: 'Trivia Teste', moduleType: 'trivia' },
  });

  assert.equal(queryCalled, false);
  assert.equal(res.statusCode, 409);
});

// Controller regression: the database adapter is simulated, not a live PostgreSQL.
for (const moduleId of ['module_trivia_beginner', 'module_trivia_intermediate']) {
  test(`CRUD visible to both readers, isolated by module: ${moduleId}`, async () => {
    const modules = ['module_trivia_beginner', 'module_trivia_intermediate'].map(id => ({
      id, name: id, module_type: 'trivia', active: true,
    }));
    let rows = [];
    const songs = new Map();
    const tablature = 'e|--0--|\nB|-----|\nG|-----|\nD|-----|\nA|-----|\nE|-----|';
    const choices = new Map();
    const query = async (sql, params = []) => {
      const result = values => ({ rows: values, rowCount: values.length });
      if (['BEGIN', 'COMMIT', 'ROLLBACK'].includes(sql)) return result([]);
      if (sql.includes('INSERT INTO songs')) {
        const [id, title, artist, difficulty, recommended_bpm, tab, queue_order, in_queue, status] = params;
        const song = { id, title, artist, difficulty, recommended_bpm, tablature: tab, queue_order, in_queue, status };
        songs.set(id, song);
        return result([song]);
      }
      if (sql.includes('SELECT id FROM songs')) {
        assert.match(sql, /FOR KEY SHARE/);
        return result(songs.has(params[0]) ? [songs.get(params[0])] : []);
      }
      if (sql.includes('FROM learning_modules')) {
        assert.match(sql, /id = ANY\(\$1\)/);
        return result(modules.filter(m => params[0].includes(m.id) && (!sql.includes('active = TRUE') || m.active)));
      }
      if (sql.includes('FROM learning_module_items i')) {
        assert.match(sql, /i.module_id = ANY\(\$1\)/);
        return result(rows.filter(i => params[0].includes(i.module_id) && (!sql.includes('i.active = TRUE') || i.active))
          .map(i => ({ ...i, ...choices.get(i.id) })));
      }
      if (sql.includes('INSERT INTO learning_module_items')) {
        const [id, module_id, item_type, song_id, title, instructions, sort_order, active] = params;
        const row = { id, module_id, item_type, song_id, title, instructions, sort_order, active };
        rows.push(row);
        return result([row]);
      }
      if (sql.includes('INSERT INTO learning_multiple_choice')) {
        const [id, question, options, correct_index, explanation] = params;
        choices.set(id, { question, options: JSON.parse(options), correct_index, explanation });
        return result([]);
      }
      if (sql.includes('DELETE FROM learning_multiple_choice')) { choices.delete(params[0]); return result([]); }
      if (sql.includes('UPDATE learning_module_items')) {
        const [id, item_type, song_id, title, instructions, sort_order, active, ids] = params;
        const row = rows.find(i => i.id === id && ids.includes(i.module_id));
        if (!row) return result([]);
        Object.assign(row, { item_type, song_id, title, instructions, sort_order, active });
        return result([row]);
      }
      if (sql.startsWith('DELETE FROM learning_module_items')) {
        const deleted = rows.filter(i => i.id === params[0] && params[1].includes(i.module_id));
        rows = rows.filter(i => !deleted.includes(i));
        return result(deleted);
      }
      throw new Error(`Unexpected query (reads must not seed/reactivate modules): ${sql}`);
    };
    pool.query = query;
    pool.connect = async () => ({ query, release() {} });
    const createdSong = await run(songsController.createAdmin, { body: {
      title: 'Real server ID in test', artist: 'Artist', tablature, recommendedBpm: 60, status: 'publicada',
    } });
    assert.equal(createdSong.statusCode, 201);
    const songId = createdSong.body.id;
    assert.match(songId, /^song_/);
    assert.equal(songs.get(songId).tablature, tablature);
    const read = handler => run(handler, {});
    for (const handler of [modulesController.listPublic, modulesController.listStaff]) {
      assert.deepEqual((await read(handler)).body.items.map(m => m.items), [[], []]);
    }
    for (const type of ['multiple_choice', 'song']) {
      const body = { id: type, itemType: type, title: 'Created',
        ...(type === 'song' ? { songId }
          : { question: 'Question?', options: ['A', 'B'], correctIndex: 1 }) };
      assert.equal((await run(modulesController.createItem, { params: { id: moduleId }, body, user: { id: 'staff' } })).statusCode, 201);
      for (const handler of [modulesController.listPublic, modulesController.listStaff]) {
        const list = (await read(handler)).body.items;
        assert.equal(list.find(m => m.id === moduleId).items[0].moduleId, moduleId);
        assert.equal(list.find(m => m.id !== moduleId).items.length, 0);
        const item = list.find(m => m.id === moduleId).items[0];
        assert.equal(item.itemType, type);
        if (type === 'multiple_choice') {
          assert.deepEqual(item.multipleChoice.options, ['A', 'B']);
          assert.equal(item.multipleChoice.correctIndex, handler === modulesController.listStaff ? 1 : undefined);
        } else assert.equal(item.songId, songId);
      }
      await run(modulesController.updateItem, { params: { id: type }, body: { ...body, title: 'Edited' } });
      assert.equal((await read(modulesController.listPublic)).body.items.find(m => m.id === moduleId).items[0].title, 'Edited');
      await run(modulesController.updateItem, { params: { id: type }, body: { ...body, active: false } });
      assert.equal((await read(modulesController.listPublic)).body.items.find(m => m.id === moduleId).items.length, 0);
      assert.equal((await read(modulesController.listStaff)).body.items.find(m => m.id === moduleId).items.length, 1);
      assert.equal((await run(modulesController.deleteItem, { params: { id: type } })).statusCode, 204);
      assert.equal(songs.get(songId).tablature, tablature, 'unlink must preserve global song and ASCII');
      for (const handler of [modulesController.listPublic, modulesController.listStaff]) {
        assert.deepEqual((await read(handler)).body.items.map(m => m.items), [[], []]);
      }
    }
    modules[0].active = false;
    assert.deepEqual((await read(modulesController.listPublic)).body.items.map(m => m.id), ['module_trivia_intermediate']);
    assert.equal(modules[0].active, false);
    modules.length = 0;
    assert.deepEqual((await read(modulesController.listPublic)).body.items, []);
  });
}
