# Interface e autenticação — 10/11/2026

## Diagnóstico confirmado

- Configurações criava o título no cabeçalho e novamente no conteúdo.
- O editor era um Dialog com 94% da largura e altura total. O ScrollView ficava dentro dessa janela, com os insets do diálogo; o botão Salvar usava um drawable sem espaço lateral.
- O tema Material3 só definia android:colorAccent. O tema noturno substituía a base por uma definição sem paleta; o tint padrão prevalecia sobre o fundo XML do botão Entrar.
- ApiErrors traduz qualquer 401 como sessão expirada. PasswordDialogs usava esse tratamento até para a recuperação pública.
- Teste remoto sem credenciais e sem envio: POST /api/auth/forgot-password com {"email":"invalid"} no Render retornou 401 {"error":"Token de autenticacao ausente."}. As rotas locais de recuperação são públicas. A resposta remota é compatível com rota ainda ausente no deploy e queda no router /api, protegido globalmente por JWT; não houve acesso aos logs internos do Render.
- O controlador local respondia 202 ANTES de consultar o banco/enviar e-mail e apenas registrava falhas posteriores no console.

## Correções desta execução

- Configurações: título único, cartões com altura mínima de 64dp, margens e ícones consistentes, SwitchCompat com cores verde/turquesa e alvo de toque de 48dp. A tela é restaurada após trocar o tema.
- Perfil: tela dentro da Activity existente, cabeçalho fixo e formulário rolável. Reutiliza IDs, carregamento local/remoto, endpoints, foto, metas, nível e notificações. Rascunho e meta semanal sobrevivem à recriação. Cancelar/Voltar retorna às configurações. Requisições são canceladas ao sair/destruir, e callbacks antigos não alteram a nova tela.
- Formulário com rótulos, espaçamentos, campos claros e texto verde-escuro, botão Salvar sem ícone sobreposto e Trocar senha destacado em Segurança da conta. Trocar senha abre o formulário autenticado sem descartar as edições de perfil.
- Login/cadastro: contraste maior com fundo verde-escuro, campos claros e ação ciano com texto verde-escuro. Temas diurno/noturno definem a paleta Material; diálogo de senha recebe tema explícito.
- Insets: a nova tela usa o tratamento já existente da Activity para barras, recorte e teclado, sem uma segunda janela. Fundo das barras acompanha o cabeçalho. Nenhuma barra é escondida pelo app.
- Recuperação: 401/403/404 públicos são apresentados como indisponibilidade do servidor, sem mensagem de sessão expirada e sem logout. Erros não mostram confirmação de envio.
- Backend: 202 somente depois de banco e Resend aceitarem a operação; exige ID de envio do provedor. Configuração, banco, HTTP inválido ou falha do provedor resultam em 503 genérico e remoção do token dessa tentativa.
- Privacidade: a mesma chamada ao provedor é feita para e-mails existentes e ausentes. Os ausentes recebem aviso neutro SEM link/token, evitando diferenças públicas de status/tempo ligadas à existência da conta. Rate limiting existente preservado.
- Rotas desconhecidas em /api/auth terminam com 404 e não caem no JWT de /api.
- Segurança existente preservada: token aleatório de 256 bits, hash SHA-256 no banco, prazo de 30 minutos, uso único transacional, senha atual exigida na troca, bcrypt e revogação das sessões após troca/reset.
- O perfil usa o ID do JWT e uma lista explícita de campos editáveis. Nome, nível e role enviado pelo cliente não promovem o usuário. O teste agora usa nome Administrador e role admin para comprovar isso.
- Nenhum endpoint, coluna ou migration nova foi criado nesta execução. As alterações anteriores do workspace foram preservadas; activityController.js não precisou ser modificado.

## Arquivos alterados nesta execução

Android:
- app/src/main/java/com/example/dedilharte/MainActivity.java
- app/src/main/java/com/example/dedilharte/view/PasswordDialogs.java
- app/src/main/java/com/example/dedilharte/view/PreferencesFields.java
- app/src/main/res/layout/activity_main.xml
- app/src/main/res/layout/activity_login.xml
- app/src/main/res/layout/activity_register.xml
- app/src/main/res/layout/dialog_edit_profile.xml
- app/src/main/res/values/themes.xml
- app/src/main/res/values-night/themes.xml
- app/src/main/res/values/styles.xml
- app/src/main/res/values/profile_ids.xml
- app/src/main/res/drawable/bg_profile_field.xml (novo)
- app/src/main/res/drawable/ic_ms_lock.xml (novo)
- app/src/androidTest/java/com/example/dedilharte/ProfilePasswordsInstrumentedTest.java

Backend/documentação:
- backend/src/controllers/passwordController.js
- backend/src/routes/auth.js
- backend/src/services/resetEmail.js
- backend/test/profilePasswords.integration.test.js
- backend/PROFILE_PASSWORDS.md
- UI_AUTH_VALIDATION.md

## Validação

- PostgreSQL 18 local em cluster de testes separado; node --test: **45 aprovados, zero falhas/pulos**. Inclui cadastro, login, perfil/persistência/isolamento de papel, respostas públicas, ausência de JWT, rota desconhecida, banco indisponível, espera pela aceitação do provedor, falha de envio, hash/expiração/token adulterado/reutilização/concorrência, troca de senha, revogação e rate limiting.
- Evidência backend: build/ui-auth-backend-final.log. Provedor simulado; o adaptador de produção continua usando a API real Resend.
- Android: testes de cadastro, entrada por papel, falha de gravação sem perda do estado confirmado, rascunho/recriação, recuperação pública com erros 401/403/404/500/503, troca autenticada, persistência e logout. API simulada e pacote com applicationId separado no AVD RepairApi36/API 36.1.
- A primeira tentativa instrumentada foi interrompida por ANR do System UI do emulador. A segunda passou 32/33; o teste restante exigia barra de navegação, ausente na configuração de teclas físicas do AVD. Logs intermediários preservados em build/ui-auth-instrumented.log e build/ui-auth-instrumented-retest.log.
- Build com JDK padrão 25 falhou por incompatibilidade com Gradle. A execução com JDK 21 passou. Android 7 permanece como minSdk 24; não há imagem API 24 instalada para teste real dessa versão.

## Pendências externas

- A API publicada ainda precisa receber o backend atualizado e as migrations já existentes de perfil/senha, caso ainda não tenham sido aplicadas. Nenhuma migration/implantação foi executada em produção.
- Não há backend/.env nem credenciais de e-mail configuradas neste workspace. Falta ambiente de homologação e destinatário autorizado para comprovar entrega real e uso do link recebido. Não foi enviado e-mail real; aceitação pelo Resend também não garante chegada à caixa de entrada.
- Configuração necessária já documentada em backend/PROFILE_PASSWORDS.md: DATABASE_URL, JWT_SECRET, RESEND_API_KEY, MAIL_FROM verificado, PASSWORD_RESET_BASE_URL HTTPS e TRUST_PROXY_HOPS conforme infraestrutura.
- Nenhum commit/push, alteração de dados de produção ou instalação sobre o aplicativo pessoal.

## Resultado final

- **45 testes backend aprovados**, **32 testes unitários Android aprovados**.
- **14 testes finais de perfil aprovados** em build/ui-auth-profile-final.log. Incluem cadastro, recuperação, troca, edição/persistência, logout, rotação e insets.
- Outros **19 casos instrumentados de autenticação, navegação e ícones** passaram em build/ui-auth-instrumented-retest.log. Os 33 casos foram validados em rodadas; esse log intermediário contém a falha de configuração de barra de navegação descrita acima.
- Verificação visual reforçada passou em build/ui-auth-visual-final.log: barras visíveis, teclado sem cobrir o campo, Salvar totalmente visível em retrato e acionável em paisagem. A execução isolada exigiu inicializar explicitamente o tema claro no teste para não depender da preferência deixada pela rodada anterior; a aplicação não foi alterada para contornar essa expectativa.
- Capturas conferidas: build/ui-auth-edit-profile-keyboard.png, build/ui-auth-edit-profile-actions.png, build/ui-auth-edit-profile-landscape.png, build/ui-auth-settings-light.png e build/ui-auth-settings-dark.png. Dados exclusivamente simulados. As capturas aguardam a renderização após rolagem/rotação.
- Build final com JDK 21: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest e lintDebug aprovados. **Lint: 0 erros, 182 avisos**. Evidência: build/ui-auth-final-gradle.log. git diff --check aprovado.
- APK final: app/build/outputs/apk/debug/app-debug.apk, applicationId com.example.dedilharte, aproximadamente 34 MB. O APK não foi instalado no celular conectado.
- PostgreSQL local de testes encerrado; pacotes de teste removidos e emulador isolado encerrado.
