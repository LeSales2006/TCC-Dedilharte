# Modo noturno — validação

A preferência já era gravada e o AppCompat já recriava a Activity. O problema estava nas cores fixas em Java/XML e nos diálogos que forçavam o tema claro. A correção reutiliza esse mecanismo e fornece cores diurnas/noturnas para telas, cartões, formulários, menus, diálogos e desenho do instrumento. Os valores do tema claro foram preservados. Nenhuma lógica de autenticação, persistência de perfil ou backend foi alterada nesta correção.

## Validação realizada

- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` e `lintDebug`: BUILD SUCCESSFUL, JDK 21, execução offline.
- 32 testes unitários aprovados; lint sem erros, com 182 avisos.
- Teste instrumentado `NightThemeInstrumentedTest#modeSwitchUpdatesScreensAndPersists`: aprovado no emulador API 36.1, usando sessão/API simuladas e pacote isolado.
- Verificados: switch real, aplicação imediata, configurações, músicas, arpejos, sobre, perfil, menu, diálogo de recuperação, login, cadastro, reabertura com preferência salva e retorno às cores originais do tema claro.
- Encerramento do processo por `am force-stop` e inicialização fria do login: preferência noturna mantida; conferência visual das capturas em `build/night-*.png`.
- `git diff --check`: aprovado. Compatibilidade de compilação mantida com o mínimo Android existente; não houve execução em dispositivo Android 7.
- Os primeiros testes foram interrompidos por ANRs do Launcher/System UI do emulador; após fechar esses alertas e ajustar a seleção da janela do menu no teste, a execução completa passou.

APK final: `app/build/outputs/apk/debug/app-debug.apk`, applicationId `com.example.dedilharte`.

## Arquivos desta correção

Java, sob `app/src/main/java/com/example/dedilharte/`:

- `MainActivity.java`, `RegisterActivity.java`
- `data/ThemeStore.java`
- `view/PreferencesFields.java`, `view/PasswordDialogs.java`
- `view/ProgressDashboardView.java`, `view/GuitarPracticeView.java`

Recursos, sob `app/src/main/res/`:

- `values/colors.xml`, `values/styles.xml`
- `values-night/colors.xml`, `values-night/themes.xml`
- `layout/activity_login.xml`, `layout/activity_main.xml`, `layout/activity_register.xml`
- `layout/dialog_edit_profile.xml`, `layout/drawer_profile.xml`
- `drawable/bg_cyan_button_16.xml`, `drawable/bg_dark_button_16.xml`, `drawable/bg_dark_field_16.xml`
- `drawable/bg_drawer_item.xml`, `drawable/bg_profile_field.xml`, `drawable/bg_profile_photo.xml`
- `drawable/bg_teal_panel_20.xml`, `drawable/bg_white_button_16.xml`

Teste: `app/src/androidTest/java/com/example/dedilharte/NightThemeInstrumentedTest.java`.

Esta lista se refere somente ao modo noturno; o workspace já continha outras alterações anteriores.
