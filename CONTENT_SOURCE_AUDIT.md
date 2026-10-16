# Auditoria das fontes de conteúdo — Android, API e cache

## Evidência obtida nesta investigação

Antes de editar, foram inspecionados MainActivity, os três repositories,
LessonRepository, modelos de rede, cliente/interface Retrofit, recursos/assets,
rotas/controllers do backend, migrations, configuração de banco e APK instalado.

- BASE_URL única: `https://tcc-dedilharte.onrender.com/`, em
  `app/src/main/java/com/example/dedilharte/network/ApiConfig.java`.
- DedilharteApiClient usa essa constante no Retrofit com Gson e OkHttp.
  Não há flavors/dev/prod alternativos nem cache HTTP configurado no cliente.
- Chamadas reais com curl para `/api/songs` e `/api/modules` retornaram HTTP 401
  e `Token de autenticacao ausente.`. Não se contornou autenticação.
- No aparelho conectado, com.example.dedilharte estava instalado e atualizado;
  `shared_prefs/dedilharte_songs.xml` continha `songs=[]`. Não havia cache público
  nem sessão autenticada naquele instante. Não havia diretório databases.
- Portanto não há evidência de músicas locais armazenadas nesse aparelho nesse
  instante. Não é possível atribuir títulos atualmente vistos pelo usuário a uma
  resposta remota específica sem identificar a tela/título e obter sessão válida.
- O backend usa `process.env.DATABASE_URL`, em `backend/src/db.js`. A configuração
  secreta do serviço Render não está disponível no workspace. Não se confirmou
  que o Render usa exatamente o banco dedilharte consultado no Aiven, nem se
  o deploy remoto contém as alterações locais.

## Mapa verificável

| Tela | Entrada Android | Endpoint | Resposta | Origem no backend |
|---|---|---|---|---|
| Trivia/início/troca de nível | MainActivity.showCourse → openFixedTriviaModule → getModules | GET /api/modules | LearningModuleListResponse, LearningModuleResponse, LearningModuleItemResponse, MultipleChoiceResponse | learning_modules + learning_module_items + learning_multiple_choice |
| Administração Trivia | MainActivity.showAdminModules → getStaffModules | GET /api/staff/modules | mesmos modelos, com correctIndex para equipe | mesmas tabelas |
| Repertório | MainActivity.showSongs → SongRepository.fetchPublishedQueued → getSongs | GET /api/songs | SongListResponse / SongResponse | songs, status publicada e in_queue TRUE |
| Administração músicas | MainActivity.showAdminSongs → SongRepository.fetchAdmin | GET /api/admin/songs | SongListResponse / SongResponse | songs |
| Arpejos | MainActivity.showArpeggios → ArpeggioRepository.fetchPublic | GET /api/arpeggios | ArpeggioListResponse / ArpeggioResponse | arpeggios |
| Administração arpejos | MainActivity.showAdminArpeggios → ArpeggioRepository.fetchAdmin | GET /api/admin/arpeggios | ArpeggioListResponse / ArpeggioResponse | arpeggios |

A interface completa é network/DedilharteApiService.java. O servidor monta as
rotas de backend/src/routes/users.js sob /api e exige autenticação.

Trivia não chama o endpoint genérico de músicas. Seleciona exclusivamente o ID
module_trivia_beginner ou module_trivia_intermediate; o backend agrupa os itens
pelo module_id. Um item song é identificado por song_id e mostra o título e as
instruções cadastrados no módulo. O código atual não busca automaticamente um
repertório global nem resolve a tablatura de songs para esses cartões.

## Fontes independentes encontradas e corrigidas

1. **Aulas fixas:** LessonRepository.createLessons contém 14 aulas, incluindo
   Conhecendo as cordas e Primeiros arpejos. O fluxo antigo do menu e do início
   chamava showCourse e as renderizava, enquanto a administração lia módulos do
   servidor. Nas correções anteriores desta sessão, showCourse passou a abrir a
   API. O catálogo legado permanece para histórico/métricas; não abastece Trivia.
2. **Músicas locais:** SongRepository usava SharedPreferences; public cache vazio
   caía em readAll, e falhas retornavam o conteúdo antigo. A tela renderizava
   cache antes da rede. Esses caminhos foram removidos na correção anterior.
   Nesta investigação também se removeu o fallback administrativo. A criação,
   alteração e exclusão não escrevem mais resultados otimistas locais nem ignoram
   erro da API: a interface aguarda confirmação e recarrega o servidor.
3. **Arpejos fixos ainda ativos:** havia sete acordes Dó, Ré, Mi, Fá, Sol, Lá e Si
   em MainActivity.ARPEGGIOS/fallbackArpeggios e outra cópia em
   ArpeggioRepository.fallback. Tanto vazio como erro recuperavam esses dados,
   até com instalação limpa. Esses arrays/fallbacks foram removidos.
4. **Seed em GET:** arpeggiosController.ensureDefaultArpeggiosWhenEmpty inseria ou
   reativava os sete arpejos quando não havia ativos. Removido dos GETs, inclusive
   a lista fixa. Nenhum dado existente foi excluído e nenhuma migration foi feita.

As tabelas informadas vazias não incluem arpeggios. Portanto um arpejo remoto
existente nessa tabela é independente de songs/learning_module_items e continua
válido na biblioteca Arpejos, mas não vira item de Trivia.

Não foram encontrados JSONs de catálogo em assets/res/raw, Room, SQLite,
ViewModels/adapters fornecendo listas fixas ou uma API externa de músicas.
res/raw contém WAVs de notas isoladas do instrumento; assets/audio_sources.txt
contém créditos, não conteúdo curricular. URLs desses créditos não são APIs.

## Persistência

- `/data/user/0/com.example.dedilharte/shared_prefs/dedilharte_songs.xml`:
  `songs` é snapshot administrativo, atualizado por GET administrativo válido;
  `published_cache` é atualizado pela resposta pública e invalidado antes de
  consulta ou após mutação confirmada. O repertório não o renderiza antes da API.
  Vazio substitui cache; erro público devolve vazio + sinal de falha. `seedIfEmpty`
  apenas inicializa `[]`; não cria músicas. Existe limpeza antiga de seed_asa_branca.
- `dedilharte_arpeggios.xml`, chave `cache`: invalidado antes de consulta pública
  e atualizado com a resposta, inclusive vazia. Não é fonte de exibição nem fallback.
- Preferências de sessão/perfil/progresso/tema existem, mas não fornecem itens de
  Trivia. Não há cache persistente de módulos.
- Limpar os dados Android remove essas preferências, mas não é necessário para
  a correção funcionar. Não foi executado pm clear no aparelho do usuário.

## Limites da prova

As regressões usam handlers reais com adaptador de banco simulado, e Activity/
repositories reais com respostas Retrofit simuladas. Elas comprovam isolamento,
resposta vazia, remoção após reload e ausência de fallback/cache antigo. Não
comprovam a configuração secreta DATABASE_URL do Render nem o estado do Aiven.
Para fechar essa parte, comparar a conexão do serviço Render com o Aiven e repetir
os cenários após autenticar no APK atualizado. Não é necessário fornecer senhas
ou tokens em mensagens; o login pode ser feito normalmente no aparelho.

## Arquivos alterados neste complemento

- MainActivity.java: remove biblioteca fixa e renderização inicial por cache de
  arpejos/admin músicas; estados de carregamento/erro/vazio; mutações de música
  aguardam resposta do servidor.
- data/ArpeggioRepository.java: remove sete defaults e fallback de cache.
- data/SongRepository.java: remove fallback administrativo e gravações otimistas.
- backend/src/controllers/arpeggiosController.js: GET não popula/reativa dados.
- backend/test/arpeggiosController.test.js e novo songsController.test.js:
  listas vazias sem inserts/updates.
- SongRepositoryInstrumentedTest.java e novo ArpeggioRepositoryInstrumentedTest.java:
  falhas, cache antigo, cache ausente, listas vazias e mutações rejeitadas.
- CONTENT_SOURCE_AUDIT.md e atualização de TRIVIA_VALIDATION.md.

As alterações anteriores da sessão também abrangem modulesController.js,
modulesController.test.js, drawer_profile.xml, MainActivityEncodingInstrumentedTest
 e TriviaContentInstrumentedTest. Nenhuma migration/seed nova; dados remotos não
foram modificados. Alterações pré-existentes de .idea foram preservadas.

## Resultados

- npm test no backend: 23/23 aprovados.
- testDebugUnitTest: 15/15 aprovados.
- assembleDebug e assembleDebugAndroidTest: BUILD SUCCESSFUL com Java 21.
- lintDebug: aprovado, com avisos não bloqueantes.
- AndroidJUnitRunner via adb no aparelho conectado: OK (4 tests), cobrindo
  Trivia pela tela inicial, músicas e arpejos. APK atualizado instalado.
- git diff --check: aprovado.
- Banco/API simulados nas regressões; nenhum dado fake criado no Aiven.
- Cenário de armazenamento ausente foi exercitado no repository de arpejos;
  não se apagaram todos os dados do app. O cenário 5 completo com login e Aiven,
  bem como cenários 1–4 em produção, ainda requer validação no ambiente remoto.
