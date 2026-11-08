const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const errorHandler = require('../src/middleware/errorHandler');

test('HTTP errors distinguish malformed JSON, payload limits, DB outages and schema errors', async t => {
  const app = express();
  app.use(express.json({ limit: '100b' }));
  app.post('/echo', (req, res) => res.json(req.body));
  app.get('/outage', (req, res, next) => next(Object.assign(new Error('private connection details'), { code: 'ETIMEDOUT' })));
  app.get('/schema', (req, res, next) => next(Object.assign(new Error('private column details'), { code: '42703' })));
  app.use(errorHandler);
  const server = await new Promise(resolve => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = `http://127.0.0.1:${server.address().port}`;
  for (const [path, options, status] of [
    ['/echo', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{' }, 400],
    ['/echo', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: 'x'.repeat(200) }) }, 413],
    ['/outage', {}, 503], ['/schema', {}, 500],
  ]) {
    const response = await fetch(base + path, options);
    assert.equal(response.status, status);
    assert.doesNotMatch(await response.text(), /private/);
  }
});
