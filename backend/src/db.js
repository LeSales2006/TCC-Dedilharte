const { Pool } = require('pg');
require('dotenv').config();

if (!process.env.DATABASE_URL) {
  console.warn('DATABASE_URL nao configurada. Configure backend/.env antes de iniciar em ambiente real.');
}

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: {
    rejectUnauthorized: false,
  },
  connectionTimeoutMillis: 10000,
  idleTimeoutMillis: 30000,
  statement_timeout: 15000,
  query_timeout: 20000,
});

pool.on('error', (error) => {
  console.error('Conexao ociosa PostgreSQL encerrada', { code: error.code });
});

module.exports = pool;
