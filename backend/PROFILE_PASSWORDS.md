# Perfil, metas e senhas

## Implantação (pendente; não executada em produção)

1. Fazer backup e revisar as migrations existentes. Em instalações novas, aplicar `schema.sql`, `2026-09-28_phase1_functional.sql`, `2026-11-04_auth_songs_compat.sql` e depois `2026-11-08_profile_passwords.sql`. Em instalações já atualizadas, aplicar somente a migration nova, por procedimento de implantação autorizado.
2. A migration nova é transacional e repetível. Adiciona preferências a `users`, `password_reset_tokens` e `auth_rate_limits`; não remove usuários, conteúdo ou progresso. Usuários existentes recebem nível declarado Iniciante, meta de 10 músicas e notificações desativadas. A meta semanal existente é preservada.
3. Publicar a API com a migration aplicada **antes** de distribuir o novo APK. Não há execução automática de migration na inicialização.
4. Usar Node.js 20+ e manter `DATABASE_URL` e `JWT_SECRET`. Configurar no Render `RESEND_API_KEY`, `MAIL_FROM` (remetente de domínio verificado) e `PASSWORD_RESET_BASE_URL=https://SEU-SERVICO/api/auth/reset-password`. Sem configuração, recuperação retorna 503 para qualquer e-mail; cadastro/login/troca autenticada não dependem do serviço de e-mail.
5. Configurar `TRUST_PROXY_HOPS` somente após conferir a topologia de proxies. Em Render com um proxy confiável, usar `1`. Sem variável, Express usa o endereço da conexão. Não permitir acesso direto que contorne o proxy configurado.
6. Homologar entrega real de e-mail, incluindo spam e domínio verificado. Os testes usam um provedor simulado e não enviam e-mails reais.

Referências: [envio de e-mail Resend](https://resend.com/docs/api-reference/emails/send-email), [insets Android](https://developer.android.com/develop/ui/views/layout/edge-to-edge).

## Contrato

- `POST /api/auth/register`: campos existentes e `initialLevel` (`beginner`/`intermediate`), `weeklyGoal` (inteiro 1–7), `songGoal` (inteiro 1–2147483647), `notificationsEnabled` (booleano). Omissão usa os padrões para compatibilidade. Não aceita promoção de papel.
- `GET /api/auth/me`, `GET /api/profile` e `PUT /api/profile`: retornam preferências. A edição usa exclusivamente o usuário do JWT, valida dados e grava horário do servidor. Campos de preferências omitidos na edição são preservados. Não altera o progresso nem o nível calculado.
- `POST /api/auth/forgot-password`: `{email}`; público, sem JWT. Só retorna 202 depois da gravação e da aceitação do e-mail pelo Resend (resposta com ID de envio). Falhas de configuração, banco ou envio retornam 503 genérico; o token daquela tentativa é removido. Isso confirma aceitação pelo provedor, não entrega na caixa de entrada.
- Privacidade: o status e o corpo HTTP são iguais para endereços cadastrados e ausentes. Ambos passam pelo provedor, inclusive em falhas, evitando diferenciar contas pelo tempo do envio ou por um 503 seletivo. Endereços ausentes recebem apenas um aviso neutro para conferir o endereço usado no cadastro, **sem token/link**. Os limites por IP/e-mail também se aplicam a esses avisos. Não são enviados tokens de outras contas nem informações cadastrais. A existência de uma conta não é informada na resposta pública.
- Rotas desconhecidas em `/api/auth` terminam com 404 nesse router; não passam para o middleware JWT de `/api`. Em 10/11/2026, um POST com e-mail inválido à API Render ainda retornou 401 `Token de autenticacao ausente.`; publicar as rotas locais é necessário para disponibilizar a recuperação.
- `GET /api/auth/reset-password`: formulário público HTTPS. Token no fragmento do link, removido do histórico ao abrir, sem envio no GET. Sem recursos externos, sem cache e com CSP.
- `POST /api/auth/reset-password`: `{token,newPassword,confirmPassword}`. Token aleatório de 256 bits, apenas SHA-256 persistido; expira em 30 minutos. Transação e bloqueio de usuário garantem uso único mesmo com concorrência. Senha bcrypt custo 12; máximo 72 bytes UTF-8, mínimo 8 caracteres com letra e número.
- `POST /api/auth/change-password`: JWT e `{currentPassword,newPassword,confirmPassword}`. Verifica senha atual; troca e recuperação incrementam `token_version`, invalidando todas as sessões e links anteriores. O app volta ao login após a troca.
- Rate limiting persistido no PostgreSQL por IP/conta, chaves HMAC sem e-mail/IP em texto claro; 429 com `Retry-After`. Cadastro 5/IP/15min; login 30/IP/15min e 15/e-mail/15min; recuperação 5/IP/15min e 3/e-mail/hora; reset 10/IP/15min; troca 5/usuário/15min; perfil 30/usuário/15min.
- Dashboard reutiliza `/api/activity/summary`, `/api/modules`, `/api/module-progress`, `/api/songs`, `/api/songs/progress`. Música aprendida/meta pessoal, percentual limitado a 100%; quantidade real pode superar a meta. Trivias e cálculo de nível permanecem independentes do nível declarado.

## Android e operação

Preferências são armazenadas na sessão do usuário autenticado. O perfil só altera o estado local após confirmação do servidor; falhas mantêm os últimos indicadores válidos. A permissão `POST_NOTIFICATIONS` é solicitada no Android 13+ após opção explícita por notificações; negar a permissão não desfaz a preferência salva. A preferência não cria, por si só, um serviço de push/agendamento.

Abrir o app busca o perfil sem reenviar uma cópia local sobre edições de outro dispositivo. Revisões remotas são comparadas com o último horário confirmado pelo servidor, não com o relógio do aparelho. Edição de perfil e troca de senha fixam a autorização da sessão que abriu o formulário; respostas tardias não alteram a sessão de outro usuário.

Tokens revogados são recusados na próxima chamada autenticada. Como antes, um aparelho offline pode abrir seu cache enquanto o JWT local não expirou; não há revogação remota instantânea sem conexão.

Pode-se agendar manutenção dos **novos registros técnicos** expirados em `auth_rate_limits` e `password_reset_tokens`, conforme política de retenção; nenhuma limpeza é executada pela migration ou por este trabalho.

Testes de banco exigem `TEST_DATABASE_URL` apontando exclusivamente para localhost. Criam e removem apenas schemas isolados de teste. Para Android, usar applicationId de teste separado do app de uso pessoal; testes de instrumentação limpam a sessão do pacote de teste.
