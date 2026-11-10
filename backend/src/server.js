const express = require('express');
const pool = require('./db');
const authRoutes = require('./routes/auth');
const usersRoutes = require('./routes/users');
require('dotenv').config();

const app = express();
const port = process.env.PORT || 3000;
// Set to the known reverse-proxy hop count on Render; never trust arbitrary forwarded headers.
if (/^[1-9]\d?$/.test(process.env.TRUST_PROXY_HOPS || '')) app.set('trust proxy', Number(process.env.TRUST_PROXY_HOPS));

app.use(express.json({ limit: '1mb' }));

app.get('/health', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    return res.json({ ok: true, database: true });
  } catch (error) {
    console.error('ERRO AIVEN:', {
      message: error.message,
      code: error.code
    });

    return res.status(503).json({
      ok: false,
      database: false
    });
  }
});

app.use('/api/auth', authRoutes);
app.use('/api', usersRoutes);

app.use((req, res) => {
  res.status(404).json({ error: 'Rota nao encontrada.' });
});

app.use(require('./middleware/errorHandler'));

app.listen(port, () => {
  console.log(`Dedilharte API ouvindo na porta ${port}`);
});
