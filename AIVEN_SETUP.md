# Configuracao do PostgreSQL Aiven no Dedilharte

## Arquitetura

```text
Android App
   ↓
API REST Node.js em backend/
   ↓
PostgreSQL Aiven
```

O app Android nao se conecta diretamente ao PostgreSQL. As credenciais do Aiven ficam apenas no backend.

## Criar ou usar PostgreSQL no Aiven

1. Acesse o Aiven Console.
2. Crie um servico PostgreSQL ou abra um servico existente.
3. Na pagina do servico, encontre:
   - `Host`
   - `Port`
   - `Database`
   - `User`
   - `Password`
   - `Service URI`
4. Copie o `Service URI`. Ele sera usado como `DATABASE_URL`.

## Configurar .env

```bash
cd backend
copy .env.example .env
```

Edite `backend/.env`:

```env
DATABASE_URL=postgres://USUARIO:SENHA@HOST:PORTA/DATABASE?sslmode=require
PORT=3000
```

Substitua os placeholders pelos dados reais do Aiven. Nao coloque o arquivo `.env` no Git.

## Instalar backend

```bash
cd backend
npm install
```

## Criar tabelas

Opcao 1: cole o conteudo de `backend/schema.sql` no SQL Console do Aiven.

Opcao 2: use `psql`:

```bash
cd backend
psql "postgres://USUARIO:SENHA@HOST:PORTA/DATABASE?sslmode=require" -f schema.sql
```

## Iniciar API

```bash
cd backend
npm start
```

## Testar health check

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

Se o banco estiver indisponivel, a API responde `503` com `database: false`.

## Executar Android no emulador

No Android Emulator, o endereco `localhost` aponta para o proprio emulador. Para acessar a API rodando no computador, use:

```text
http://10.0.2.2:3000/
```

Essa URL esta centralizada em `ApiConfig.BASE_URL`.

## localhost, 127.0.0.1, 10.0.2.2 e IP local

- `localhost`: a propria maquina onde o comando esta rodando.
- `127.0.0.1`: equivalente a `localhost`.
- `10.0.2.2`: atalho do Android Emulator para acessar o localhost do computador host.
- IP local: endereco do computador na rede, usado para testar em celular fisico, por exemplo `http://192.168.0.10:3000/`.

Para celular fisico, troque `ApiConfig.BASE_URL` para o IP local do computador ou use uma API hospedada publicamente.

## API hospedada futuramente

Quando publicar o backend em Render, Railway, Fly.io, VPS ou outro provedor, altere apenas:

```java
ApiConfig.BASE_URL
```

Use uma URL HTTPS em producao, por exemplo:

```text
https://api.seudominio.com/
```
