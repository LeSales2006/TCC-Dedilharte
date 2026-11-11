const assert = require('node:assert/strict');
const test = require('node:test');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

process.env.JWT_SECRET = 'test-secret-only-for-unit-tests';

const pool = require('../src/db');
const authController = require('../src/controllers/authController');
const { VERSION } = require('../src/terms');
const acceptance = { termsAccepted: true, termsVersion: VERSION };
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
    body: { name: 'Leticia', email: 'TESTE@DEDILHARTE.COM', password: 'Teste123', ...acceptance },
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
    body: { name: 'Leticia', email: 'teste@dedilharte.com', password: 'Teste123', ...acceptance },
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

test('database failure with a valid JWT reaches HTTP error handling, not 401', async () => {
  const outage = Object.assign(new Error('unavailable'), { code: 'ECONNREFUSED' });
  pool.query = async () => { throw outage; };
  const token = jwt.sign({ sub: 'user-1' }, process.env.JWT_SECRET);
  const res = response();
  let forwarded;
  await authenticateToken({ headers: { authorization: `Bearer ${token}` } }, res, error => { forwarded = error; });
  assert.equal(forwarded, outage);
  assert.equal(res.body, undefined);
});

test('expired, revoked, blocked and subject-less JWT sessions are rejected', async () => {
  for (const [claims, row, status] of [
    [{ sub: 'user-1', exp: 1 }, {}, 401],
    [{}, {}, 401],
    [{ sub: 'user-1', tokenVersion: 1 }, { token_version: 2 }, 401],
    [{ sub: 'user-1' }, { account_status: 'blocked' }, 403],
  ]) {
    pool.query = async () => ({ rowCount: 1, rows: [{ id: 'user-1', ...row }] });
    const token = jwt.sign(claims, process.env.JWT_SECRET);
    const res = await run(authenticateToken, { headers: { authorization: `Bearer ${token}` } });
    assert.equal(res.statusCode, status);
  }
});

test('missing login body returns 400 and concurrent duplicate registration returns 409', async () => {
  assert.equal((await run(authController.login, {})).statusCode, 400);
  pool.query = async sql => {
    if (sql.startsWith('SELECT')) return { rowCount: 0, rows: [] };
    throw Object.assign(new Error('duplicate'), { code: '23505' });
  };
  const res = await run(authController.register, { body: { name: 'Test', email: 'test@example.com', password: 'Teste123', ...acceptance } });
  assert.equal(res.statusCode, 409);
});

test('REGISTER rejects missing, false, coerced or outdated acceptance and non-student roles before queries', async () => {
  pool.query = async () => { throw new Error('Rejected registration must not touch users'); };
  for (const fields of [{}, { termsAccepted: false, termsVersion: VERSION },
    { termsAccepted: 'true', termsVersion: VERSION }, { termsAccepted: 1, termsVersion: VERSION },
    { termsAccepted: true }, { termsAccepted: true, termsVersion: 'old' },
    ...['admin', 'configurator', 'STUDENT', '', null].map(role => ({ ...acceptance, role }))]) {
    const res = await run(authController.register, { body: {
      name: 'Aluno', email: 'student@example.com', password: 'Password123', ...fields,
    } });
    assert.equal(res.statusCode, 400, JSON.stringify(fields));
  }
});
