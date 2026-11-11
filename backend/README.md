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
JWT_SECRET=<segredo-forte-definido-no-ambiente>
```

## Criar tabelas

Execute o SQL em `schema.sql` no console SQL do Aiven ou via `psql`:

```bash
psql "postgres://USUARIO:SENHA@HOST:PORTA/DATABASE?sslmode=require" -f schema.sql
```

## Iniciar API

Para um banco existente, revise e aplique explicitamente
`migrations/2026-11-04_auth_songs_compat.sql` e depois
`migrations/2026-11-11_configurator_account_protection.sql` antes de publicar
este backend. A primeira adiciona as colunas de autenticação ausentes, converte
os campos legados de músicas para `bpm`, `sort_order` e `active`, preserva
IDs/conteúdo e impede novos vínculos órfãos. A segunda adiciona
`is_primary_configurator`, cria a proteção imutável da conta principal do
configurador e remove a coluna legada `managed_password`. Nenhuma roda
automaticamente. A validação usa PostgreSQL 18 local.

Antes da migration, execute `diagnostics/preflight_compat.sql` no banco escolhido.
Essa consulta funciona também com os campos legados e mostra apenas metadados e
contagens. `duplicate_email_groups` precisa ser zero: a migration cria o índice
único de e-mail sem diferenciar maiúsculas de minúsculas. Duplicatas abortam toda
a transação; nenhuma conta é removida ou unificada automaticamente. Credenciais
ficam apenas em `password_hash` (bcrypt).

Com `DATABASE_URL` definida no ambiente e o destino revisado, os comandos são:

```powershell
psql --dbname="$env:DATABASE_URL" -v ON_ERROR_STOP=1 -f diagnostics/preflight_compat.sql
# Aplicar somente após revisar o diagnóstico e autorizar a alteração no banco:
psql --dbname="$env:DATABASE_URL" -v ON_ERROR_STOP=1 -f migrations/2026-11-04_auth_songs_compat.sql
psql --dbname="$env:DATABASE_URL" -v ON_ERROR_STOP=1 -f migrations/2026-11-11_configurator_account_protection.sql
psql --dbname="$env:DATABASE_URL" -v ON_ERROR_STOP=1 -f diagnostics/validate_content.sql
```

Em uma instalação nova, use `schema.sql`, as migrations de módulos/progresso
necessárias e, por último, a migration de compatibilidade (para criar a FK após
`learning_module_items`). O arquivo `diagnostics/validate_content.sql` permite
conferir esquema, contagens das duas Trivias, vínculos e tablaturas sem escrita.

Para testes com banco real, defina `TEST_DATABASE_URL` apontando exclusivamente
para PostgreSQL local e execute `npm test`. Os testes criam e removem apenas
schemas `repair_test_*`; nunca usam `DATABASE_URL` para integração.
Há cenários que aplicam a migration diretamente ao esquema legado, sem executar
`schema.sql` antes, e verificam o rollback se já houver e-mails duplicados.

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
