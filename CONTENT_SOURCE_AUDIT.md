# Auditoria atual — repertório e trivias (2026-11-02)

Este registro substitui o diagnóstico anterior deste arquivo. Descreve os arquivos
atuais e as verificações desta execução; não comprova o estado do banco remoto.

## Causa e fontes verificadas

- `songsController.js` ainda consultava `recommended_bpm`, `queue_order`,
  `in_queue`, `status` e timestamps que não fazem parte do esquema informado.
  `modulesController.js` repetia essas referências no JOIN de músicas das trivias.
- `MainActivity.showSongs` já aguardava `GET /api/songs`, sem fallback local.
  `SongRepository` ainda lia snapshots antigos de SharedPreferences (`songs`,
  `published_cache`) em seus acessores. Agora descarta ambos ao iniciar e somente
  armazena respostas confirmadas da API; vazio e erro não recuperam dados locais.
- Não há catálogo de músicas/perguntas em JSON nos assets. O JSON encontrado é o
  manifesto da biblioteca de sons, que não fornece itens do repertório/trivia.
- Trivia: menu e troca de nível dentro da trivia chamam `openFixedTriviaModule`,
  depois `GET /api/modules`. A fonte é `learning_modules` +
  `learning_module_items` + `learning_multiple_choice`. Os GETs não inserem seeds.
  A migration histórica de fase 1 insere apenas os dois módulos iniciais.
- Perguntas de múltipla escolha não exigem `song_id`. Itens do tipo música usam
  `song_id` e recebem os dados de músicas ativas por LEFT JOIN. A ausência de uma
  música não apaga a pergunta, o item educacional ou seu progresso.
- `LessonRepository` contém aulas locais legítimas, acessadas por `showCourse`.
  Esse fluxo é separado das trivias remotas e foi preservado.

## Correção implementada

- SQL de músicas usa somente `id`, `title`, `artist`, `difficulty`, `bpm`,
  `tablature`, `content`, `active`, `sort_order`, `created_by`.
- Contrato REST: `bpm`, `active`, `sortOrder`; listagem pública filtra
  `active = TRUE`, ordenada por `sort_order, id`. Android também filtra inativas.
- Admin cadastra, edita, arquiva, restaura e exclui. Cada sucesso recarrega a API.
  Arquivar/restaurar usa `active`; exclusão exige confirmação na Activity.
  Campos omitidos no PUT são preservados, inclusive `content` e `created_by`.
  A autoria da criação vem do usuário autenticado, não do corpo da requisição.
- Os endpoints existentes já tinham JWT e `requireStaff`, que aceita apenas
  `admin` e consulta o perfil atual em `users`; essa proteção foi reutilizada e
  testada por HTTP, inclusive com papel divergente no token.
- Não existe estado separado de rascunho/fila no banco informado. Os controles
  existentes passam a representar ativa/arquivada; SUBIR/DESCER usa `sort_order`.
  Foram mantidos estrutura dos cards, dimensões, cores, pesquisa e modo de treino.
  Rótulos administrativos foram ajustados para mostrar ARQUIVADAS/RESTAURAR.
- Nenhum layout XML, OGG, GuitarSampleBank ou GuitarSoundPlayer foi alterado nesta
  execução. Nenhuma migration foi aplicada, nenhum registro remoto foi criado.

## Arquivos modificados nesta execução

- `backend/src/controllers/songsController.js`
- `backend/src/controllers/modulesController.js`
- `backend/test/songsController.test.js`
- `backend/test/modulesController.test.js`
- `app/src/main/java/com/example/dedilharte/MainActivity.java`
- `app/src/main/java/com/example/dedilharte/data/SongRepository.java`
- `app/src/main/java/com/example/dedilharte/model/Song.java`
- `app/src/main/java/com/example/dedilharte/network/model/SongRequest.java`
- `app/src/main/java/com/example/dedilharte/network/model/SongResponse.java`
- `app/src/androidTest/java/com/example/dedilharte/SongRepositoryInstrumentedTest.java`
- `app/src/androidTest/java/com/example/dedilharte/ModuleLessonNavigationInstrumentedTest.java`
- `app/src/androidTest/java/com/example/dedilharte/SongManagementInstrumentedTest.java` (novo)
- `CONTENT_SOURCE_AUDIT.md`

Havia alterações anteriores no workspace, inclusive na biblioteca de áudio e nas
aulas. Foram preservadas; a lista acima identifica apenas arquivos tocados aqui.

## Validação e limites

- `npm test --prefix backend`: 26/26 aprovados. HTTP Express/JWT reais, adaptador
  PostgreSQL simulado. Inclui CRUD, filtro active, vazio, preservação de content,
  autoria, validação, permissões e perguntas sem vínculo com música.
- `testDebugUnitTest`: 18/18 aprovados.
- `assembleDebug`, `assembleDebugAndroidTest` e `lintDebug`: aprovados no build
  final com Java 21. Lint sem erros e com 162 avisos.
- Quatro regressões instrumentadas aprovadas no emulador API 36.1: repositório
  sem fallback, recarga de trivia, navegação da música da trivia até o treino,
  e CRUD Admin com pesquisa, arquivar/restaurar, cancelamento e confirmação de
  exclusão. A última passou na execução isolada (`OK (1 test)`). Os dados foram
  simulados na API, sem comunicação com produção.
- A suíte instrumentada completa não passou: `AuthenticationFlowInstrumentedTest`
  abre MainActivity sem sessão e tenta acessar uma Activity já encerrada pelo
  redirecionamento para LoginActivity. O teste pressupõe o login antigo dentro
  da MainActivity; não foi alterado por estar fora desta correção.
- Houve também ANR do System UI do emulador (capturado em screenshot), erro de
  foco no diálogo do teste Admin e falha gRPC/UTP na coleta. O teste longo de áudio
  foi interrompido ao reiniciar o emulador. A coleta das regressões foi feita
  diretamente via AndroidJUnitRunner. O teste Admin passou a inspecionar o diálogo
  real e acionar seus botões pela hierarquia da janela, sem depender do foco
  roubado pelo System UI. Não foi alterado código de produção para contornar isso.
- Logs: `app/build/song-trivia-instrumentation.log` (três regressões aprovadas e
  a falha inicial de foco) e `app/build/song-admin-instrumentation.log` (Admin
  aprovado após o ajuste do teste). A suíte completa não é declarada aprovada.
- `git diff --check`: aprovado.
- Não há `DATABASE_URL` nem `backend/.env` configurados neste ambiente. O Android
  aponta para `https://tcc-dedilharte.onrender.com/`. Não foi confirmado o esquema
  por conexão Aiven nem publicado o backend: é necessário atualizar backend e APK
  juntos e validar com sessão real no ambiente remoto.
- `backend/schema.sql` e a migration histórica `2026-10-05_songs_content.sql`
  descrevem o modelo antigo. Não foram executados nem alterados: a correção adapta
  as consultas ao esquema fornecido, sem inventar tipos/defaults do banco remoto.

## Continuação: tela inicial do aluno

- A entrada do aluno agora abre `showHome`, com retomada da última lição acessada
  e até três sugestões de músicas. Administrador/configurador mantêm seus painéis.
- `ProgressStore` guarda IDs de módulo e lição por usuário, neste aparelho,
  inclusive após fechar o app. Apenas abrir uma lição muda a retomada; navegar
  para outra trivia ou praticar uma música sugerida não a substitui. Não há
  sincronização desse marcador entre aparelhos nem migração de histórico antigo.
- A lição remota é consultada novamente pela API. Itens/módulos removidos ou
  inativos mostram indisponibilidade; falha de conexão mostra erro, sem fallback.
  Aulas locais legítimas também podem ser retomadas pelo ID.
- Sugestões usam somente músicas ativas retornadas pela API, excluem aprendidas
  e priorizam proximidade de dificuldade e artistas de músicas já aprendidas.
  Catálogo vazio continua vazio. O card abre o treino e volta para o início.
- Novo acesso `Início` no menu. Aulas, trivias, repertório e estilos existentes
  foram reutilizados. Nenhum backend, schema ou arquivo de áudio foi modificado
  nesta continuação.
- Arquivos desta continuação: `MainActivity.java`, `data/ProgressStore.java`,
  `data/SongRepository.java`, `res/layout/drawer_profile.xml`,
  `MainActivityEncodingInstrumentedTest.java`, novos `HomeInstrumentedTest.java`
  e `SongRecommendationsTest.java`, além deste relatório.
- Validação: 26 testes backend e 21 unitários Android aprovados; APKs debug e de
  testes gerados; lint aprovado (163 avisos, zero erros). Quatro testes
  instrumentados aprovados via AndroidJUnitRunner no emulador API 36.1, com API
  simulada, incluindo retomada entre trivias/reabertura, isolamento por aluno,
  música sugerida/retorno, remoção/inativação, rede indisponível e aulas/treino.
  Log: `app/build/home-instrumentation.log` (`OK (4 tests)`).
