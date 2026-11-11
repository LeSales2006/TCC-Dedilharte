const assert = require('node:assert/strict');
const test = require('node:test');
const bcrypt = require('bcryptjs');

const pool = require('../src/db');
const usersController = require('../src/controllers/usersController');

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
    send(payload) {
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
  if (nextError) throw nextError;
  return res;
}

function managedRow(overrides = {}) {
  return {
    id: 'user-1',
    name: 'Usuario',
    email: 'user@example.com',
    role: 'student',
    weekly_goal: 3,
    account_status: 'active',
    must_change_password: false,
    is_primary_configurator: false,
    has_photo: false,
    profile_photo_updated_at: null,
    created_at: new Date('2026-01-01T00:00:00Z'),
    updated_at: new Date('2026-01-02T00:00:00Z'),
    ...overrides,
  };
}

test('configurator nao pode alterar a propria role nem chamar o banco', async () => {
  const original = pool.query;
  let calls = 0;
  pool.query = async () => { calls += 1; return { rowCount: 0, rows: [] }; };
  try {
    const res = await run(usersController.updateManagedRole, {
      user: { id: 'config-1', role: 'configurator' },
      params: { id: 'config-1' },
      body: { role: 'admin' },
    });
    assert.equal(res.statusCode, 403);
    assert.equal(calls, 0);
  } finally {
    pool.query = original;
  }
});

test('role da conta principal e imutavel mesmo por outro configurador', async () => {
  const original = pool.query;
  let updates = 0;
  pool.query = async (sql) => {
    if (/UPDATE users/i.test(sql)) updates += 1;
    return { rowCount: 1, rows: [managedRow({ id: 'primary', role: 'configurator', is_primary_configurator: true })] };
  };
  try {
    const res = await run(usersController.updateManagedRole, {
      user: { id: 'config-2', role: 'configurator' },
      params: { id: 'primary' },
      body: { role: 'admin' },
    });
    assert.equal(res.statusCode, 403);
    assert.equal(updates, 0);
  } finally {
    pool.query = original;
  }
});

test('conta principal nao pode ser excluida pela propria conta', async () => {
  const original = pool.query;
  let updates = 0;
  pool.query = async (sql) => {
    if (/UPDATE users/i.test(sql)) updates += 1;
    return {
      rowCount: 1,
      rows: [{ id: 'primary', password_hash: 'hash', is_primary_configurator: true }],
    };
  };
  try {
    const res = await run(usersController.deleteOwnAccount, {
      user: { id: 'primary', role: 'configurator' },
      body: { password: 'Secret123' },
    });
    assert.equal(res.statusCode, 403);
    assert.equal(updates, 0);
  } finally {
    pool.query = original;
  }
});

test('configurador comum exclui a propria conta somente com a senha correta', async () => {
  const original = pool.query;
  const passwordHash = await bcrypt.hash('Secret123', 4);
  let anonymized = false;
  pool.query = async (sql) => {
    if (/UPDATE users/i.test(sql)) {
      anonymized = true;
      assert.match(sql, /is_primary_configurator = FALSE/);
      return { rowCount: 1, rows: [] };
    }
    return {
      rowCount: 1,
      rows: [{ id: 'config-1', password_hash: passwordHash, is_primary_configurator: false }],
    };
  };
  try {
    const wrong = await run(usersController.deleteOwnAccount, {
      user: { id: 'config-1', role: 'configurator' },
      body: { password: 'Wrong123' },
    });
    assert.equal(wrong.statusCode, 400);
    assert.equal(anonymized, false);

    const ok = await run(usersController.deleteOwnAccount, {
      user: { id: 'config-1', role: 'configurator' },
      body: { password: 'Secret123' },
    });
    assert.equal(ok.statusCode, 204);
    assert.equal(anonymized, true);
  } finally {
    pool.query = original;
  }
});

test('conta principal nao pode ser bloqueada nem excluida pelo gerenciamento', async () => {
  const original = pool.query;
  let updates = 0;
  pool.query = async (sql) => {
    if (/UPDATE users/i.test(sql)) updates += 1;
    return {
      rowCount: 1,
      rows: [{ id: 'primary', role: 'configurator', account_status: 'active', is_primary_configurator: true }],
    };
  };
  try {
    const blocked = await run(usersController.updateManagedStatus, {
      user: { id: 'admin-1', role: 'admin' },
      params: { id: 'primary' },
      body: { account_status: 'blocked' },
    });
    assert.equal(blocked.statusCode, 403);

    const deleted = await run(usersController.deleteManagedUser, {
      user: { id: 'admin-1', role: 'admin' },
      params: { id: 'primary' },
    });
    assert.equal(deleted.statusCode, 403);
    assert.equal(updates, 0);
  } finally {
    pool.query = original;
  }
});

test('configurador nao exclui a propria conta pela rota legada', async () => {
  const original = pool.query;
  let updates = 0;
  pool.query = async (sql) => {
    if (/UPDATE users/i.test(sql)) updates += 1;
    return {
      rowCount: 1,
      rows: [{ id: 'config-1', role: 'configurator', account_status: 'blocked', is_primary_configurator: false }],
    };
  };
  try {
    const res = await run(usersController.deleteUser, {
      user: { id: 'config-1', role: 'configurator' },
      params: { id: 'config-1' },
    });
    assert.equal(res.statusCode, 403);
    assert.equal(updates, 0);
  } finally {
    pool.query = original;
  }
});

test('criacao de usuario grava apenas password_hash e nao devolve senha', async () => {
  const original = pool.query;
  let insertSql = '';
  let insertParams = [];
  pool.query = async (sql, params) => {
    insertSql = sql;
    insertParams = params;
    return {
      rowCount: 1,
      rows: [managedRow({ id: params[0], name: params[1], email: params[2], role: params[4] })],
    };
  };
  try {
    const res = await run(usersController.createManagedUser, {
      user: { id: 'config-1', role: 'configurator' },
      body: { name: 'Novo Admin', email: 'novo@example.com', password: 'Secret123', role: 'admin' },
    });
    assert.equal(res.statusCode, 201);
    assert.doesNotMatch(insertSql, /managed_password/);
    assert.match(insertSql, /password_hash/);
    assert.equal(insertParams[4], 'admin');
    assert.equal('password' in res.body, false);
    assert.equal('temporaryPassword' in res.body, false);
  } finally {
    pool.query = original;
  }
});
