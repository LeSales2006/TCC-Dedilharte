const assert = require('node:assert/strict');
const test = require('node:test');
const pool = require('../src/db');
const controller = require('../src/controllers/songsController');

for (const name of ['listPublic', 'listAdmin']) {
  test(`${name}: songs vazio retorna items vazio sem seed`, async () => {
    let calls = 0;
    pool.query = async sql => {
      calls++;
      assert.match(sql, /^SELECT /);
      assert.match(sql, /FROM songs/);
      if (name === 'listPublic') assert.match(sql, /status = 'publicada' AND in_queue = TRUE/);
      return { rows: [], rowCount: 0 };
    };
    let body;
    let failure;
    await controller[name]({}, { json(value) { body = value; } }, error => { failure = error; });
    assert.equal(failure, undefined);
    assert.deepEqual(body, { items: [] });
    assert.equal(calls, 1);
  });
}
