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

## Pendências atualizadas — 07/11/2026

- Conferir os seis exercícios reais e o esquema implantado. Continua sem `DATABASE_URL` configurada. O bloqueio anterior da revisão automática ao uso do JWT salvo foi respeitado: foi solicitada autorização específica para consultas de leitura, ainda sem resposta nesta continuação. Nenhum token do usuário foi acessado e as consultas autenticadas não foram executadas.
- Aplicar a migration revisada e publicar o backend somente com autorização. O APK continua apontando para o Render; alterações locais não corrigem automaticamente o servidor implantado.
- Resolver a interceptação HTTPS pelo AVG no ambiente de desenvolvimento. O emissor AVG foi reconfirmado em 07/11 no Windows; a validação TLS do app permanece ativa.
- `C:/Users/VAIO/.continue/config.yaml` pertence à ferramenta de desenvolvimento e não configura Retrofit/PostgreSQL; não foi modificado.
