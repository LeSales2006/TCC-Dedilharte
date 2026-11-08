# Diagnóstico do carregamento de músicas — 03/11/2026

## Atualização — 07/11/2026

O `GET /health` sem autenticação retornou novamente HTTP 200 com
`{"ok":true,"database":true}`. Uma conexão TLS separada, usando as autoridades
confiáveis do Windows e mantendo a verificação ativa, apresentou o emissor
`AVG Web/Mail Shield Root`, reconfirmando a interceptação pelo AVG.

As rotas autenticadas de músicas e Trivias não foram consultadas nesta
continuação: a autorização específica para usar a sessão salva permanece
pendente. O HTTP 500 relatado abaixo é a evidência histórica de 03/11, não uma
nova medição. Também não foi repetida a conexão do Android ao servidor real.

Os testes locais atualizados e as correções de 07/11 estão em
`REPAIR_VALIDATION.md`. Evidências remotas novas:
`build/repair-2026-11-07-remote-health.log` e `build/repair-2026-11-07-tls.log`.

## Registro original — 03/11/2026

Foram reproduzidas duas falhas independentes: uma de HTTPS no emulador deste
computador e outra na rota de músicas do backend remoto.

## 1. O Android rejeita o certificado HTTPS

O certificado apresentado neste computador para
`https://tcc-dedilharte.onrender.com` tem o emissor
`AVG Web/Mail Shield Root`, indicando interceptação HTTPS pelo AVG.

A validação de sessão do próprio aplicativo, no AVD existente aberto em modo
somente leitura, registrou:

```text
DedilharteAuth: validacao de sessao falhou:
java.security.cert.CertPathValidatorException:
Trust anchor for certification path not found.
```

Um GET de diagnóstico executado dentro do Android também falhou com
`SSLHandshakeException` e a mesma causa. Evidência do aplicativo:
`app/build/connection-diagnostic/android-network.log`.

No Windows, o GET funcionou usando os certificados confiáveis do sistema.
O Node falhou com `UNABLE_TO_VERIFY_LEAF_SIGNATURE`; usando `--use-system-ca`,
o mesmo GET funcionou. Isso reforça o diagnóstico de confiança no certificado
introduzido pelo AVG, e não de indisponibilidade geral da API.

É necessário resolver a interceptação HTTPS do AVG para o ambiente de
desenvolvimento. A validação TLS do aplicativo deve continuar habilitada.

## 2. A API de músicas retorna erro interno

Requisições feitas pelo computador, usando em memória a sessão já salva no
aplicativo, produziram:

| Requisição | Resultado |
| --- | --- |
| `GET /health` | 200, `{"ok":true,"database":true}` |
| `GET /api/modules` autenticado | 200, dois módulos |
| `GET /api/songs` autenticado | 500, `{"error":"Erro interno do servidor."}` |

Portanto, mesmo resolvendo o HTTPS do emulador, o carregamento de músicas
continua dependendo da correção do backend.

O arquivo `backend/src/controllers/songsController.js` no HEAD público do
GitHub ainda consulta `recommended_bpm`, `queue_order`, `in_queue`, `status`
e timestamps. A versão local já foi alterada para `bpm`, `sort_order`,
`active`, `content` e `created_by`, conforme a auditoria anterior do projeto.
Essa alteração ainda não está commitada. O SQL de criação e a migration
histórica de músicas também continuam com os nomes antigos.

A divergência de esquema/versão é a principal suspeita para o HTTP 500.
O endpoint de saúde só executa `SELECT 1`: ele não valida a tabela `songs`.
Sem os logs do Render ou acesso ao esquema remoto, não foi possível confirmar
a coluna exata que falha nem o commit efetivamente implantado no Render.

O próximo passo é conferir o erro SQL nos logs do Render, validar os nomes
das colunas remotas e publicar o backend compatível. Alterar somente o Android
não atualiza a API hospedada.

## Configuração e validação

- `C:/Users/VAIO/.continue/config.yaml` configura Continue/Ollama em
  `localhost:11434`; não participa da conexão do app com as músicas.
- A URL usada pelo Android está em `network/ApiConfig.java` e aponta para o
  Render, não para um backend local.
- Os nove testes de `songsController.test.js` e `modulesController.test.js`
  passaram na versão local. Usam banco simulado, portanto não comprovam a
  consulta ao banco de produção.
- O diagnóstico não modificou o código do app, certificados ou dados remotos.
  O emulador de diagnóstico foi aberto com `-read-only -no-snapshot-save`.
