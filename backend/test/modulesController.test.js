const assert = require('node:assert/strict');
const test = require('node:test');

const pool = require('../src/db');
const modulesController = require('../src/controllers/modulesController');

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
    if (sql.startsWith('INSERT INTO learning_modules')) {
      return { rowCount: 1, rows: [] };
    }
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
