const assert = require('node:assert/strict');
const test = require('node:test');

const pool = require('../src/db');
const arpeggiosController = require('../src/controllers/arpeggiosController');

function response() {
  return {
    statusCode: 200,
    body: undefined,
    sent: false,
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(payload) {
      this.body = payload;
      return this;
    },
    send() {
      this.sent = true;
      return this;
    },
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

test('deleteAdmin desativa somente o arpejo do ID informado', async () => {
  const arpeggios = [
    { id: 'arp_1', active: true },
    { id: 'arp_2', active: true },
    { id: 'arp_3', active: true },
  ];

  pool.query = async (sql, params) => {
    assert.match(sql, /WHERE id = \$1 AND active = TRUE/);
    assert.deepEqual(params, ['arp_2']);

    const item = arpeggios.find((arpeggio) => arpeggio.id === params[0] && arpeggio.active);
    if (!item) {
      return { rowCount: 0, rows: [] };
    }
    item.active = false;
    return { rowCount: 1, rows: [{ id: item.id }] };
  };

  const res = await run(arpeggiosController.deleteAdmin, { params: { id: 'arp_2' } });

  assert.equal(res.statusCode, 204);
  assert.equal(arpeggios[0].active, true);
  assert.equal(arpeggios[1].active, false);
  assert.equal(arpeggios[2].active, true);
});

test('deleteAdmin aborta sem query quando ID esta ausente', async () => {
  let queryCalled = false;
  pool.query = async () => {
    queryCalled = true;
    return { rowCount: 0, rows: [] };
  };

  const res = await run(arpeggiosController.deleteAdmin, { params: { id: 'undefined' } });

  assert.equal(queryCalled, false);
  assert.equal(res.statusCode, 400);
  assert.deepEqual(res.body, { error: 'ID do arpejo e obrigatorio.' });
});

test('listAdmin consulta somente arpejos ativos', async () => {
  let call = 0;
  pool.query = async (sql) => {
    assert.match(sql, /WHERE active = TRUE/);
    call++;
    if (call === 1) {
      return { rowCount: 1, rows: [{ total: 2 }] };
    }
    return {
      rowCount: 2,
      rows: [
        { id: 'arp_1', title: 'C', chord: 'C', notes: [], difficulty: 'easy', active: true, sort_order: 1 },
        { id: 'arp_3', title: 'E', chord: 'E', notes: [], difficulty: 'easy', active: true, sort_order: 3 },
      ],
    };
  };

  const res = await run(arpeggiosController.listAdmin, { params: {} });

  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.body.items.map((item) => item.id), ['arp_1', 'arp_3']);
});

test('listAdmin restaura arpejos padrao quando nao ha nenhum ativo', async () => {
  const upsertedIds = [];
  let selectCalled = false;

  pool.query = async (sql, params) => {
    if (sql.startsWith('SELECT COUNT')) {
      return { rowCount: 1, rows: [{ total: 0 }] };
    }
    if (sql.startsWith('INSERT INTO arpeggios')) {
      upsertedIds.push(params[0]);
      assert.match(sql, /ON CONFLICT \(id\) DO UPDATE/);
      assert.match(sql, /active = TRUE/);
      return { rowCount: 1, rows: [] };
    }
    if (sql.includes('SELECT id, title')) {
      selectCalled = true;
      assert.match(sql, /WHERE active = TRUE/);
      return {
        rowCount: 7,
        rows: upsertedIds.map((id, index) => ({
          id,
          title: id,
          chord: '',
          notes: [],
          difficulty: 'easy',
          active: true,
          sort_order: index + 1,
        })),
      };
    }
    throw new Error(`Query inesperada: ${sql}`);
  };

  const res = await run(arpeggiosController.listAdmin, { params: {} });

  assert.equal(selectCalled, true);
  assert.equal(upsertedIds.length, 7);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.items.length, 7);
});
