# Perfil, metas e senhas

## Implantação (pendente; não executada em produção)

1. Fazer backup e revisar as migrations existentes. Em instalações novas, aplicar `schema.sql`, `2026-09-28_phase1_functional.sql`, `2026-11-04_auth_songs_compat.sql` e depois `2026-11-10_password_recovery_codes.sql`. Em instalações já atualizadas, aplicar somente a migration nova, por procedimento de implantação autorizado.
2. A migration nova é transacional e repetível. Adiciona preferências a `users`, `password_recovery_requests` e `auth_rate_limits`; não remove usuários, conteúdo ou progresso. Usuários existentes recebem nível declarado Iniciante, meta de 10 músicas e notificações desativadas. A meta semanal existente é preservada.
3. Publicar a API com a migration aplicada **antes** de distribuir o novo APK. Não há execução automática de migration na inicialização.
4. Usar Node.js 20+; manter DATABASE_URL e JWT_SECRET. Configurar BREVO_API_KEY, MAIL_FROM de remetente verificado no Brevo (endereço simples ou `Nome <endereço>`) e PASSWORD_RECOVERY_SECRET (segredo aleatório com pelo menos 32 bytes, estável e igual em todas as instâncias). A recuperação retorna 503 se faltar configuração ou se o segredo tiver menos de 32 bytes. Não há migração automática na inicialização.
5. Configurar `TRUST_PROXY_HOPS` somente após conferir a topologia de proxies. Em Render com um proxy confiável, usar `1`. Sem variável, Express usa o endereço da conexão. Não permitir acesso direto que contorne o proxy configurado.
6. Homologar entrega real de e-mail, incluindo spam e domínio verificado. Os testes usam um provedor simulado e não enviam e-mails reais.

Referências: [envio de e-mail Brevo API v3](https://developers.brevo.com/reference/send-transac-email), [insets Android](https://developer.android.com/develop/ui/views/layout/edge-to-edge).

## Contrato

- `POST /api/auth/register`: campos existentes e `initialLevel` (`beginner`/`intermediate`), `weeklyGoal` (inteiro 1–7), `songGoal` (inteiro 1–2147483647), `notificationsEnabled` (booleano). Omissão usa os padrões para compatibilidade. Não aceita promoção de papel.
- `GET /api/auth/me`, `GET /api/profile` e `PUT /api/profile`: retornam preferências. A edição usa exclusivamente o usuário do JWT, valida dados e grava horário do servidor. Campos de preferências omitidos na edição são preservados. Não altera o progresso nem o nível calculado.
- POST `/api/auth/password-recovery/request`: `{email}`. Público; retorna 202 com `requestId`, `expiresIn: 600`, `resendAfter: 60` apenas após commit e aceitação do e-mail pela API v3 do Brevo. Código uniforme de 6 dígitos, gerado por `crypto.randomInt`, válido por 10 minutos. Apenas HMAC-SHA256 vinculado à solicitação fica no banco.
- POST `/api/auth/password-recovery/verify`: `{requestId,code}`. Até 5 tentativas persistidas. Consome o código e retorna `authorization` aleatória de 256 bits, válida por 5 minutos. Apenas seu SHA-256 é armazenado, associado à solicitação.
- POST `/api/auth/password-recovery/reset`: `{requestId,authorization,newPassword,confirmPassword}`. Não recebe novamente o código. Consome autorização uma única vez, inclusive sob concorrência. Senha bcrypt custo 12, mínimo 8 caracteres com letra e número, máximo 72 bytes UTF-8.
- Solicitações para contas ausentes/bloqueadas recebem o mesmo código por e-mail, os mesmos estados e respostas, mas não alteram usuários. Isso evita revelar contas tanto na API quanto no conteúdo da mensagem. Falhas de configuração/banco/provedor não são apresentadas como sucesso.
- Reenvio invalida códigos e autorizações anteriores. Reset/troca incrementam token_version e invalidam sessões e demais solicitações do usuário. As transações bloqueiam primeiro o usuário e depois a solicitação para manter uso único e evitar inversão de locks.
- POST `/api/auth/change-password`: JWT e `{currentPassword,newPassword,confirmPassword}`. A troca autenticada continua exigindo a senha atual.
- Recuperação: 5 solicitações/IP/15min, intervalo de 60s/e-mail e até 3 envios/e-mail/hora; verificação 30/IP/15min, reset 10/IP/15min. Limites persistem em auth_rate_limits; 429 inclui Retry-After. As outras rotas mantêm seus limites. Respostas de recuperação usam Cache-Control: no-store.
- Android: Login → e-mail → código → nova senha → login, com rolagem, teclado numérico, colagem de seis dígitos, temas claro/escuro e insets. Estado e autorização sobrevivem à rotação em ViewModel, sem gravar código, senha ou autorização em preferências/Bundle. Se o processo morrer, uma nova solicitação será necessária.

- Dashboard reutiliza `/api/activity/summary`, `/api/modules`, `/api/module-progress`, `/api/songs`, `/api/songs/progress`. Música aprendida/meta pessoal, percentual limitado a 100%; quantidade real pode superar a meta. Trivias e cálculo de nível permanecem independentes do nível declarado.

## Android e operação

Preferências são armazenadas na sessão do usuário autenticado. O perfil só altera o estado local após confirmação do servidor; falhas mantêm os últimos indicadores válidos. A permissão `POST_NOTIFICATIONS` é solicitada no Android 13+ após opção explícita por notificações; negar a permissão não desfaz a preferência salva. A preferência não cria, por si só, um serviço de push/agendamento.

Abrir o app busca o perfil sem reenviar uma cópia local sobre edições de outro dispositivo. Revisões remotas são comparadas com o último horário confirmado pelo servidor, não com o relógio do aparelho. Edição de perfil e troca de senha fixam a autorização da sessão que abriu o formulário; respostas tardias não alteram a sessão de outro usuário.

Tokens revogados são recusados na próxima chamada autenticada. Como antes, um aparelho offline pode abrir seu cache enquanto o JWT local não expirou; não há revogação remota instantânea sem conexão.

Pode-se agendar manutenção dos **novos registros técnicos** expirados em `auth_rate_limits` e `password_recovery_requests`, conforme política de retenção; nenhuma limpeza é executada pela migration ou por este trabalho.

Testes de banco exigem `TEST_DATABASE_URL` apontando exclusivamente para localhost. Criam e removem apenas schemas isolados de teste. Para Android, usar applicationId de teste separado do app de uso pessoal; testes de instrumentação limpam a sessão do pacote de teste.
