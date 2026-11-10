# Recuperação por código — arquivos e validação

## Arquivos alterados/criados

- `NIGHT_THEME_VALIDATION.md`
- `REPAIR_VALIDATION.md`
- `UI_AUTH_VALIDATION.md`
- `app/src/androidTest/java/com/example/dedilharte/NightThemeInstrumentedTest.java`
- `app/src/androidTest/java/com/example/dedilharte/ProfilePasswordsInstrumentedTest.java`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/dedilharte/LoginActivity.java`
- `app/src/main/java/com/example/dedilharte/network/DedilharteApiClient.java`
- `app/src/main/java/com/example/dedilharte/network/DedilharteApiService.java`
- `app/src/main/java/com/example/dedilharte/network/model/PasswordRequest.java`
- `app/src/main/java/com/example/dedilharte/view/PasswordDialogs.java`
- `app/src/main/res/layout/activity_login.xml`
- `backend/PROFILE_PASSWORDS.md`
- `backend/migrations/2026-11-08_profile_passwords.sql`
- `backend/src/controllers/passwordController.js`
- `backend/src/routes/auth.js`
- `backend/src/services/resetEmail.js`
- `backend/test/profilePasswords.integration.test.js`
- `app/src/androidTest/java/com/example/dedilharte/PasswordRecoveryInstrumentedTest.java`
- `app/src/main/java/com/example/dedilharte/PasswordRecoveryActivity.java`
- `app/src/main/java/com/example/dedilharte/network/model/RecoveryRequest.java`
- `app/src/main/java/com/example/dedilharte/network/model/RecoveryResponse.java`
- `app/src/main/res/layout/activity_password_recovery.xml`
- `backend/migrations/2026-11-10_password_recovery_codes.sql`
- `PASSWORD_RECOVERY_VALIDATION.md` (este relatório)

## Removidos

- `backend/src/resetPasswordPage.js`

O controlador, as rotas, o adaptador Resend, a migration anterior e os testes/documentos foram atualizados para eliminar o fluxo anterior. JWT, controles compartilhados, perfil e troca autenticada foram preservados.

## Resultados

- Backend: 49 testes aprovados, sem falhas ou skips; PostgreSQL local, schemas isolados e envio Resend simulado. Código correto/incorreto/expirado, 5 tentativas, reenvio, cooldown e limites por IP/e-mail, privacidade de contas ausentes/bloqueadas, uso único concorrente, autorização vinculada/expirada, bcrypt, sessões revogadas, login e regressões de perfil/músicas/progresso.
- Migration executada duas vezes apenas no schema local de teste; usuários, hashes, conteúdo e progresso preservados.
- Android: 32 testes unitários e 17 instrumentados aprovados no emulador API 36.1 (6 do novo fluxo, 10 regressões, 1 tema). Colagem com zero inicial, rotação com autorização em memória, validação/confirmar senha, falhas HTTP/rede, bloqueios de reenvio, retorno ao login, temas, perfil, troca autenticada e logout.
- As primeiras execuções foram afetadas por ANR do System UI e timeout do callback legado de fechamento do teclado no emulador. O teste passou a usar insets para fechar/verificar o teclado; todas as execuções finais passaram.
- `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`: BUILD SUCCESSFUL com JDK 21. Lint: 0 erros, 203 avisos. `git diff --check`: aprovado.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, applicationId final `com.example.dedilharte`. Instalações de teste usaram somente o pacote isolado `com.example.dedilharte.navigationtest`.

## SQL

[Migration aditiva e idempotente](backend/migrations/2026-11-10_password_recovery_codes.sql): adiciona initial_level, song_goal e notifications_enabled quando ausentes; cria password_recovery_requests e auth_rate_limits. Não executada em produção.

## Render/Resend pendentes

- Configurar `RESEND_API_KEY` e `MAIL_FROM` de [domínio verificado no Resend](https://resend.com/docs/dashboard/domains/introduction).
- Configurar `PASSWORD_RECOVERY_SECRET`: segredo aleatório de pelo menos 32 bytes, distinto do JWT_SECRET, estável e igual em todas as instâncias. Exemplo de geração local: `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`.
- Manter `DATABASE_URL` e `JWT_SECRET`; conferir `TRUST_PROXY_HOPS` conforme os proxies confiáveis do Render e Node.js 20+.
- Aplicação da migration e publicação da API ficam para procedimento posterior autorizado, antes de distribuir o APK. Homologar entrega real/caixa de entrada/spam com domínio e destinatário autorizados; nenhum e-mail real foi enviado nestes testes.

Nenhuma migration de produção, commit, push ou deploy foi executado.

