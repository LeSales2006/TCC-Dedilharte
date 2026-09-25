# Dedilharte

Aplicativo Android educacional para o ensino de dedilhado no violão. O projeto foi desenvolvido em Java para Android Studio e funciona a partir do Android 7.0.

## Requisitos técnicos

- Android Studio com JDK 17
- Android SDK 36
- Android Gradle Plugin 8.12.3
- Gradle 8.13, já configurado no Wrapper
- `minSdk 24`: Android 7.0
- `targetSdk 36`
- Linguagem principal: Java

## Funcionalidades

- Cadastro local do nome do estudante
- Escolha entre trilha Iniciante e Intermediária
- 14 aulas didáticas, com objetivo, teoria e passo a passo
- Marcação de aulas concluídas
- Progresso salvo no aparelho com `SharedPreferences`
- Estado inicial igual ao protótipo: somente a primeira aula concluída
- Biblioteca de arpejos por acorde
- Repertório de exercícios com busca
- Modo treino animado com:
  - seis cordas;
  - indicação dos dedos P, I, M e A;
  - controle de 40 a 140 BPM;
  - iniciar, pausar e reiniciar;
  - guia sonoro opcional.
- Edição do perfil
- Tela de progresso
- Funcionamento offline

## Estrutura principal

```text
app/src/main/java/com/example/dedilharte/
├── MainActivity.java
├── data/
│   ├── LessonRepository.java
│   └── ProgressStore.java
├── model/
│   └── Lesson.java
└── view/
    └── GuitarPracticeView.java
```

## Como abrir no Android Studio

1. Extraia a pasta do projeto.
2. Abra o Android Studio.
3. Clique em **Open**.
4. Selecione a pasta `TCC-Dedilharte`.
5. Aguarde a sincronização do Gradle.
6. Crie ou selecione um dispositivo com Android 7.0 ou superior.
7. Execute o módulo `app`.

## Testes

O catálogo de aulas possui testes unitários para validar:

- quantidade de aulas por nível;
- IDs únicos;
- ordem das aulas;
- padrões de cordas válidos;
- recuperação de aula por ID.

Para executar:

```bash
./gradlew test
```

## Integração com PostgreSQL / Aiven

O projeto agora possui uma API REST separada em `backend/` para sincronizar dados com PostgreSQL no Aiven:

```text
Android
   ↓
REST API Node.js/Express
   ↓
PostgreSQL Aiven
```

O aplicativo Android continua usando `SharedPreferences` e funciona offline. Quando o usuario cria ou edita conta, conclui aulas ou restaura o progresso, o app salva localmente primeiro e tenta sincronizar com a API em segundo plano. Falhas de internet, API ou banco nao bloqueiam o uso.

As credenciais do Aiven ficam somente no backend, no arquivo `backend/.env`, que nao deve ser versionado. Use `backend/.env.example` como modelo.

Comandos principais:

```bash
cd backend
npm install
copy .env.example .env
npm start
```

Para testar no Android Emulator, a URL da API fica centralizada em `ApiConfig.BASE_URL` usando `http://10.0.2.2:3000/`. Para celular fisico, use o IP local do computador ou uma URL publica HTTPS.

O passo a passo completo para configurar o Aiven, criar tabelas com `schema.sql`, iniciar a API e testar `/health` esta em `AIVEN_SETUP.md`.

## Identidade visual

- Ciano claro: aula não concluída
- Ciano escuro: item pressionado ou selecionado
- Ciano acinzentado com `✓`: aula concluída

Essa regra preserva a lógica definida no protótipo web do Dedilharte.
