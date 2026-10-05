const assert = require('node:assert/strict');
const test = require('node:test');
const { requireAdmin, requireConfigurator } = require('../src/middleware/authMiddleware');

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

test('requireAdmin retorna 403 para student', () => {
  const req = { user: { id: 'student-1', role: 'student' } };
  const res = response();
  let nextCalled = false;

  requireAdmin(req, res, () => {
    nextCalled = true;
  });

  assert.equal(nextCalled, false);
  assert.equal(res.statusCode, 403);
  assert.deepEqual(res.body, { error: 'Acesso restrito a administradores.' });
});

test('requireAdmin permite admin', () => {
  const req = { user: { id: 'admin-1', role: 'admin' } };
  const res = response();
  let nextCalled = false;

  requireAdmin(req, res, () => {
    nextCalled = true;
  });

  assert.equal(nextCalled, true);
  assert.equal(res.statusCode, 200);
});

test('requireConfigurator retorna 403 para admin', () => {
  const req = { user: { id: 'admin-1', role: 'admin' } };
  const res = response();
  let nextCalled = false;

  requireConfigurator(req, res, () => {
    nextCalled = true;
  });

  assert.equal(nextCalled, false);
  assert.equal(res.statusCode, 403);
});

test('requireConfigurator permite configurator', () => {
  const req = { user: { id: 'config-1', role: 'configurator' } };
  const res = response();
  let nextCalled = false;

  requireConfigurator(req, res, () => {
    nextCalled = true;
  });

  assert.equal(nextCalled, true);
  assert.equal(res.statusCode, 200);
});
