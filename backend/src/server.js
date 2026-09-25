const express = require('express');
const pool = require('./db');
const usersRoutes = require('./routes/users');
require('dotenv').config();

const app = express();
const port = process.env.PORT || 3000;

app.use(express.json({ limit: '1mb' }));

app.get('/health', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    return res.json({ ok: true, database: true });
  } catch (error) {
    return res.status(503).json({ ok: false, database: false });
  }
});

app.use('/api', usersRoutes);

app.use((req, res) => {
  res.status(404).json({ error: 'Rota nao encontrada.' });
});

app.use((error, req, res, next) => {
  console.error(error.message);
  res.status(500).json({ error: 'Erro interno do servidor.' });
});

app.listen(port, () => {
  console.log(`Dedilharte API ouvindo na porta ${port}`);
});
