const assert = require('node:assert/strict');
const test = require('node:test');

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

test('listManagedUsers lista usuarios sem consultar senha em texto claro', async () => {
  pool.query = async (sql) => {
    assert.doesNotMatch(sql, /managed_password/);
    return {
      rowCount: 1,
      rows: [{
        id: 'user-1',
        name: 'Aluno',
        email: 'aluno@example.com',
        role: 'student',
        weekly_goal: 3,
        account_status: 'active',
        must_change_password: false,
        has_photo: false,
        profile_photo_updated_at: null,
        created_at: new Date('2026-01-01T00:00:00Z'),
        updated_at: new Date('2026-01-02T00:00:00Z'),
      }],
    };
  };

  const res = await run(usersController.listManagedUsers, { user: { role: 'configurator' } });

  assert.equal(res.statusCode, 200);
  assert.equal(res.body.items.length, 1);
  assert.equal(res.body.items[0].id, 'user-1');
  assert.equal(res.body.items[0].temporaryPassword, '');
});
