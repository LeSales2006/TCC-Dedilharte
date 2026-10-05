const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');
require('dotenv').config();

async function main() {
  const migrationName = process.argv[2];
  if (!migrationName) {
    console.error('Uso: npm run migrate -- <arquivo.sql>');
    process.exit(1);
  }

  if (!process.env.DATABASE_URL) {
    console.error('DATABASE_URL nao configurada em backend/.env.');
    process.exit(1);
  }

  const migrationPath = path.join(__dirname, '..', 'migrations', migrationName);
  if (!fs.existsSync(migrationPath)) {
    console.error(`Migration nao encontrada: ${migrationPath}`);
    process.exit(1);
  }

  const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false },
  });

  try {
    const sql = fs.readFileSync(migrationPath, 'utf8');
    await pool.query(sql);
    console.log(`Migration aplicada: ${migrationName}`);
  } finally {
    await pool.end();
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
