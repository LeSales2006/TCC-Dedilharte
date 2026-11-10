# Correções locais — 04/11/2026, 07/11/2026 e 08/11/2026

As seções iniciais preservam o registro de 04/11. Os resultados novos estão em
“Continuação — 07/11/2026”, “Áudio local — 07/11/2026” e
“Navegação das Trivias — 08/11/2026”; as pendências ao final refletem a situação atual.

## Causas e correções

- `schema.sql` não criava todas as colunas de autenticação e usava os nomes antigos de músicas. Schema e migration agora preservam dados e convertem `recommended_bpm`, `queue_order`, `status`/`in_queue` para o contrato atual. A migration é explícita e idempotente; não foi aplicada em produção.
- Erros PostgreSQL eram convertidos em JWT inválido (401), apagando sessões. Agora seguem para tratamento HTTP próprio; conexão, consultas e chamadas Android têm limites de tempo. Respostas de login sem token/usuário válido são recusadas.
- `SongRepository` apagava o cache no construtor, antes do GET e em falhas. Agora mantém respostas públicas válidas em SharedPreferences por usuário, aceita lista vazia apenas quando explicitamente válida e ignora respostas atrasadas. HTTP 401/403 bloqueia o uso offline até revalidação.
- Cache administrativo nunca alimenta o catálogo público. Preferências legadas sem proprietário permanecem intactas e não são importadas. Arpejos receberam isolamento e preservação equivalentes. Perfil, atividades e progresso já possuem chaves por usuário; não foi encontrado Room/SQLite.
- Trivias omitem vínculos com músicas ausentes/inativas; a FK impede novos vínculos órfãos e exclusão de músicas vinculadas. O app verifica o ID vinculado e a tablatura antes do treino. Erros ao obter conexão para editar itens agora encerram a requisição corretamente.
- Conteúdo offline reflete a última publicação confirmada. Uma despublicação remota só pode ser conhecida com nova conexão; respostas válidas atualizam/removem o conteúdo correspondente.

## Arquivos alterados nesta execução

- Em `app/src/main/java/com/example/dedilharte/`: `LoginActivity.java`, `MainActivity.java`, `auth/SessionManager.java`, `data/SongRepository.java`, `data/ArpeggioRepository.java`, novo `data/UserCacheScope.java`, `network/DedilharteApiClient.java`, `network/model/SongResponse.java`, `SongListResponse.java`, `ArpeggioListResponse.java`.
- Em `backend/src/`: `db.js`, `server.js`, `middleware/authMiddleware.js`, novo `middleware/errorHandler.js`, `controllers/authController.js`, `songsController.js`, `modulesController.js`, `moduleProgressController.js`.
- Banco/documentação: `backend/schema.sql`, nova `backend/migrations/2026-11-04_auth_songs_compat.sql`, nova consulta somente de leitura `backend/diagnostics/validate_content.sql`, `backend/README.md`, este relatório.
- Testes backend: `auth.test.js`, `modulesController.test.js`, novos `errorHandler.test.js` e `postgres.integration.test.js`.
- Testes Android: `SongRepositoryInstrumentedTest.java`, `ArpeggioRepositoryInstrumentedTest.java`, `HomeInstrumentedTest.java`, `SongManagementInstrumentedTest.java`, novos `AuthRecoveryInstrumentedTest.java` e `SongJsonTest.java`.

Alterações anteriores de telas, áudio e outros recursos foram preservadas.

## Testes

- Backend: 33 aprovados, incluindo PostgreSQL 18 real em cluster temporário local. Cobertura de esquema novo/legado, reaplicação da migration, login/JWT, seis exercícios de teste (3 por Trivia), publicação, treino/progresso e integridade dos vínculos. Sem acesso ao Aiven.
- Android: 24 testes unitários e 9 instrumentados aprovados. API simulada, emulador separado criado para os testes, rede desativada; o emulador do usuário não foi alterado.
- Gradle/JDK 21: `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` aprovados. Lint: zero erros, 159 avisos. `git diff --check` aprovado.
- Evidências: `build/repair-backend-tests.log`, `build/repair-final-gradle.log`, `build/repair-instrumentation.log`, `build/repair-cache-final.log`; relatórios XML em `app/build/test-results/testDebugUnitTest/`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Continuação — 07/11/2026

- Corrigido o cache de arpejos: respostas com itens nulos, identidade ausente,
  publicação ausente ou notas inválidas não substituem o último conteúdo válido.
  Uma lista explicitamente vazia ou itens explicitamente inativos continuam
  removendo o conteúdo correspondente. A serialização incompleta não grava cache parcial.
- Respostas antigas de arpejos são ignoradas também entre instâncias do repositório
  e após edição/exclusão confirmada. A troca de sessão impede callbacks antigos;
  ações administrativas exigem a sessão administrativa atual.
- HTTP 401/403 no catálogo público bloqueia o cache offline até nova resposta
  pública válida. Uma recusa exclusiva da rota administrativa não bloqueia o
  catálogo público, e uma resposta administrativa não libera um bloqueio público.
- O novo teste de migration isolada reproduziu uma falha que os testes anteriores
  não detectavam: executar `schema.sql` antes da migration já criava o índice de
  e-mail, ocultando sua ausência na migration. A migration agora cria esse índice
  e inclui `managed_password`, exigida pelas rotas de gestão de usuários.
- E-mails duplicados preexistentes abortam a migration com rollback, preservando
  as contas. A nova consulta `backend/diagnostics/preflight_compat.sql` funciona
  antes da conversão dos campos legados e retorna metadados/contagens, sem dados
  de autenticação. A ordem de diagnóstico e aplicação está em `backend/README.md`.

### Arquivos desta continuação

- Android: `data/ArpeggioRepository.java`, `network/model/ArpeggioResponse.java`,
  `ArpeggioRepositoryInstrumentedTest.java`; novos `ArpeggioJsonTest.java` e
  `ArpeggioCacheRecoveryInstrumentedTest.java`.
- Backend: `schema.sql`, `migrations/2026-11-04_auth_songs_compat.sql`,
  `test/postgres.integration.test.js`, `README.md`; novo `diagnostics/preflight_compat.sql`.
- Relatórios: este arquivo e `CONNECTION_DIAGNOSIS.md`.

### Validação em 07/11

- Backend: **35 testes aprovados, sem testes ignorados**, incluindo PostgreSQL 18
  local em cluster separado. Cobertura adicional da migration sem `schema.sql`,
  cadastro/login após atualização, unicidade de e-mail e rollback com duplicatas.
- Android: **26 testes unitários e 18 instrumentados aprovados**. Os instrumentados
  cobrem cache de músicas/arpejos, recuperação de autenticação, início, gestão de
  músicas e Trivias. API simulada, AVD `RepairApi36` separado, rede desativada.
- Gradle com JDK 21: `testDebugUnitTest`, `assembleDebug`,
  `assembleDebugAndroidTest` e `lintDebug` aprovados. Lint: **0 erros, 159 avisos**.
- `git diff --check` aprovado. Emulador e PostgreSQL de teste encerrados ao final.
- Render: `GET /health` sem autenticação retornou **200** com
  `{"ok":true,"database":true}`. A primeira tentativa na rede restrita expirou;
  a consulta externa autorizada funcionou. Isso não valida esquema nem exercícios.
- TLS no Windows: conexão com validação ativa apresentou novamente emissor
  `AVG Web/Mail Shield Root`. Não foram alterados certificados, antivírus ou
  configuração TLS do app. A conexão Android ao servidor não foi repetida.
- Evidências: `build/repair-2026-11-07-backend.log`,
  `build/repair-2026-11-07-migration-regression.log` (falha reproduzida antes da correção),
  `build/repair-2026-11-07-gradle.log`, `build/repair-2026-11-07-instrumentation.log`,
  `build/repair-2026-11-07-remote-health.log`, `build/repair-2026-11-07-tls.log`.
- APK atualizado: `app/build/outputs/apk/debug/app-debug.apk`.

## Áudio local — 07/11/2026

### Chamada da biblioteca e falha reproduzida

- As telas de aulas, músicas e arpejos criam `GuitarSoundPlayer`. Em
  `MainActivity`, `bindPracticeAudio()` prepara a sequência e
  `playCurrentPracticeEvent()` toca a nota/acorde quando as amostras estão prontas
  e o botão de som está ligado.
- `GuitarSampleBank` lê `assets/guitar/manifest.json` e
  `assets/guitar/meta-steel-guitar.sfz`, selecionando o OGG por corda, casa e
  intensidade. Foram conferidos **375 OGG no projeto e no APK**, sem ausências
  nem compressão ZIP que impedisse `AssetManager.openFd()`. Não há chamada de
  áudio à API nem download de amostras.
- Amostras de até 1 MiB de PCM usam `SoundPool`; gravações maiores usam
  `MediaPlayer` diretamente do APK, preservando a duração. O cache mantém até
  24 amostras, com antecipação de três eventos e até seis cordas tocando.
- Foi reproduzida a ausência de confirmação de carregamento do `SoundPool`:
  uma amostra de Si permanecia pendente com as demais prontas. Como o player
  espera todas as notas do acorde, o treino podia ficar em carregamento sem som.
  O teste das casas 0–24 também expirava antes da correção.

### Correções e ambiente

- `GuitarSoundPlayer` agora troca para `MediaPlayer` usando **o mesmo OGG local**
  se o SoundPool recusar a amostra ou não confirmar o carregamento em cinco
  segundos. Cancela o temporizador ao concluir, remover ou liberar a amostra e
  ignora callbacks atrasados. Concluir o carregamento não inicia som por conta
  própria após uma pausa/cancelamento.
- `MainActivity` direciona os botões físicos de volume para `STREAM_MUSIC`,
  inclusive antes de iniciar o treino. O emulador estava com mídia em 5/15;
  durante a verificação foi ajustado para 10/15.
- O APK antigo do AVD `Medium_Phone_API_36.1` foi atualizado com `adb install -r`.
  O AVD foi reiniciado sem carregar snapshot, preservando seus dados. Nesta etapa
  rodaram somente os testes de áudio, que não limpam sessão, perfil ou progresso.
- Na medição sincronizada com um acorde, a sessão do emulador no Windows
  apresentou pico **0,07166 (aproximadamente −22,9 dBFS)**, volume de sessão 100%
  e mute desativado. A saída selecionada era **Alto-falantes**. Isso comprova sinal
  chegando à saída do Windows; a escuta física do usuário não foi confirmada.
  Medições anteriores fora da janela de reprodução não servem para concluir silêncio.

### Validação do áudio

- **5 testes instrumentados aprovados**: uso dos dois reprodutores e parada sem
  som atrasado; reprodução sustentada com avanço da posição; recuperação com
  callback do SoundPool deliberadamente desativado; casas 0–24 mantendo o limite
  de cache; decodificação dos ataques das seis cordas abertas. Os ataques
  medidos apresentaram picos entre −18,3 e −11,9 dBFS na intensidade padrão 80.
- Um teste de avanço com prazo de dois segundos falhou na primeira execução
  após o reinício. As duas execuções seguintes e a suíte final passaram; não foi
  alterado esse prazo para ocultar a falha inicial.
- **26 testes unitários aprovados**. `assembleDebug`, `assembleDebugAndroidTest`,
  `testDebugUnitTest` e `lintDebug` aprovados com JDK 21. Lint: **0 erros, 159 avisos**.
- Evidências: `build/repair-audio-coldboot-load-details.log` (falha original),
  `build/repair-audio-suite.log`, `build/repair-audio-suite-details.log`,
  `build/repair-audio-synchronized-test.log`,
  `build/repair-audio-synchronized-output.json` e `build/repair-audio-final-gradle.log`.
- Arquivos desta etapa: `audio/GuitarSoundPlayer.java`, `MainActivity.java`,
  `assets/audio_sources.txt`, testes `audio/GuitarSoundPlayerInstrumentedTest.java`
  e `audio/GuitarSampleAudioInstrumentedTest.java`, além deste relatório.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Navegação das Trivias — 08/11/2026

- Identificados os fluxos locais `showLevelChoice()` → `showCourse()` →
  `LessonRepository`, e o fluxo remoto `openFixedTriviaModule()` → `getModules()`
  → `showDynamicModule()`. A navegação do aluno agora usa exclusivamente os
  módulos `module_trivia_beginner` e `module_trivia_intermediate` da API REST.
- Retiradas a seleção antiga de nível, a listagem local de aulas, o atalho
  `VER AULAS`, a retomada de aulas locais e a restauração dessas telas ao recriar
  a Activity. Os métodos antigos de entrada ficaram como redirecionamentos:
  `showLevelChoice()` abre Seu progresso e `showCourse()` abre a Trivia remota
  correspondente. `LessonRepository`, seus IDs e os dados de progresso foram
  preservados para compatibilidade e sincronização existentes.
- Seu progresso passou a oferecer os dois atalhos remotos e o menu principal.
  O resumo/reset das aulas locais saiu dessa tela; o progresso remoto continua
  disponível em cada Trivia, carregado por `getModuleProgress()`. Não houve
  reset ou conversão dos registros existentes.
- Menus, troca de trilha, retornos de telas auxiliares e Voltar deixam de abrir
  o catálogo local. O treino de um item remoto continua voltando à prévia desse
  mesmo item; a prévia volta ao módulo da API. A falha/ausência de conteúdo na API
  mantém a mensagem de erro/indisponibilidade, sem substituir por aulas locais.
- Aluno inicia em Seu progresso; administrador inicia no Painel administrativo.
  Visualizar app como aluno abre Seu progresso e Voltar retorna ao painel.
  Para o aluno, Voltar na raiz encerra a Activity.
- Código de exercícios, tablaturas, áudio, conclusão remota e IDs dos módulos
  preservado. Alterações preexistentes na gestão de músicas foram mantidas.
  Nenhum arquivo de backend/migration foi alterado e nenhum registro do banco
  foi consultado ou modificado nesta etapa. Sem push.

### Arquivos e verificação

- Produção: `app/src/main/java/com/example/dedilharte/MainActivity.java`.
- Testes: `MainActivityEncodingInstrumentedTest.java`, `HomeInstrumentedTest.java`
  e novo `TriviaNavigationInstrumentedTest.java`, em
  `app/src/androidTest/java/com/example/dedilharte/`.
- Documentação: este relatório.
- **26 testes unitários aprovados**. **18 cenários instrumentados validados**:
  17 passaram na suíte inicial; o cenário de menu falhou por seleção de uma
  janela sem foco no Espresso. Após especificar `isDialog()` no teste, esse
  cenário passou isoladamente, incluindo clique nas duas Trivias.
- Cobertura: atalhos, menu sem entradas locais, troca entre módulos, progresso
  retornado pela API, prévia/treino/retorno, aluno/administrador, recriação da
  Activity, preservação de progresso antigo e ausência de fallback quando
  offline ou sem módulo. Usada API simulada em todos esses testes.
- Instrumentação executada no emulador com application ID temporário
  `com.example.dedilharte.navigationtest`, separado do app do usuário. Os dois
  pacotes temporários foram removidos ao final. O celular conectado e os dados
  do app original não foram alterados.
- Build com JDK 21: `assembleDebug`, `assembleDebugAndroidTest`,
  `testDebugUnitTest` e `lintDebug` aprovados; lint com **0 erros, 159 avisos**.
  Referências revisadas e `git diff --check` aprovado.
- Evidências: `build/trivia-navigation-instrumentation.log`,
  `build/trivia-navigation-drawer.log`, `build/trivia-navigation-final-gradle.log`
  e `build/trivia-navigation-final-package.log`.
- APK final: `app/build/outputs/apk/debug/app-debug.apk`, com application ID
  original `com.example.dedilharte`.

## Seu progresso — 08/11/2026

- Tela redesenhada em `view/ProgressDashboardView.java`: perfil horizontal,
  cartões arredondados, ícones, cores existentes e barras animadas em 600 ms.
  Mostra meta semanal, as duas Trivias e músicas aprendidas por dificuldade.
- `data/DashboardMetrics.java` calcula percentuais sobre conteúdo publicado,
  desconsidera duplicatas e progresso órfão e reutiliza a regra de nível existente
  com os resultados reais das Trivias, músicas e atividade. Abrir uma Trivia
  não altera mais o nível calculado do perfil.
- `data/DashboardRepository.java` reutiliza os cinco endpoints de módulos,
  progresso de módulos, músicas, progresso de músicas e resumo de atividade.
  Salva somente respostas completas, em cache separado por usuário; cancela
  consultas antigas e rejeita respostas de outra sessão. Offline mostra o
  último resultado válido com data; sem dados mostra indisponibilidade, nunca
  percentuais inventados. Respostas 401/403 bloqueiam a exibição do cache.
- `MainActivity.java` atualiza a tela no retorno e após gravações confirmadas.
  Músicas só aparecem como aprendidas após confirmação da API; falhas permitem
  tentar novamente. Respostas atrasadas não sobrescrevem gravações recentes
  nem o progresso de outra conta. Aluno, administrador, menu e atalhos das
  Trivias mantêm seus destinos; não há fallback para aulas locais.
- `ActivitySummaryResponse.java`, `ModuleProgressListResponse.java` e
  `DedilharteSyncManager.java` receberam os campos e callbacks necessários.
  As alterações preexistentes em `drawer_profile.xml` foram preservadas.
- `backend/src/controllers/activityController.js` corrige o identificador SQL
  reservado usado na consulta e acrescenta o histórico semanal ao endpoint
  autenticado existente, sempre filtrado pelo usuário da sessão. Reutiliza
  `learning_module_progress`, `user_song_progress` e `user_activity` pelos
  endpoints existentes. Não foram criados endpoints, dependências ou migrations.
- Testes adicionados: `DashboardMetricsTest.java`,
  `DashboardInstrumentedTest.java` e cenários em
  `backend/test/postgres.integration.test.js`. Ajustados os testes existentes
  `MainActivityEncodingInstrumentedTest.java` e
  `TriviaNavigationInstrumentedTest.java` para a nova consulta do dashboard.
- Os testes PostgreSQL usam um cluster local temporário e schemas isolados;
  os testes Android usam API simulada e o pacote separado
  `com.example.dedilharte.navigationtest` no emulador. Nenhum dado existente
  do Aiven, do aplicativo original ou do celular conectado foi alterado.
- Validação: **32 testes unitários Android**, **25 testes instrumentados** e
  **36 testes do backend aprovados**, sem falhas ou testes ignorados. Cobrem
  percentuais, conteúdo vazio, erros, offline, 403, troca de usuário, respostas
  atrasadas, conclusão de aula, gravação de música, recriação, persistência no
  PostgreSQL e navegação. A inspeção visual também identificou e corrigiu o
  espaçamento do cabeçalho sob a barra de status.
- Após o ajuste visual, os **7 testes do dashboard passaram novamente**.
  Build final com JDK 21: `assembleDebug`, `assembleDebugAndroidTest`,
  `testDebugUnitTest` e `lintDebug` aprovados; lint com **0 erros e 166 avisos**.
  `git diff --check` aprovado. APK: `app/build/outputs/apk/debug/app-debug.apk`,
  com o ID original `com.example.dedilharte`. Pacotes temporários removidos do
  emulador e cluster PostgreSQL de teste encerrado ao final.
- Evidências: `build/dashboard-backend-tests.log`,
  `build/dashboard-instrumentation-final.log`,
  `build/dashboard-insets-instrumentation.log`,
  `build/dashboard-final-gradle.log` e `build/dashboard-preview.png`.
  A imagem contém exclusivamente os dados simulados do teste.
- Pendência de implantação: publicar a alteração do backend e validar a tela
  com a API implantada. O APK continua usando a URL já configurada. Não houve
  acesso autenticado à produção, commit ou push nesta etapa.

## Recuperação dos dados de Seu progresso — 08/11/2026

- Diagnóstico confirmado no Android: o repositório exigia sucesso nas cinco
  consultas para exibir qualquer indicador. Uma resposta HTTP/JSON inválida
  descartava as respostas válidas dos outros endpoints. Além disso,
  `invalidate()` apagava o último snapshot após uma gravação, deixando a tela
  vazia quando a consulta seguinte falhava. O nível era exibido como traço
  quando não havia snapshot, apesar de existir um valor local.
- Evidência do ambiente implantado: logs existentes do emulador mostram HTTP
  **500 em `/api/module-progress` e `/api/songs/progress`**, enquanto
  `/api/modules` e `/api/songs` retornam 200. Evidência filtrada, sem tokens ou
  corpos de resposta: `build/dashboard-production-http-evidence.log`.
  Esses registros são anteriores à correção e não identificam o erro SQL
  interno do Render/Aiven; não se pode afirmar que uma tabela/coluna específica
  está ausente sem o log do servidor ou consulta autorizada ao esquema.
- `DashboardRepository.java` agora atualiza e persiste os grupos Trivias,
  músicas e atividade independentemente. Mantém o último valor confirmado
  quando um grupo falha, inclusive após `invalidate()`, e permite recuperar
  cada grupo na próxima tentativa. Isolamento por usuário/sessão e bloqueio
  do cache em 401/403 continuam ativos.
- `DashboardMetrics.java` distingue grupos confirmados de grupos ainda não
  sincronizados, preservando compatibilidade com snapshots completos antigos.
  `ProgressDashboardView.java` mostra zeros iniciais acompanhados de indicação
  de sincronização pendente; respostas vazias válidas mostram 0% e 0 concluídas.
  `MainActivity.java` fornece nível/meta locais e só persiste um novo nível
  calculado quando os três grupos têm dados confirmados.
- Diagnóstico temporário em builds depuráveis, tag `DashboardRepository`:
  registra caminho da consulta, status HTTP, classe da falha de transporte e
  motivo de validação JSON. Não registra JWT, identificadores de usuário,
  cabeçalhos ou corpo das respostas. As mensagens da tela identificam o grupo
  afetado; carregamento termina mesmo quando uma consulta falha.
- Endpoints existentes preservados: GET `/api/activity/summary`, `/api/modules`,
  `/api/module-progress`, `/api/songs` e `/api/songs/progress`. Não há endpoint
  agregado. Rotas conferidas no Retrofit e em `routes/users.js`, montado sob
  `/api` e protegido por `authenticateToken`; JSON usa `items` nas listas,
  `item_id`/`user_id` no progresso, e campos camelCase no resumo de atividade.
- Testes: `DashboardInstrumentedTest.java` acrescenta falha HTTP 500 em cada
  endpoint, recuperação, atualização parcial sem apagar outro grupo, cache
  após invalidação e zeros/nível local no primeiro acesso offline. A suíte
  Android passou **28 testes instrumentados** e **32 testes unitários**.
  `backend/test/postgres.integration.test.js` agora também exercita as cinco
  rotas HTTP reais com JWT, verifica 401 sem token, 200 autenticado e os campos
  JSON usando PostgreSQL local isolado. **36 testes backend aprovados**, sem
  falhas ou testes ignorados.
- Na versão final, os **10 testes do dashboard passaram novamente**.
  `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` e `lintDebug`
  aprovados; lint com **0 erros e 166 avisos**. APK final em
  `app/build/outputs/apk/debug/app-debug.apk`, ID `com.example.dedilharte`.
  Testes no pacote temporário separado, removido ao final; cluster local
  encerrado. O aplicativo instalado do usuário não foi substituído.
- Evidências: `build/dashboard-recovery-instrumentation.log`,
  `build/dashboard-recovery-final-instrumentation.log`,
  `build/dashboard-recovery-backend.log` e
  `build/dashboard-recovery-final-gradle.log`.
- Pendência: obter o erro interno do Render para corrigir os HTTP 500 do
  ambiente implantado e publicar as correções necessárias. A consulta semanal
  corrigida na etapa anterior também depende de publicação. Os testes locais
  não demonstram que o servidor implantado já está corrigido. Solicitada a
  mensagem de erro do Render, sem segredos; nenhum acesso a credenciais ou
  dados de produção, implantação, commit ou push foi realizado nesta etapa.

## Padronização de ícones — 08/11/2026

- Adicionados 30 VectorDrawable/XML locais de Material Symbols Outlined do
  repositório oficial Google: `app/src/main/res/drawable/ic_ms_*.xml`.
  Origem, adaptações e licença Apache-2.0 em
  `assets/material_symbols_sources.txt` e `material_symbols_LICENSE.txt`.
  Os vetores somam aproximadamente 23 KB, sem fontes ou novas dependências.
- Novo `view/MaterialIcons.java` centraliza tamanho de 24dp, tintas, ícones
  junto ao texto e botões acessíveis somente com imagem. Sobre turquesa claro,
  a imagem branca usa base verde-escura e desenho de 20dp para contraste de
  pelo menos 3:1. Fundos claros recebem ícones verde-escuros; fundos escuros
  recebem brancos. Ícones direcionais respeitam RTL.
- `MainActivity.java` e `ProgressDashboardView.java`: símbolos decorativos
  substituídos no cabeçalho, retorno, dashboard, controles de música, conclusão,
  busca, foto padrão, configurações, administração e avisos de confirmação.
  Play/pausa permanecem vinculados ao estado da reprodução. Não existe um
  serviço de notificações do sistema no aplicativo atual; os avisos existentes
  foram tratados sem introduzir uma funcionalidade nova.
- `drawer_profile.xml`, `dialog_edit_profile.xml` e `values/styles.xml`:
  ícones locais no menu, fechar e ajustar meta, espaçamentos consistentes e
  toque de 48dp no botão de fechar. Logotipo, fotos reais, textos funcionais,
  descrições de acessibilidade e dados musicais foram preservados.
- Testes ajustados: `DashboardInstrumentedTest.java` e
  `GuitarPracticeAudioInstrumentedTest.java`. Novo
  `MaterialIconsInstrumentedTest.java` verifica tamanho, desenho monocromático,
  contraste, centralização, descrições e temas claro/escuro. A paleta fixa
  existente do dashboard foi mantida nos dois temas, sem redesenhar o tema.
- Validação: **31 testes instrumentados aprovados**, incluindo menu, dashboard,
  navegação, aluno/admin e reprodução/pausa/silêncio offline nos três modos de
  treino. Após a correção de centralização, **3 testes específicos passaram
  novamente**, cobrindo os dois temas, contraste e áudio. **32 testes unitários
  aprovados**. Busca por emojis/símbolos decorativos no Java/XML concluída;
  código de cifras, tablaturas e exercícios não foi alterado.
- Evidências: `build/material-icons-instrumentation.log`,
  `build/material-icons-final-instrumentation.log`,
  `build/material-icons-final-gradle.log`, `build/material-icons-light.png` e
  `build/material-icons-dark.png`. Capturas usam exclusivamente dados simulados.
- Nenhum arquivo de backend, banco de dados ou logotipo foi alterado.
  Sem commit/push. Testes executados somente no pacote isolado do emulador.
- Build final aprovado com JDK 21: `assembleDebug`, `assembleDebugAndroidTest`,
  `testDebugUnitTest` e `lintDebug`; lint com **0 erros e 173 avisos**.
  `git diff --check` aprovado. Pacotes de teste removidos. APK com o ID original
  em `app/build/outputs/apk/debug/app-debug.apk`; app do usuário não substituído.

## Pendências atualizadas — 07/11/2026

- Conferir os seis exercícios reais e o esquema implantado. Continua sem `DATABASE_URL` configurada. O bloqueio anterior da revisão automática ao uso do JWT salvo foi respeitado: foi solicitada autorização específica para consultas de leitura, ainda sem resposta nesta continuação. Nenhum token do usuário foi acessado e as consultas autenticadas não foram executadas.
- Aplicar a migration revisada e publicar o backend somente com autorização. O APK continua apontando para o Render; alterações locais não corrigem automaticamente o servidor implantado.
- Resolver a interceptação HTTPS pelo AVG no ambiente de desenvolvimento. O emissor AVG foi reconfirmado em 07/11 no Windows; a validação TLS do app permanece ativa.
- `C:/Users/VAIO/.continue/config.yaml` pertence à ferramenta de desenvolvimento e não configura Retrofit/PostgreSQL; não foi modificado.

## Cadastro, perfil, metas e senhas — 08–09/11/2026

- Cadastro em duas etapas: dados de acesso e personalização. Botão ciano
  `#54D6DD` com texto verde-escuro; nível declarado, meta de 1–7 dias,
  meta positiva de músicas e preferência de notificações enviados à API.
- Perfil salvo somente após confirmação da API. Falhas mantêm o formulário e
  os últimos dados válidos. Abrir o app não envia mais um perfil local sobre
  alterações de outro dispositivo. Revisões remotas usam horário do servidor;
  chamadas de edição e senha mantêm a autorização da sessão de origem.
- Dashboard: músicas aprendidas/meta pessoal, percentual real limitado a 100%
  e animação de 600 ms. Trivias e seus IDs permanecem independentes; nível
  calculado e nível inicial declarado aparecem separadamente.
- Recuperação com e-mail por Resend, link HTTPS, token aleatório de 256 bits,
  somente hash SHA-256 persistido, expiração de 30 minutos e uso único em
  transação. Resposta pública idêntica para e-mails existentes e ausentes.
  Troca exige senha atual, bcrypt custo 12 e invalida sessões/links anteriores.
- Migration aditiva, repetível, sem remoção de usuários, aulas ou progresso.
  Rate limiting persistido no PostgreSQL. Nenhuma migration de produção executada.
- Insets comuns nas Activities e nos formulários/modais, incluindo teclado;
  perfil rolável, ícones quadrados de 20–24dp e contraste do nível ajustado.
  Logo e conteúdo musical preservados. Notificações: preferência persistida e
  solicitação explícita de permissão no Android 13+.

Arquivos desta etapa (além dos ícones já descritos na etapa anterior):

- Android: `app/src/main/AndroidManifest.xml`;
  `java/com/example/dedilharte/{LoginActivity,RegisterActivity,MainActivity}.java`;
  `auth/{SessionManager,NotificationPermissions}.java`;
  `data/DashboardRepository.java`;
  `network/{DedilharteApiClient,DedilharteApiService}.java`;
  `network/model/{AuthenticatedUser,RegisterRequest,UserRequest,UserResponse,PasswordRequest}.java`;
  `view/{ProgressDashboardView,PreferencesFields,PasswordDialogs,WindowInsetsHelper,MaterialIcons}.java`;
  `res/layout/{activity_login,activity_register,dialog_edit_profile}.xml`;
  `res/values/profile_ids.xml`. Os caminhos Java/res são relativos a `app/src/main/`.
- Backend: `backend/.env.example`, `backend/PROFILE_PASSWORDS.md`,
  `backend/migrations/2026-11-08_profile_passwords.sql`,
  `backend/src/controllers/{authController,usersController,passwordController}.js`,
  `backend/src/routes/{auth,users}.js`, `backend/src/server.js`,
  `backend/src/profilePreferences.js`, `backend/src/resetPasswordPage.js`,
  `backend/src/middleware/rateLimit.js`, `backend/src/services/resetEmail.js`.
- Testes: `backend/test/{postgres.integration,profilePasswords.integration}.test.js`;
  `app/src/androidTest/java/com/example/dedilharte/{AuthenticationFlowInstrumentedTest,ExampleInstrumentedTest,DashboardInstrumentedTest,ProfilePasswordsInstrumentedTest}.java`;
  `app/src/androidTest/java/com/example/dedilharte/audio/GuitarSoundPlayerInstrumentedTest.java`.
  O teste de áudio passou a aguardar avanço real do MediaPlayer, com prazo máximo,
  em vez de supor que a inicialização assíncrona terminaria em dois segundos.
- Registro: `REPAIR_VALIDATION.md`.

Validação desta etapa:

- **43 testes de backend aprovados**, sem pulos, em PostgreSQL 18 local:
  cadastro/preferências, atualização, persistência, isolamento, migração
  repetida, recuperação genérica, envio simulado, hash/expiração, concorrência,
  reutilização, troca de senha, revogação de sessões e rate limiting.
  Evidência: `build/profile-backend-final.log`.
- **32 testes unitários Android aprovados**. A suíte instrumentada ampliada
  executou 71 testes inicialmente, com 6 falhas. Foram corrigidas expectativas
  de cadastro local/pacote fixo e esperas dos testes de UI/áudio; todos os casos
  falhos foram revalidados nas rodadas seguintes. O teste de cadastro antigo
  foi substituído pela verificação de login e entrada por papel.
- A rodada final de **10 testes de perfil/cadastro/senhas/insets/permissões e
  navegação passou integralmente**, incluindo rotação, retorno entre etapas,
  falha de rede sem sobrescrever estado, revisão remota independente do relógio,
  metas de músicas e separação de níveis. Ícones nos dois temas e reprodução
  sustentada também passaram nas reexecuções específicas.
- Durante a retomada, o emulador apresentou falha do `system_server` e aviso de
  System UI sem resposta. Foi reiniciado sem apagar seus dados; os testes de UI
  afetados foram repetidos e passaram. As configurações temporárias de teclado
  foram restauradas e os dois pacotes isolados removidos.
- Evidências: `build/profile-all-instrumented.log`,
  `build/profile-final-retest.log`, `build/profile-final-validation-instrumented.log`
  e `build/profile-ui-final.log`. As rodadas intermediárias registram as falhas
  e suas revalidações; não equivalem a uma única execução integral sem falhas.
- Capturas de dados simulados: `build/profile-dashboard-light.png`,
  `build/profile-dashboard-dark.png`, `build/profile-register-keyboard-final.png`.
- Banco de testes local encerrado. Nenhum dado de produção acessado ou alterado.
- Build final aprovado: `assembleDebug`, `assembleDebugAndroidTest`,
  `testDebugUnitTest` e `lintDebug`; **0 erros e 179 avisos de lint**.
  `git diff --check` aprovado. Evidência: `build/profile-final-gradle.log`.
  APK em `app/build/outputs/apk/debug/app-debug.apk`, com applicationId original
  `com.example.dedilharte`; não instalado sobre o aplicativo pessoal.

Configuração e homologação pendentes:

- Aplicar migration revisada e publicar backend por procedimento autorizado,
  antes de distribuir o APK. A API atualmente implantada não recebe as mudanças
  por este trabalho local.
- Configurar `RESEND_API_KEY`, `MAIL_FROM` de domínio verificado,
  `PASSWORD_RESET_BASE_URL` HTTPS apontando para `/api/auth/reset-password`,
  `TRUST_PROXY_HOPS` conforme topologia, além de `DATABASE_URL` e `JWT_SECRET`.
  Usar Node.js 20+. Instruções e contratos em `backend/PROFILE_PASSWORDS.md`.
- Homologar entrega real de e-mail e fluxo com o Render/Aiven atualizado.
  Os testes de envio usam provedor simulado, sem e-mails reais.
- Nenhum commit, push ou alteração no aplicativo pessoal instalado.
