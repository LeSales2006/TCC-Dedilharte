const assert = require('node:assert/strict');
const test = require('node:test');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

process.env.JWT_SECRET = 'test-secret-only-for-unit-tests';

const pool = require('../src/db');
const authController = require('../src/controllers/authController');
const { authenticateToken } = require('../src/middleware/authMiddleware');

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

test('REGISTER valido retorna 201, token e user sem password_hash', async () => {
  let insertedPasswordHash = '';
  pool.query = async (sql, params) => {
    if (sql.includes('SELECT id FROM users')) {
      return { rowCount: 0, rows: [] };
    }
    insertedPasswordHash = params[3];
    return {
      rowCount: 1,
      rows: [{ id: params[0], name: params[1], email: params[2], role: 'student' }],
    };
  };

  const res = await run(authController.register, {
    body: { name: 'Leticia', email: 'TESTE@DEDILHARTE.COM', password: 'Teste123' },
  });

  assert.equal(res.statusCode, 201);
  assert.ok(res.body.token);
  assert.equal(res.body.user.email, 'teste@dedilharte.com');
  assert.equal(res.body.user.role, 'student');
  assert.equal(res.body.user.password_hash, undefined);
  assert.notEqual(insertedPasswordHash, 'Teste123');
});

test('REGISTER email duplicado retorna 409', async () => {
  pool.query = async () => ({ rowCount: 1, rows: [{ id: 'existing' }] });

  const res = await run(authController.register, {
    body: { name: 'Leticia', email: 'teste@dedilharte.com', password: 'Teste123' },
  });

  assert.equal(res.statusCode, 409);
});

test('REGISTER senha curta retorna 400', async () => {
  const res = await run(authController.register, {
    body: { name: 'Leticia', email: 'teste@dedilharte.com', password: 'T1' },
  });

  assert.equal(res.statusCode, 400);
});

test('LOGIN correto retorna 200 e token', async () => {
  const hash = await bcrypt.hash('Teste123', 4);
  pool.query = async () => ({
    rowCount: 1,
    rows: [{
      id: 'user-1',
      name: 'Leticia',
      email: 'teste@dedilharte.com',
      password_hash: hash,
      role: 'student',
    }],
  });

  const res = await run(authController.login, {
    body: { email: 'teste@dedilharte.com', password: 'Teste123' },
  });

  assert.equal(res.statusCode, 200);
  assert.ok(res.body.token);
  assert.equal(res.body.user.id, 'user-1');
});

test('LOGIN senha incorreta retorna 401', async () => {
  const hash = await bcrypt.hash('Teste123', 4);
  pool.query = async () => ({
    rowCount: 1,
    rows: [{
      id: 'user-1',
      name: 'Leticia',
      email: 'teste@dedilharte.com',
      password_hash: hash,
      role: 'student',
    }],
  });

  const res = await run(authController.login, {
    body: { email: 'teste@dedilharte.com', password: 'Errada123' },
  });

  assert.equal(res.statusCode, 401);
});

test('/auth/me com token retorna 200', async () => {
  pool.query = async () => ({
    rowCount: 1,
    rows: [{ id: 'user-1', name: 'Leticia', email: 'teste@dedilharte.com', role: 'student' }],
  });

  const token = jwt.sign({ role: 'student' }, process.env.JWT_SECRET, {
    subject: 'user-1',
    expiresIn: '7d',
  });
  const req = {
    headers: { authorization: `Bearer ${token}` },
  };
  const res = response();

  await new Promise((resolve, reject) => {
    authenticateToken(req, res, reject);
    resolve();
  });
  await authController.me(req, res, (error) => {
    throw error;
  });

  assert.equal(res.statusCode, 200);
  assert.equal(res.body.user.id, 'user-1');
});

test('/auth/me sem token retorna 401', () => {
  const req = { headers: {} };
  const res = response();

  authenticateToken(req, res, () => {
    throw new Error('next nao deveria ser chamado');
  });

  assert.equal(res.statusCode, 401);
});
