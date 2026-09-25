# Dedilharte Backend

API REST em Node.js/Express para sincronizar o app Android Dedilharte com PostgreSQL hospedado no Aiven.

## Configuracao

```bash
cd backend
npm install
copy .env.example .env
```

Edite `backend/.env` e preencha:

```env
DATABASE_URL=postgres://USUARIO:SENHA@HOST:PORTA/DATABASE?sslmode=require
PORT=3000
```

## Criar tabelas

Execute o SQL em `schema.sql` no console SQL do Aiven ou via `psql`:

```bash
psql "postgres://USUARIO:SENHA@HOST:PORTA/DATABASE?sslmode=require" -f schema.sql
```

## Iniciar API

```bash
npm start
```

## Testar

```bash
curl http://localhost:3000/health
```

Resposta esperada:

```json
{
  "ok": true,
  "database": true
}
```
