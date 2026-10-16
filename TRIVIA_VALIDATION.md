# Trivia: fonte de conteúdo e validação

## Diagnóstico

O menu Android (`drawerTriviaBeginner`/`drawerTriviaIntermediate`) chamava
`chooseLevel()` → `showCourse()` → `LessonRepository.getLessons()`. Portanto,
mostrava aulas Java fixas (por exemplo, `ini_1`, Conhecendo as cordas), sem consultar
os itens da Trivia. A tela de módulos remotos já existia, mas esses botões não a abriam.
A administração usa `GET /api/staff/modules`, via `showAdminModules()`.

A busca no Android encontrou o catálogo de aulas em `data/LessonRepository.java`,
catálogos/cache de músicas e arpejos nos respectivos repositories e preferências
para sessão/progresso. Não encontrou Room/SQLite ou JSON de perguntas em assets,
res/raw ou strings.xml. Os arquivos raw são amostras WAV de notas de violão.
O catálogo legado permanece no código para compatibilidade de histórico, mas a
tela inicial e a troca de nível agora abrem os módulos remotos; não o carregam.
Nenhuma aula foi copiada para PostgreSQL.

Referências de módulos/tabelas no backend: `modulesController.js`,
`moduleProgressController.js`, testes de módulos e migrations
`2026-09-28_phase1_functional.sql` e `2026-10-01_fix_trivia_labels.sql`.
As rotas estão em `src/routes/users.js`; os contratos Android, em
`network/DedilharteApiService.java` e `network/model/LearningModule*`.

## Fonte oficial e comportamento

- O menu abre `GET /api/modules` e seleciona o ID exato do módulo.
- Cada abertura ou clique em Atualizar consulta novamente a API, sem cache de conteúdo.
- Lista vazia mostra estado vazio; erro mostra erro e opção de tentar novamente.
- Respostas de uma tela já abandonada não substituem a tela atual.
- Músicas e perguntas são renderizadas pelos itens retornados, sem filtro local por tipo.
- Administração e aluno leem `learning_modules` + `learning_module_items`, com
  LEFT JOIN em `learning_multiple_choice`. Não existe endpoint separado de GET
  dos itens: eles são serializados no array `items` de cada módulo.
- O aluno recebe módulos/itens ativos; a administração também recebe inativos e
  o índice correto da resposta. A correção de respostas continua no servidor.
- GETs não recriam, reativam nem sobrescrevem módulos. A migration existente já
  contém a criação inicial dos dois módulos. Nenhuma migration/seed foi alterada.
- O filtro existente para os dois IDs fixos foi preservado. `trivia_teste_001`
  não passa a ser exibido por esta alteração.
- Rotas de criação, edição, exclusão, autenticação e permissões foram preservadas.

## Schema

Foi inspecionado o schema versionado, não o banco remoto (DATABASE_URL não está
configurada neste workspace). A confirmação dos registros no Aiven é a fornecida
pelo solicitante. `learning_module_items` contém id, module_id, item_type,
song_id, title, instructions, sort_order, active, created_by, created_at e updated_at.
Não contém `content`. Perguntas/alternativas ficam em `learning_multiple_choice`.
Nenhum dado remoto foi alterado.

## Regressões e validação manual

`backend/test/modulesController.test.js` simula o adaptador de banco e exercita
os handlers reais: vazio, criar pergunta/música, editar, desativar, excluir,
isolamento beginner/intermediate, serialização e ausência de escrita nos GETs.
Não substitui um teste de integração com PostgreSQL.

`TriviaContentInstrumentedTest.java` exercita a Activity com API simulada:
abrir ambos os módulos vazios, incluir/editar/remover conteúdo e atualizar,
falha de rede sem conteúdo local ou antigo. Usa a sessão de teste da suíte Android.

Para validar ponta a ponta após publicar o backend e instalar o APK:

1. Abrir cada Trivia vazia e comparar com a administração.
2. Criar uma pergunta ativa, conferir module_id no banco, abrir/atualizar o aluno.
3. Repetir com música e conferir título/instruções.
4. Editar e atualizar; excluir e atualizar; verificar ausência do item removido.
5. Repetir no outro módulo e verificar que os itens não atravessam módulos.
6. Desativar um item: permanece visível na administração e sai do aluno.
7. Desligar a rede e atualizar: deve aparecer erro, sem aulas locais.

O app não recebe mudanças por push: use Atualizar ou reabra a Trivia.

## Resultado nesta execução

- Backend: `npm test`, 20 testes aprovados; `node --check` nos arquivos JS aprovado.
- Android: 15 testes unitários aprovados; APK debug e APK de instrumentação gerados.
- Lint debug aprovado após corrigir o escape de `C\:` em `local.properties`
  (arquivo de configuração local, ignorado pelo Git). Restam 202 avisos.
- Aparelho SM-A556E/Android 15: runner registrou `OK (2 tests)` para a classe
  TriviaContentInstrumentedTest. O Gradle/UTP falhou ao receber os resultados
  por erro gRPC `UNAVAILABLE`; portanto a tarefa connectedDebugAndroidTest saiu
  com erro apesar dos testes aprovados no log do runner.
- Java 21 foi usado; Java 25 não é compatível com este Gradle. Os downloads
  exigiram certificados do sistema (Node `--use-system-ca`; Gradle
  `-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE`).
- Backend não tem scripts de lint/build/typecheck; é JavaScript, sem TypeScript.
- A integração com Aiven e a publicação do backend permanecem para validação
  no ambiente configurado, conforme roteiro acima.

## Complemento: tela inicial e repertório

A primeira correção alterava apenas os botões do menu. A tela inicial ainda
chamava showCourse e apresentava aulas locais; agora showCourse abre o módulo
remoto correspondente, inclusive ao trocar nível e retornar de outras telas.
O cabeçalho mantém o menu acessível nos estados de carregamento/erro e no módulo.

SongRepository também tinha fallback do cache público vazio para a lista local
administrativa, além de fallback em falhas HTTP/rede. Esses caminhos foram
removidos. O repertório aguarda GET /api/songs antes de exibir músicas, invalida
cache público na consulta, distingue erro de lista vazia e oferece Atualizar.
Os registros locais administrativos não foram apagados.

A regressão da Activity agora entra por showCourse, não apenas pelo método do
menu. SongRepositoryInstrumentedTest cobre cache público vazio, resposta vazia
após exclusão, resposta com música e falha de rede sem recuperar música local.

Validação do complemento: testDebugUnitTest, assembleDebug,
assembleDebugAndroidTest e lintDebug aprovados. APK atualizado instalado no
aparelho conectado. Execução direta pelo AndroidJUnitRunner via adb terminou
com OK (3 tests): dois da Activity e um do repositório de músicas. Esta execução
não dependeu da coleta UTP que falhou anteriormente. API simulada nos testes;
confirmação contra Aiven continua pendente. Os testes usam sessão temporária.

Auditoria posterior: ver CONTENT_SOURCE_AUDIT.md. Foram removidos também os
fallbacks de arpejos, o seed automático de arpejos em GET e o fallback local da
administração de músicas. As mutações de músicas aguardam confirmação da API.
Resultado atualizado: 23 testes backend, 15 unitários Android, 4 instrumentados,
lint e build aprovados. BASE_URL Render confirmada; conexão Render→Aiven ainda
não verificada por falta de configuração remota/sessão autenticada disponível.
