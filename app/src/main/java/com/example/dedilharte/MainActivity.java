package com.example.dedilharte;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.dedilharte.audio.GuitarSoundPlayer;
import com.example.dedilharte.data.LessonRepository;
import com.example.dedilharte.data.ProgressStore;
import com.example.dedilharte.data.SongRepository;
import com.example.dedilharte.data.TablatureParser;
import com.example.dedilharte.model.Lesson;
import com.example.dedilharte.model.Song;
import com.example.dedilharte.model.TablatureEvent;
import com.example.dedilharte.network.model.UserResponse;
import com.example.dedilharte.sync.DedilharteSyncManager;
import com.example.dedilharte.view.GuitarPracticeView;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/**
 * Aplicativo educacional offline para o ensino de dedilhado no violão.
 *
 * Compatibilidade mínima: Android 7.0 (API 24).
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PROFILE_PHOTO = 42;
    private static final String ACTIVE_USER_ID = "active_user_id";
    private static final String ACCOUNT_IDS = "account_ids";
    private static final String USER_PREFIX = "user_";
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_STUDENT = "student";
    private static final int TEAL = Color.rgb(50, 150, 145);
    private static final int DARK = Color.rgb(27, 79, 76);
    private static final int CYAN = Color.rgb(84, 214, 221);
    private static final int PALE = Color.rgb(137, 201, 211);
    private static final int SOFT = Color.rgb(232, 246, 246);
    private static final int TEXT = Color.rgb(35, 66, 64);
    private static final int WHITE = Color.WHITE;
    private static final Arpeggio[] ARPEGGIOS = {
            new Arpeggio(
                    "D\u00f3 maior (C)",
                    new int[][]{{5, 3}, {3, 0}, {2, 1}, {1, 0}}
            ),
            new Arpeggio(
                    "R\u00e9 maior (D)",
                    new int[][]{{4, 0}, {3, 2}, {2, 3}, {1, 2}}
            ),
            new Arpeggio(
                    "Mi maior (E)",
                    new int[][]{{6, 0}, {3, 1}, {2, 0}, {1, 0}}
            ),
            new Arpeggio(
                    "F\u00e1 maior (F)",
                    new int[][]{{6, 1}, {3, 2}, {2, 1}, {1, 1}}
            ),
            new Arpeggio(
                    "Sol maior (G)",
                    new int[][]{{6, 3}, {3, 0}, {2, 0}, {1, 3}}
            ),
            new Arpeggio(
                    "L\u00e1 maior (A)",
                    new int[][]{{5, 0}, {3, 2}, {2, 2}, {1, 0}}
            ),
            new Arpeggio(
                    "Si maior (B)",
                    new int[][]{{5, 2}, {3, 4}, {2, 4}, {1, 2}}
            )
    };

    private static final class Arpeggio {
        private final String title;
        private final int[][] notes;
        private final int[] pattern;
        private final String tablature;

        private Arpeggio(String title, int[][] notes) {
            this.title = title;
            this.notes = notes;
            this.pattern = patternFromNotes(notes);
            this.tablature = tablatureFromNotes(notes);
        }
    }

    private static int[] patternFromNotes(int[][] notes) {
        int[] pattern = new int[notes.length];
        for (int i = 0; i < notes.length; i++) {
            pattern[i] = notes[i][0];
        }
        return pattern;
    }

    private static String tablatureFromNotes(int[][] notes) {
        String[] labels = {"E", "B", "G", "D", "A", "E"};
        int columnsPerEvent = TablatureParser.DEFAULT_COLUMNS_PER_BEAT;
        int width = Math.max(1, (notes.length - 1) * columnsPerEvent + 1);
        StringBuilder[] lines = new StringBuilder[6];
        for (int stringIndex = 0; stringIndex < 6; stringIndex++) {
            lines[stringIndex] = new StringBuilder();
            for (int column = 0; column < width; column++) {
                lines[stringIndex].append('-');
            }
        }
        for (int i = 0; i < notes.length; i++) {
            int stringNumber = notes[i][0];
            int fret = notes[i][1];
            int stringIndex = stringNumber - 1;
            if (stringIndex >= 0 && stringIndex < 6 && fret >= 0 && fret <= 9) {
                lines[stringIndex].setCharAt(i * columnsPerEvent, (char) ('0' + fret));
            }
        }
        StringBuilder result = new StringBuilder();
        for (int stringIndex = 0; stringIndex < 6; stringIndex++) {
            if (stringIndex > 0) {
                result.append('\n');
            }
            result.append(labels[stringIndex]).append('|').append(lines[stringIndex]).append('|');
        }
        return result.toString();
    }

    private enum Screen {
        WELCOME,
        LEVEL,
        COURSE,
        LESSON,
        PRACTICE,
        ARPEGGIOS,
        SONGS,
        ADMIN_SONGS,
        ADMIN_EDITOR,
        PROGRESS,
        ABOUT
    }

    private FrameLayout root;
    private SharedPreferences profile;
    private ProgressStore progressStore;
    private DedilharteSyncManager syncManager;
    private SongRepository songRepository;
    private GuitarSoundPlayer guitarSoundPlayer;
    private GuitarPracticeView practiceView;
    private Lesson currentLesson;
    private Song currentSong;
    private Screen screen = Screen.WELCOME;
    private Screen practiceReturnScreen = Screen.COURSE;
    private String currentUserId = "";
    private String userName = "";
    private String level = LessonRepository.BEGINNER;
    private String userRole = ROLE_STUDENT;
    private String profilePhotoUri = "";
    private boolean soundEnabled = true;
    private ImageView profilePhotoPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        profile = getSharedPreferences("dedilharte_profile", MODE_PRIVATE);
        migrateLegacyProfileIfNeeded();
        currentUserId = profile.getString(ACTIVE_USER_ID, "");
        loadActiveProfile();
        progressStore = new ProgressStore(this, currentUserId);
        syncManager = new DedilharteSyncManager();
        songRepository = new SongRepository(this);

        if (savedInstanceState != null) {
            String restoredLevel = savedInstanceState.getString("level");
            if (restoredLevel != null) {
                level = restoredLevel;
            }
            String lessonId = savedInstanceState.getString("lesson_id");
            if (lessonId != null) {
                currentLesson = LessonRepository.getById(lessonId);
            }
        }

        if (currentUserId.trim().isEmpty()) {
            showWelcome();
        } else if (isAdmin()) {
            synchronizeCurrentUser(false);
            showAdminSongs();
        } else {
            synchronizeCurrentUser(false);
            showCourse(level);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("level", level);
        if (currentLesson != null) {
            outState.putString("lesson_id", currentLesson.getId());
        }
    }

    private void migrateLegacyProfileIfNeeded() {
        String legacyName = profile.getString("name", "");
        if (legacyName.trim().isEmpty() || !accountIds().isEmpty()) {
            return;
        }

        String legacyId = "legacy_" + System.currentTimeMillis();
        Set<String> accounts = new HashSet<>();
        accounts.add(legacyId);
        profile.edit()
                .putStringSet(ACCOUNT_IDS, accounts)
                .putString(ACTIVE_USER_ID, legacyId)
                .putString(accountKey(legacyId, "name"), legacyName)
                .putString(accountKey(legacyId, "level"),
                        profile.getString("level", LessonRepository.BEGINNER))
                .putString(accountKey(legacyId, "role"), ROLE_STUDENT)
                .putString(accountKey(legacyId, "photo_uri"), profile.getString("photo_uri", ""))
                .putBoolean(accountKey(legacyId, "sound_enabled"),
                        profile.getBoolean("sound_enabled", true))
                .putLong(accountKey(legacyId, "updated_at"), System.currentTimeMillis())
                .apply();
    }

    private Set<String> accountIds() {
        return new HashSet<>(profile.getStringSet(ACCOUNT_IDS, new HashSet<>()));
    }

    private String accountKey(String userId, String field) {
        return USER_PREFIX + userId + "_" + field;
    }

    private void loadActiveProfile() {
        if (currentUserId.trim().isEmpty()) {
            userName = "";
            level = LessonRepository.BEGINNER;
            userRole = ROLE_STUDENT;
            profilePhotoUri = "";
            soundEnabled = true;
            return;
        }

        userName = profile.getString(accountKey(currentUserId, "name"), "");
        level = profile.getString(accountKey(currentUserId, "level"), LessonRepository.BEGINNER);
        userRole = profile.getString(accountKey(currentUserId, "role"), ROLE_STUDENT);
        profilePhotoUri = profile.getString(accountKey(currentUserId, "photo_uri"), "");
        soundEnabled = profile.getBoolean(accountKey(currentUserId, "sound_enabled"), true);
    }

    private void saveActiveProfile() {
        if (currentUserId.trim().isEmpty()) {
            return;
        }
        profile.edit()
                .putString(accountKey(currentUserId, "name"), userName)
                .putString(accountKey(currentUserId, "level"), level)
                .putString(accountKey(currentUserId, "role"), userRole)
                .putString(accountKey(currentUserId, "photo_uri"), profilePhotoUri)
                .putBoolean(accountKey(currentUserId, "sound_enabled"), soundEnabled)
                .apply();
    }

    private void markActiveUserUpdated() {
        if (currentUserId.trim().isEmpty()) {
            return;
        }
        profile.edit()
                .putLong(accountKey(currentUserId, "updated_at"), System.currentTimeMillis())
                .apply();
    }

    private long activeUserUpdatedAt() {
        if (currentUserId.trim().isEmpty()) {
            return System.currentTimeMillis();
        }
        return profile.getLong(accountKey(currentUserId, "updated_at"), System.currentTimeMillis());
    }

    private void setActiveUserUpdatedAt(long updatedAtMillis) {
        if (currentUserId.trim().isEmpty() || updatedAtMillis <= 0) {
            return;
        }
        profile.edit()
                .putLong(accountKey(currentUserId, "updated_at"), updatedAtMillis)
                .apply();
    }

    private void syncActiveUser() {
        if (syncManager == null || currentUserId.trim().isEmpty()) {
            return;
        }
        syncManager.syncUser(currentUserId, userName, activeUserUpdatedAt());
    }

    private void synchronizeCurrentUser(boolean includeAllProgress) {
        if (syncManager == null || currentUserId.trim().isEmpty()) {
            return;
        }
        syncActiveUser();
        syncManager.fetchRemoteUser(currentUserId, this::applyRemoteUserIfNewer);
        syncAllProgress(includeAllProgress);
        syncManager.fetchRemoteProgress(currentUserId, progressStore, () -> {
            if (screen == Screen.PROGRESS) {
                showProgress();
            } else if (screen == Screen.COURSE) {
                showCourse(level);
            }
        });
    }

    private void syncLessonProgress(String lessonId) {
        if (syncManager == null || currentUserId.trim().isEmpty()) {
            return;
        }
        syncManager.syncProgress(currentUserId, progressStore.progressRecord(lessonId));
    }

    private void syncAllProgress(boolean includeAllProgress) {
        if (syncManager == null || currentUserId.trim().isEmpty()) {
            return;
        }
        syncManager.syncProgressRecords(
                currentUserId,
                progressStore.progressRecords(LessonRepository.getAll()),
                includeAllProgress
        );
    }

    private void applyRemoteUserIfNewer(UserResponse remoteUser) {
        if (remoteUser == null
                || remoteUser.id == null
                || !remoteUser.id.equals(currentUserId)
                || remoteUser.updatedAtMillis <= activeUserUpdatedAt()) {
            return;
        }
        userName = remoteUser.name == null ? userName : remoteUser.name;
        setActiveUserUpdatedAt(remoteUser.updatedAtMillis);
        saveActiveProfile();
        if (screen == Screen.PROGRESS) {
            showProgress();
        } else if (screen == Screen.COURSE) {
            showCourse(level);
        }
    }

    private void createAccount(String name, boolean admin) {
        currentUserId = "user_" + System.currentTimeMillis();
        userName = name;
        level = LessonRepository.BEGINNER;
        userRole = admin ? ROLE_ADMIN : ROLE_STUDENT;
        profilePhotoUri = "";
        soundEnabled = true;

        Set<String> accounts = accountIds();
        accounts.add(currentUserId);
        profile.edit()
                .putStringSet(ACCOUNT_IDS, accounts)
                .putString(ACTIVE_USER_ID, currentUserId)
                .apply();
        markActiveUserUpdated();
        saveActiveProfile();
        progressStore.setUserId(currentUserId);
        synchronizeCurrentUser(false);
    }

    private void switchAccount(String userId) {
        currentUserId = userId;
        profile.edit().putString(ACTIVE_USER_ID, currentUserId).apply();
        loadActiveProfile();
        progressStore.setUserId(currentUserId);
        synchronizeCurrentUser(false);
        if (isAdmin()) {
            showAdminSongs();
        } else {
            showCourse(level);
        }
    }

    private void logout() {
        stopPractice();
        currentLesson = null;
        currentUserId = "";
        profile.edit().remove(ACTIVE_USER_ID).apply();
        loadActiveProfile();
        progressStore.setUserId(currentUserId);
        showWelcome();
    }

    private void confirmDeleteAccount() {
        if (currentUserId.trim().isEmpty()) {
            showWelcome();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Excluir conta?")
                .setMessage("Esta acao remove seu perfil e progresso deste aparelho. Nao sera possivel desfazer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> deleteCurrentAccount())
                .show();
    }

    private void deleteCurrentAccount() {
        stopPractice();
        currentLesson = null;
        currentSong = null;

        String deletedUserId = currentUserId;
        Set<String> accounts = accountIds();
        accounts.remove(deletedUserId);

        SharedPreferences.Editor editor = profile.edit()
                .putStringSet(ACCOUNT_IDS, accounts)
                .remove(ACTIVE_USER_ID)
                .remove(accountKey(deletedUserId, "name"))
                .remove(accountKey(deletedUserId, "level"))
                .remove(accountKey(deletedUserId, "role"))
                .remove(accountKey(deletedUserId, "photo_uri"))
                .remove(accountKey(deletedUserId, "sound_enabled"))
                .remove(accountKey(deletedUserId, "updated_at"));
        if (deletedUserId.startsWith("legacy_")) {
            editor.remove("name")
                    .remove("level")
                    .remove("photo_uri")
                    .remove("sound_enabled");
        }
        editor.apply();

        syncManager.deleteUser(deletedUserId);
        progressStore.deleteUserData(deletedUserId);
        currentUserId = "";
        loadActiveProfile();
        progressStore.setUserId(currentUserId);
        Toast.makeText(this, "Conta excluida", Toast.LENGTH_SHORT).show();
        showWelcome();
    }

    private void showWelcome() {
        stopPractice();
        screen = Screen.WELCOME;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout page = column(TEAL, Gravity.CENTER);
        page.setPadding(dp(32), dp(24), dp(32), dp(24));
        page.addView(logo(178));
        page.addView(text("Boas-vindas!", 23, WHITE, true, Gravity.CENTER, 0, 14));
        page.addView(text(
                "Aprenda dedilhado no seu ritmo, com exercícios visuais e prática guiada.",
                16, WHITE, false, Gravity.CENTER, 0, 34
        ));

        Set<String> accounts = accountIds();
        if (!accounts.isEmpty()) {
            page.addView(text("Entrar com conta salva", 17, WHITE, true, Gravity.START, 0, 10));
            for (String accountId : accounts) {
                String savedName = profile.getString(accountKey(accountId, "name"), "");
                if (savedName.trim().isEmpty()) {
                    continue;
                }
                TextView accountButton = button(savedName, WHITE, DARK, 54);
                accountButton.setOnClickListener(v -> switchAccount(accountId));
                page.addView(accountButton, matchWrap(0, 10));
            }
            page.addView(text("Ou crie uma nova conta", 17, WHITE, true, Gravity.START, 10, 10));
        } else {
            page.addView(text("Como podemos chamar você?", 17, WHITE, true, Gravity.START, 0, 10));
        }

        EditText nameInput = input("", "Digite seu nome");
        page.addView(nameInput, matchWrap(0, 18));

        TextView continueButton = button("CRIAR CONTA", WHITE, DARK, 62);
        continueButton.setOnClickListener(v -> {
            String typed = nameInput.getText().toString().trim();
            if (typed.isEmpty()) {
                nameInput.setError("Digite seu nome");
                nameInput.requestFocus();
                return;
            }
            createAccount(typed, false);
            showLevelChoice();
        });
        page.addView(continueButton, matchWrap(0, 0));
        root.addView(page, matchMatch());
    }

    private void showLevelChoice() {
        stopPractice();
        screen = Screen.LEVEL;
        root.removeAllViews();

        LinearLayout page = column(TEAL, Gravity.CENTER);
        page.setPadding(dp(32), dp(24), dp(32), dp(24));
        page.addView(logo(164));
        page.addView(text("Dedilharte", 29, WHITE, true, Gravity.CENTER, 4, 54));
        page.addView(text(
                firstName() + ", qual é o seu nível atual?",
                21, WHITE, true, Gravity.CENTER, 0, 10
        ));
        page.addView(text(
                "Você poderá trocar de trilha a qualquer momento.",
                15, WHITE, false, Gravity.CENTER, 0, 30
        ));

        TextView beginner = button("INICIANTE", CYAN, WHITE, 64);
        beginner.setOnClickListener(v -> chooseLevel(LessonRepository.BEGINNER));
        page.addView(beginner, matchWrap(0, 16));

        TextView intermediate = button("INTERMEDIÁRIO", WHITE, DARK, 64);
        intermediate.setOnClickListener(v -> chooseLevel(LessonRepository.INTERMEDIATE));
        page.addView(intermediate, matchWrap(0, 0));
        root.addView(page, matchMatch());
    }

    private void chooseLevel(String selectedLevel) {
        level = selectedLevel;
        saveActiveProfile();
        showCourse(level);
    }

    private void showCourse(String selectedLevel) {
        stopPractice();
        screen = Screen.COURSE;
        currentLesson = null;
        level = selectedLevel;
        saveActiveProfile();
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(mainHeader(), new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));

        content.addView(text("Olá, " + firstName() + "!", 18, TEAL, true, Gravity.START, 0, 6));
        content.addView(text(
                "Trilha " + selectedLevel,
                30, DARK, true, Gravity.START, 0, 8
        ));
        content.addView(text(
                selectedLevel.equals(LessonRepository.BEGINNER)
                        ? "Comece pelas cordas e avance até seu primeiro acompanhamento completo."
                        : "Desenvolva coordenação, variações rítmicas e arpejos mais completos.",
                15, TEXT, false, Gravity.START, 0, 18
        ));

        List<Lesson> lessons = LessonRepository.getLessons(selectedLevel);
        int completed = progressStore.completedCount(lessons);
        int percent = progressStore.progressPercent(lessons);
        content.addView(progressPanel(completed, lessons.size(), percent), matchWrap(0, 18));

        Lesson next = firstIncomplete(lessons);
        if (next != null) {
            TextView continueButton = button(
                    "CONTINUAR: AULA " + next.getOrder(),
                    DARK, WHITE, 58
            );
            continueButton.setOnClickListener(v -> showLessonDetail(next));
            content.addView(continueButton, matchWrap(0, 24));
        }

        content.addView(text("Aulas", 22, DARK, true, Gravity.START, 0, 12));
        for (Lesson lesson : lessons) {
            TextView card = lessonCard(lesson);
            card.setOnClickListener(v -> showLessonDetail(lesson));
            content.addView(card, matchWrap(0, 14));
        }

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private View progressPanel(int completed, int total, int percent) {
        LinearLayout panel = column(SOFT, Gravity.TOP);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(roundRect(SOFT, 16));
        panel.addView(text("Seu progresso", 16, DARK, true, Gravity.START, 0, 5));
        panel.addView(text(
                completed + " de " + total + " aulas concluídas",
                14, TEXT, false, Gravity.START, 0, 10
        ));
        ProgressBar bar = horizontalProgress(percent);
        panel.addView(bar, new LinearLayout.LayoutParams(-1, dp(13)));
        panel.addView(text(percent + "%", 13, TEAL, true, Gravity.END, 7, 0));
        return panel;
    }

    private void showLessonDetail(Lesson lesson) {
        stopPractice();
        screen = Screen.LESSON;
        currentLesson = lesson;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Aula " + lesson.getOrder(), () -> showCourse(lesson.getLevel())),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(36));

        content.addView(statusPill(
                progressStore.isCompleted(lesson.getId()) ? "CONCLUÍDA ✓" : lesson.getLevel().toUpperCase(Locale.ROOT),
                progressStore.isCompleted(lesson.getId()) ? PALE : CYAN
        ), wrapStart(0, 16));
        content.addView(text(lesson.getTitle(), 29, DARK, true, Gravity.START, 0, 8));
        content.addView(text(lesson.getSubtitle(), 16, TEAL, true, Gravity.START, 0, 24));

        content.addView(infoCard("OBJETIVO", lesson.getObjective()), matchWrap(0, 14));
        content.addView(infoCard("COMO FUNCIONA", lesson.getTheory()), matchWrap(0, 20));

        content.addView(text("Passo a passo", 21, DARK, true, Gravity.START, 0, 12));
        String[] steps = lesson.getSteps();
        for (int i = 0; i < steps.length; i++) {
            content.addView(stepCard(i + 1, steps[i]), matchWrap(0, 10));
        }

        content.addView(text("Padrão do exercício", 21, DARK, true, Gravity.START, 14, 8));
        content.addView(infoCard(
                lesson.getChord(),
                "Cordas: " + patternToText(lesson.getPattern()) + "\nVelocidade sugerida: " + lesson.getBpm() + " BPM"
        ), matchWrap(0, 18));

        TextView practiceButton = button("ABRIR MODO TREINO", DARK, WHITE, 62);
        practiceButton.setOnClickListener(v -> showPractice(
                lesson.getTitle(),
                lesson.getChord(),
                lesson.getPattern(),
                lesson.getBpm(),
                lesson,
                Screen.LESSON
        ));
        content.addView(practiceButton, matchWrap(0, 12));

        TextView completionButton = completionButton(lesson);
        content.addView(completionButton, matchWrap(0, 0));

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private TextView completionButton(Lesson lesson) {
        boolean completed = progressStore.isCompleted(lesson.getId());
        TextView button = button(
                completed ? "AULA CONCLUÍDA ✓" : "MARCAR COMO CONCLUÍDA",
                completed ? PALE : CYAN,
                WHITE,
                62
        );
        button.setOnClickListener(v -> {
            boolean newState = !progressStore.isCompleted(lesson.getId());
            progressStore.setCompleted(lesson.getId(), newState);
            syncLessonProgress(lesson.getId());
            Toast.makeText(
                    this,
                    newState ? "Aula concluída!" : "Conclusão removida",
                    Toast.LENGTH_SHORT
            ).show();
            showLessonDetail(lesson);
        });
        return button;
    }

    private void showPractice(
            String title,
            String chord,
            int[] pattern,
            int suggestedBpm,
            Lesson lesson,
            Screen returnScreen
    ) {
        stopPractice();
        screen = Screen.PRACTICE;
        practiceReturnScreen = returnScreen;
        currentLesson = lesson;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Modo treino", this::returnFromPractice),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        content.addView(text(title, 25, DARK, true, Gravity.START, 0, 4));
        content.addView(text(chord, 15, TEAL, true, Gravity.START, 0, 6));

        practiceView = new GuitarPracticeView(this);
        practiceView.setPattern(pattern);
        practiceView.setBpm(suggestedBpm);
        practiceView.setBackground(roundRect(WHITE, 18));
        LinearLayout.LayoutParams practiceParams = new LinearLayout.LayoutParams(-1, -2);
        practiceParams.setMargins(0, dp(2), 0, dp(8));
        content.addView(practiceView, practiceParams);

        TextView status = text(
                "Corda " + pattern[0] + " • " + fingerForString(pattern[0]),
                16, DARK, true, Gravity.CENTER, 0, 12
        );
        content.addView(status);

        TextView bpmLabel = text(
                suggestedBpm + " BPM",
                17, TEAL, true, Gravity.CENTER, 0, 4
        );
        content.addView(bpmLabel);

        SeekBar speed = new SeekBar(this);
        speed.setMax(100);
        speed.setProgress(suggestedBpm - 40);
        speed.setContentDescription("Controle de velocidade entre 40 e 140 BPM");
        speed.getProgressDrawable().setColorFilter(TEAL, PorterDuff.Mode.SRC_IN);
        speed.getThumb().setColorFilter(DARK, PorterDuff.Mode.SRC_IN);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int bpm = progress + 40;
                bpmLabel.setText(bpm + " BPM");
                if (practiceView != null) {
                    practiceView.setBpm(bpm);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        content.addView(speed, matchWrap(0, 10));

        final int[] repetition = {0};
        practiceView.setOnStepListener((position, stringNumber) -> {
            status.setText("Corda " + stringNumber + " • " + fingerForString(stringNumber));
            if (position == 0 && practiceView != null && practiceView.isRunning()) {
                repetition[0]++;
            }
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setWeightSum(2f);
        TextView startPause = button("INICIAR", DARK, WHITE, 58);
        TextView reset = button("REINICIAR", CYAN, WHITE, 58);
        LinearLayout.LayoutParams halfLeft = new LinearLayout.LayoutParams(0, -2, 1f);
        halfLeft.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams halfRight = new LinearLayout.LayoutParams(0, -2, 1f);
        halfRight.setMargins(dp(6), 0, 0, 0);
        controls.addView(startPause, halfLeft);
        controls.addView(reset, halfRight);
        content.addView(controls, matchWrap(0, 12));

        startPause.setOnClickListener(v -> {
            if (practiceView.isRunning()) {
                practiceView.pause();
                startPause.setText("CONTINUAR");
            } else {
                practiceView.start();
                startPause.setText("PAUSAR");
            }
        });
        reset.setOnClickListener(v -> {
            repetition[0] = 0;
            practiceView.reset();
            startPause.setText("INICIAR");
        });

        practiceView.setOnTabEventListener(new GuitarPracticeView.OnTabEventListener() {
            @Override
            public void onEvent(int position, com.example.dedilharte.model.TablatureEvent event) {
                status.setText(tabEventToText(event));
            }

            @Override
            public void onFinished() {
                startPause.setText("Reproduzir");
                status.setText("Sequencia finalizada");
            }
        });

        TextView sound = button(
                soundEnabled ? "SOM: LIGADO" : "SOM: DESLIGADO",
                SOFT, DARK, 52
        );
        sound.setOnClickListener(v -> {
            soundEnabled = !soundEnabled;
            saveActiveProfile();
            sound.setText(soundEnabled ? "SOM: LIGADO" : "SOM: DESLIGADO");
        });
        content.addView(sound, matchWrap(0, 12));

        if (lesson != null) {
            TextView complete = completionButton(lesson);
            complete.setOnClickListener(v -> {
                progressStore.setCompleted(lesson.getId(), true);
                syncLessonProgress(lesson.getId());
                Toast.makeText(this, "Aula concluída!", Toast.LENGTH_SHORT).show();
                returnFromPractice();
            });
            content.addView(complete, matchWrap(0, 0));
        }

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void showSongPractice(Song song) {
        stopPractice();
        screen = Screen.PRACTICE;
        practiceReturnScreen = Screen.SONGS;
        currentSong = song;
        root.removeAllViews();

        final int[] selectedBpm = {clampBpm(song.getRecommendedBpm())};
        final int[] lastPlayedPosition = {-1};
        final String[] bpmMode = {"100"};

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Tocar musica", this::returnFromPractice),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        content.addView(text(song.getTitle(), 25, DARK, true, Gravity.START, 0, 4));
        content.addView(text(song.getArtist() + "  •  " + song.getDifficulty(), 15, TEAL, true, Gravity.START, 0, 10));
        content.addView(text("BPM recomendado: " + song.getRecommendedBpm(), 15, TEXT, false, Gravity.START, 0, 10));

        TextView state = text("Carregando sons...", 15, DARK, true, Gravity.CENTER, 0, 8);
        TextView bpmLabel = text("BPM selecionado: " + selectedBpm[0], 17, TEAL, true, Gravity.CENTER, 0, 4);
        TextView progress = text("Nota 1 de 1", 14, TEXT, true, Gravity.CENTER, 0, 8);
        content.addView(state);
        content.addView(bpmLabel);

        practiceView = new GuitarPracticeView(this);
        practiceView.setTablatureText(song.getTablature());
        practiceView.setBpm(selectedBpm[0]);
        practiceView.setBackground(roundRect(WHITE, 18));
        LinearLayout.LayoutParams practiceParams = new LinearLayout.LayoutParams(-1, -2);
        practiceParams.setMargins(0, dp(2), 0, dp(8));
        content.addView(practiceView, practiceParams);

        TextView status = text("Pronto para iniciar", 16, DARK, true, Gravity.CENTER, 0, 12);
        content.addView(status);
        content.addView(progress);

        LinearLayout bpmRow = new LinearLayout(this);
        bpmRow.setOrientation(LinearLayout.HORIZONTAL);
        bpmRow.setWeightSum(3f);
        TextView half = button("50%", SOFT, DARK, 50);
        TextView threeQuarter = button("75%", SOFT, DARK, 50);
        TextView full = button("100%", SOFT, DARK, 50);
        bpmRow.addView(half, weightButton(0, 4));
        bpmRow.addView(threeQuarter, weightButton(4, 4));
        bpmRow.addView(full, weightButton(4, 0));
        content.addView(bpmRow, matchWrap(0, 10));

        TextView customMode = text("Personalizado", 13, DARK, true, Gravity.CENTER, 0, 8);
        customMode.setBackground(roundRect(SOFT, 18));
        customMode.setPadding(dp(12), dp(7), dp(12), dp(7));
        content.addView(customMode, matchWrap(0, 6));

        TextView customValue = text(selectedBpm[0] + " BPM", 20, TEAL, true, Gravity.CENTER, 0, 6);
        customValue.setContentDescription("BPM personalizado, " + selectedBpm[0] + " BPM");
        content.addView(customValue, matchWrap(0, 2));

        SeekBar customBpm = new SeekBar(this);
        customBpm.setMax(210);
        customBpm.setProgress(selectedBpm[0] - 30);
        customBpm.setPadding(dp(8), dp(8), dp(8), dp(8));
        customBpm.setMinimumHeight(dp(54));
        customBpm.setFocusable(true);
        customBpm.setContentDescription("BPM personalizado, " + selectedBpm[0] + " BPM");
        customBpm.getProgressDrawable().setColorFilter(TEAL, PorterDuff.Mode.SRC_IN);
        customBpm.getThumb().setColorFilter(DARK, PorterDuff.Mode.SRC_IN);
        content.addView(customBpm, matchWrap(0, 12));

        guitarSoundPlayer = new GuitarSoundPlayer(this);
        state.setText(guitarSoundPlayer.statusText());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setWeightSum(3f);
        TextView previous = button("⏪", SOFT, DARK, 58);
        TextView startPause = button(guitarSoundPlayer.isReady() ? "▶" : "Tentar novamente", DARK, WHITE, 58);
        TextView next = button("⏩", SOFT, DARK, 58);
        previous.setTextSize(24);
        startPause.setTextSize(24);
        next.setTextSize(24);
        previous.setContentDescription("Voltar para a nota anterior");
        startPause.setContentDescription(guitarSoundPlayer.isReady() ? "Continuar música" : "Tentar novamente");
        next.setContentDescription("Avançar para a próxima nota");
        controls.addView(previous, weightButton(0, 6));
        controls.addView(startPause, weightButton(6, 6));
        controls.addView(next, weightButton(6, 0));
        content.addView(controls, matchWrap(0, 12));
        TextView reset = button("Reiniciar", CYAN, WHITE, 52);
        content.addView(reset, matchWrap(0, 12));
        updatePracticeProgress(progress);
        updateNavigationButtons(previous, next);
        startPause.postDelayed(() -> {
            if (guitarSoundPlayer != null && guitarSoundPlayer.isReady() && !practiceView.isRunning()) {
                state.setText(guitarSoundPlayer.statusText());
                startPause.setText("▶");
                startPause.setContentDescription("Continuar música");
            }
        }, 900L);

        updateBpmSelection(half, threeQuarter, full, customMode, bpmMode[0]);
        View.OnClickListener bpmChoice = v -> {
            if (practiceView != null && practiceView.isRunning()) {
                pauseForBpmChange(state, startPause, lastPlayedPosition);
            }
            int base = song.getRecommendedBpm();
            if (v == half) {
                selectedBpm[0] = clampBpm(Math.round(base * .5f));
                bpmMode[0] = "50";
            } else if (v == threeQuarter) {
                selectedBpm[0] = clampBpm(Math.round(base * .75f));
                bpmMode[0] = "75";
            } else {
                selectedBpm[0] = clampBpm(base);
                bpmMode[0] = "100";
            }
            customBpm.setProgress(selectedBpm[0] - 30);
            customValue.setText(selectedBpm[0] + " BPM");
            customValue.setContentDescription("BPM personalizado, " + selectedBpm[0] + " BPM");
            customBpm.setContentDescription("BPM personalizado, " + selectedBpm[0] + " BPM");
            updateBpmSelection(half, threeQuarter, full, customMode, bpmMode[0]);
            applySelectedBpm(song, selectedBpm[0], bpmLabel);
        };
        half.setOnClickListener(bpmChoice);
        threeQuarter.setOnClickListener(bpmChoice);
        full.setOnClickListener(bpmChoice);

        customBpm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                int bpm = clampBpm(progressValue + 30);
                customValue.setText(bpm + " BPM");
                customValue.setContentDescription("BPM personalizado, " + bpm + " BPM");
                seekBar.setContentDescription("BPM personalizado, " + bpm + " BPM");
                if (!fromUser) {
                    return;
                }
                if (practiceView != null && practiceView.isRunning()) {
                    pauseForBpmChange(state, startPause, lastPlayedPosition);
                }
                selectedBpm[0] = bpm;
                bpmMode[0] = "custom";
                updateBpmSelection(half, threeQuarter, full, customMode, bpmMode[0]);
                applySelectedBpm(song, selectedBpm[0], bpmLabel);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        practiceView.setOnTabEventListener(new GuitarPracticeView.OnTabEventListener() {
            @Override
            public void onEvent(int position, TablatureEvent event) {
                status.setText(tabEventToText(event));
                updatePracticeProgress(progress);
                updateNavigationButtons(previous, next);
                if (practiceView.isRunning() && lastPlayedPosition[0] != position) {
                    lastPlayedPosition[0] = position;
                    if (guitarSoundPlayer != null && guitarSoundPlayer.isReady()) {
                        guitarSoundPlayer.play(event);
                        state.setText("Reproduzindo");
                    } else if (guitarSoundPlayer != null) {
                        state.setText(guitarSoundPlayer.statusText());
                    }
                }
            }

            @Override
            public void onFinished() {
                if (guitarSoundPlayer != null) {
                    guitarSoundPlayer.stopAll();
                }
                startPause.setText("▶");
                startPause.setContentDescription("Continuar música");
                state.setText("Finalizado");
                updatePracticeProgress(progress);
                updateNavigationButtons(previous, next);
            }
        });

        startPause.setOnClickListener(v -> {
            if (practiceView.isRunning()) {
                practiceView.pause();
                lastPlayedPosition[0] = -1;
                if (guitarSoundPlayer != null) {
                    guitarSoundPlayer.stopAll();
                }
                state.setText("Pausado");
                startPause.setText("▶");
                startPause.setContentDescription("Continuar música");
            } else {
                if (guitarSoundPlayer == null || !guitarSoundPlayer.isReady()) {
                    state.setText(guitarSoundPlayer == null ? "Erro" : guitarSoundPlayer.statusText());
                    startPause.setText("Tentar novamente");
                    startPause.setContentDescription("Tentar novamente");
                    Toast.makeText(this, "Sons de violao ainda nao carregados.", Toast.LENGTH_SHORT).show();
                    return;
                }
                practiceView.start();
                state.setText("Reproduzindo");
                startPause.setText("⏸");
                startPause.setContentDescription("Pausar música");
            }
        });
        previous.setOnClickListener(v -> {
            if (guitarSoundPlayer != null) {
                guitarSoundPlayer.stopAll();
            }
            practiceView.moveToPreviousEvent();
            lastPlayedPosition[0] = -1;
            state.setText("Pausado");
            startPause.setText("▶");
            startPause.setContentDescription("Continuar música");
            updatePracticeProgress(progress);
            updateNavigationButtons(previous, next);
        });
        next.setOnClickListener(v -> {
            if (guitarSoundPlayer != null) {
                guitarSoundPlayer.stopAll();
            }
            practiceView.moveToNextEvent();
            lastPlayedPosition[0] = -1;
            state.setText("Pausado");
            startPause.setText("▶");
            startPause.setContentDescription("Continuar música");
            updatePracticeProgress(progress);
            updateNavigationButtons(previous, next);
        });
        reset.setOnClickListener(v -> {
            if (guitarSoundPlayer != null) {
                guitarSoundPlayer.stopAll();
            }
            lastPlayedPosition[0] = -1;
            practiceView.reset();
            updatePracticeProgress(progress);
            updateNavigationButtons(previous, next);
            state.setText(guitarSoundPlayer == null ? "Erro" : guitarSoundPlayer.statusText());
            startPause.setText(guitarSoundPlayer != null && guitarSoundPlayer.isReady() ? "▶" : "Tentar novamente");
            startPause.setContentDescription(guitarSoundPlayer != null && guitarSoundPlayer.isReady()
                    ? "Continuar música"
                    : "Tentar novamente");
        });

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void returnFromPractice() {
        stopPractice();
        if (practiceReturnScreen == Screen.LESSON && currentLesson != null) {
            showLessonDetail(currentLesson);
        } else if (practiceReturnScreen == Screen.ARPEGGIOS) {
            showArpeggios();
        } else if (practiceReturnScreen == Screen.SONGS) {
            showSongs();
        } else {
            showCourse(level);
        }
    }

    private void showArpeggios() {
        stopPractice();
        screen = Screen.ARPEGGIOS;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Arpejos", () -> showCourse(level)),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        content.addView(text("Biblioteca de arpejos", 28, DARK, true, Gravity.START, 0, 7));
        content.addView(text(
                "Escolha um acorde e pratique o padrão P–I–M–A.",
                15, TEXT, false, Gravity.START, 0, 22
        ));

        for (Arpeggio arpeggio : ARPEGGIOS) {
            TextView card = button(arpeggio.title + "\n" + patternToText(arpeggio.pattern), DARK, WHITE, 78);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(18), dp(8), dp(18), dp(8));
            card.setOnClickListener(v -> showArpeggioPractice(arpeggio));
            content.addView(card, matchWrap(0, 12));
        }

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void showArpeggioPractice(Arpeggio arpeggio) {
        stopPractice();
        screen = Screen.PRACTICE;
        practiceReturnScreen = Screen.ARPEGGIOS;
        currentLesson = null;
        root.removeAllViews();

        final int[] lastPlayedPosition = {-1};

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Modo treino", this::returnFromPractice),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        content.addView(text("Arpejo em " + arpeggio.title, 25, DARK, true, Gravity.START, 0, 4));
        content.addView(text(patternToText(arpeggio.pattern), 15, TEAL, true, Gravity.START, 0, 6));

        TextView state = text("Carregando sons...", 15, DARK, true, Gravity.CENTER, 0, 8);
        TextView bpmLabel = text("60 BPM", 17, TEAL, true, Gravity.CENTER, 0, 4);
        TextView progress = text("Nota 1 de 4", 14, TEXT, true, Gravity.CENTER, 0, 8);
        content.addView(state);
        content.addView(bpmLabel);

        practiceView = new GuitarPracticeView(this);
        practiceView.setTablatureText(arpeggio.tablature);
        practiceView.setLooping(true);
        practiceView.setBpm(60);
        practiceView.setBackground(roundRect(WHITE, 18));
        LinearLayout.LayoutParams practiceParams = new LinearLayout.LayoutParams(-1, -2);
        practiceParams.setMargins(0, dp(2), 0, dp(8));
        content.addView(practiceView, practiceParams);

        TextView status = text("Pronto para iniciar", 16, DARK, true, Gravity.CENTER, 0, 12);
        content.addView(status);
        content.addView(progress);

        SeekBar speed = new SeekBar(this);
        speed.setMax(100);
        speed.setProgress(20);
        speed.setContentDescription("Controle de velocidade entre 40 e 140 BPM");
        speed.getProgressDrawable().setColorFilter(TEAL, PorterDuff.Mode.SRC_IN);
        speed.getThumb().setColorFilter(DARK, PorterDuff.Mode.SRC_IN);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                int bpm = progressValue + 40;
                bpmLabel.setText(bpm + " BPM");
                if (practiceView != null) {
                    practiceView.setBpm(bpm);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        content.addView(speed, matchWrap(0, 12));

        guitarSoundPlayer = new GuitarSoundPlayer(this);
        state.setText(guitarSoundPlayer.statusText());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setWeightSum(3f);
        TextView previous = button("VOLTAR", SOFT, DARK, 58);
        TextView startPause = button(guitarSoundPlayer.isReady() ? "PLAY" : "TENTAR", DARK, WHITE, 58);
        TextView next = button("AVANCAR", SOFT, DARK, 58);
        previous.setTextSize(14);
        startPause.setTextSize(14);
        next.setTextSize(14);
        previous.setContentDescription("Voltar para a nota anterior");
        startPause.setContentDescription(guitarSoundPlayer.isReady() ? "Continuar arpejo" : "Tentar novamente");
        next.setContentDescription("Avancar para a proxima nota");
        controls.addView(previous, weightButton(0, 6));
        controls.addView(startPause, weightButton(6, 6));
        controls.addView(next, weightButton(6, 0));
        content.addView(controls, matchWrap(0, 12));
        updatePracticeProgress(progress);
        updateNavigationButtons(previous, next);

        practiceView.setOnTabEventListener(new GuitarPracticeView.OnTabEventListener() {
            @Override
            public void onEvent(int position, TablatureEvent event) {
                status.setText(tabEventToText(event));
                updatePracticeProgress(progress);
                updateNavigationButtons(previous, next);
                if (practiceView.isRunning() && lastPlayedPosition[0] != position) {
                    lastPlayedPosition[0] = position;
                    playCurrentPracticeEvent(state);
                }
            }

            @Override
            public void onFinished() {
            }
        });

        TextView sound = button(
                soundEnabled ? "SOM: LIGADO" : "SOM: DESLIGADO",
                SOFT, DARK, 52
        );
        sound.setOnClickListener(v -> {
            soundEnabled = !soundEnabled;
            saveActiveProfile();
            if (!soundEnabled && guitarSoundPlayer != null) {
                guitarSoundPlayer.stopAll();
            }
            sound.setText(soundEnabled ? "SOM: LIGADO" : "SOM: DESLIGADO");
        });
        content.addView(sound, matchWrap(0, 12));

        startPause.postDelayed(() -> {
            if (guitarSoundPlayer != null && guitarSoundPlayer.isReady() && practiceView != null && !practiceView.isRunning()) {
                state.setText(guitarSoundPlayer.statusText());
                startPause.setText("PLAY");
                startPause.setContentDescription("Continuar arpejo");
            }
        }, 900L);

        startPause.setOnClickListener(v -> {
            if (practiceView.isRunning()) {
                practiceView.pause();
                lastPlayedPosition[0] = -1;
                if (guitarSoundPlayer != null) {
                    guitarSoundPlayer.stopAll();
                }
                state.setText("Pausado");
                startPause.setText("PLAY");
                startPause.setContentDescription("Continuar arpejo");
            } else {
                if (guitarSoundPlayer == null || !guitarSoundPlayer.isReady()) {
                    state.setText(guitarSoundPlayer == null ? "Erro" : guitarSoundPlayer.statusText());
                    startPause.setText("TENTAR");
                    startPause.setContentDescription("Tentar novamente");
                    Toast.makeText(this, "Sons de violao ainda nao carregados.", Toast.LENGTH_SHORT).show();
                    return;
                }
                practiceView.start();
                state.setText("Reproduzindo");
                startPause.setText("PAUSAR");
                startPause.setContentDescription("Pausar arpejo");
            }
        });
        previous.setOnClickListener(v -> {
            if (guitarSoundPlayer != null) {
                guitarSoundPlayer.stopAll();
            }
            practiceView.moveToPreviousEvent();
            lastPlayedPosition[0] = practiceView.getCurrentEventIndex();
            playCurrentPracticeEvent(state);
            startPause.setText("PLAY");
            startPause.setContentDescription("Continuar arpejo");
            updatePracticeProgress(progress);
            updateNavigationButtons(previous, next);
        });
        next.setOnClickListener(v -> {
            if (guitarSoundPlayer != null) {
                guitarSoundPlayer.stopAll();
            }
            practiceView.moveToNextEvent();
            lastPlayedPosition[0] = practiceView.getCurrentEventIndex();
            playCurrentPracticeEvent(state);
            startPause.setText("PLAY");
            startPause.setContentDescription("Continuar arpejo");
            updatePracticeProgress(progress);
            updateNavigationButtons(previous, next);
        });

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void showSongs() {
        stopPractice();
        screen = Screen.SONGS;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(TEAL, Gravity.TOP);
        shell.addView(pageHeader("Repertório", () -> showCourse(level)),
                new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout content = column(TEAL, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        EditText search = input("", "Pesquise uma prática");
        search.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_search, 0);
        content.addView(search, matchWrap(0, 16));
        content.addView(text(
                "Recomendadas para você, " + firstName() + "!",
                17, WHITE, true, Gravity.START, 0, 12
        ));

        ScrollView resultsScroll = new ScrollView(this);
        LinearLayout results = column(TEAL, Gravity.TOP);
        renderSongs(results, "");
        resultsScroll.addView(results);
        content.addView(resultsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderSongs(results, s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void renderSongs(LinearLayout results, String query) {
        results.removeAllViews();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        boolean useLocalSongs = true;
        if (useLocalSongs) {
            int shown = 0;
            for (Song song : songRepository.publishedQueued()) {
                if (!normalized.isEmpty() && !song.getTitle().toLowerCase(Locale.ROOT).contains(normalized)) {
                    continue;
                }
                TextView card = songCard(
                        song.getTitle(),
                        song.getDifficulty(),
                        song.getArtist() + "  •  " + song.getRecommendedBpm() + " BPM recomendado"
                );
                card.setOnClickListener(v -> showSongPractice(song));
                results.addView(card, matchWrap(0, 10));
                shown++;
            }
            if (shown == 0) {
                results.addView(text(
                        "Nenhuma musica publicada encontrada.",
                        16, WHITE, true, Gravity.CENTER, 28, 0
                ));
            }
            return;
        }
        String[] titles = {
                "Asa Branca – estudo de progressão",
                "Romance Anônimo – estudo lento",
                "Estudo em Dó – arpejo básico",
                "Valsa de prática – compasso ternário",
                "Exercício em Sol – baixos alternados"
        };
        String[] levels = {"Iniciante", "Intermediário", "Iniciante", "Intermediário", "Intermediário"};
        String[] chords = {"C – F – G", "Am – E", "C", "Am – Dm – E", "G – C – D"};
        int[][] patterns = {
                {5, 3, 2, 1, 2, 3},
                {5, 3, 2, 1, 2, 3},
                {5, 3, 2, 1},
                {5, 3, 1, 2, 3, 2},
                {6, 3, 2, 1, 5, 3, 2, 1}
        };
        int[] bpms = {58, 62, 56, 72, 70};
        int shown = 0;

        for (int i = 0; i < titles.length; i++) {
            if (!normalized.isEmpty() && !titles[i].toLowerCase(Locale.ROOT).contains(normalized)) {
                continue;
            }
            final String title = titles[i];
            final String chord = chords[i];
            final int[] pattern = patterns[i];
            final int bpm = bpms[i];
            TextView card = songCard(title, levels[i], chord);
            card.setOnClickListener(v -> showPractice(
                    title, chord, pattern, bpm, null, Screen.SONGS
            ));
            results.addView(card, matchWrap(0, 10));
            shown++;
        }
        if (shown == 0) {
            results.addView(text(
                    "Nenhuma prática encontrada.",
                    16, WHITE, true, Gravity.CENTER, 28, 0
            ));
        }
    }

    private void showAdminSongs() {
        stopPractice();
        if (!isAdmin()) {
            showCourse(level);
            return;
        }
        screen = Screen.ADMIN_SONGS;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Administração", () -> showCourse(level)),
                new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        TextView newSong = button("NOVA MÚSICA", DARK, WHITE, 56);
        content.addView(newSong, matchWrap(0, 12));
        EditText search = input("", "Pesquisar por título");
        content.addView(search, matchWrap(0, 12));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setWeightSum(3f);
        TextView all = button("TODAS", SOFT, DARK, 46);
        TextView published = button("PUBLICADAS", SOFT, DARK, 46);
        TextView drafts = button("RASCUNHOS", SOFT, DARK, 46);
        filters.addView(all, weightButton(0, 4));
        filters.addView(published, weightButton(4, 4));
        filters.addView(drafts, weightButton(4, 0));
        content.addView(filters, matchWrap(0, 12));

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = column(WHITE, Gravity.TOP);
        final String[] statusFilter = {""};
        renderAdminSongs(results, "", statusFilter[0]);
        scroll.addView(results);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());

        newSong.setOnClickListener(v -> showSongEditor(null));
        all.setOnClickListener(v -> {
            statusFilter[0] = "";
            renderAdminSongs(results, search.getText().toString(), statusFilter[0]);
        });
        published.setOnClickListener(v -> {
            statusFilter[0] = Song.PUBLISHED;
            renderAdminSongs(results, search.getText().toString(), statusFilter[0]);
        });
        drafts.setOnClickListener(v -> {
            statusFilter[0] = Song.DRAFT;
            renderAdminSongs(results, search.getText().toString(), statusFilter[0]);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderAdminSongs(results, s.toString(), statusFilter[0]);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void renderAdminSongs(LinearLayout results, String query, String statusFilter) {
        results.removeAllViews();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        for (Song song : songRepository.all()) {
            if (!normalized.isEmpty() && !song.getTitle().toLowerCase(Locale.ROOT).contains(normalized)) {
                continue;
            }
            if (!statusFilter.isEmpty() && !statusFilter.equals(song.getStatus())) {
                continue;
            }
            LinearLayout card = column(SOFT, Gravity.TOP);
            card.setPadding(dp(14), dp(12), dp(14), dp(14));
            card.setBackground(roundRect(SOFT, 14));
            card.addView(text(
                    song.getQueueOrder() + ". " + song.getTitle(),
                    17, DARK, true, Gravity.START, 0, 6
            ));
            card.addView(text(
                    song.getArtist() + " • " + song.getDifficulty() + " • " + song.getRecommendedBpm() + " BPM",
                    14, TEXT, false, Gravity.START, 0, 7
            ));
            card.addView(text(
                    song.getStatus() + (song.isInQueue() ? " • na fila" : " • fora da fila"),
                    13, TEAL, true, Gravity.START, 0, 10
            ));

            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setWeightSum(2f);
            TextView edit = button("EDITAR", DARK, WHITE, 44);
            TextView publish = button(Song.PUBLISHED.equals(song.getStatus()) ? "DESPUBLICAR" : "PUBLICAR", CYAN, WHITE, 44);
            row1.addView(edit, weightButton(0, 5));
            row1.addView(publish, weightButton(5, 0));
            card.addView(row1, matchWrap(8, 8));

            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setWeightSum(3f);
            TextView up = button("SUBIR", SOFT, DARK, 42);
            TextView down = button("DESCER", SOFT, DARK, 42);
            TextView queue = button(song.isInQueue() ? "REMOVER FILA" : "ADICIONAR FILA", SOFT, DARK, 42);
            row2.addView(up, weightButton(0, 4));
            row2.addView(down, weightButton(4, 4));
            row2.addView(queue, weightButton(4, 0));
            card.addView(row2, matchWrap(0, 8));

            LinearLayout row3 = new LinearLayout(this);
            row3.setOrientation(LinearLayout.HORIZONTAL);
            row3.setWeightSum(2f);
            TextView archive = button("ARQUIVAR", PALE, DARK, 42);
            TextView delete = button("EXCLUIR", Color.rgb(210, 75, 75), WHITE, 42);
            row3.addView(archive, weightButton(0, 5));
            row3.addView(delete, weightButton(5, 0));
            card.addView(row3, matchWrap(0, 0));

            edit.setOnClickListener(v -> showSongEditor(song));
            publish.setOnClickListener(v -> {
                try {
                    TablatureParser.parse(song.getTablature(), song.getRecommendedBpm(), TablatureParser.DEFAULT_COLUMNS_PER_BEAT);
                    songRepository.save(song.withStatus(Song.PUBLISHED.equals(song.getStatus()) ? Song.DRAFT : Song.PUBLISHED));
                    showAdminSongs();
                } catch (IllegalArgumentException ex) {
                    Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            up.setOnClickListener(v -> {
                songRepository.save(song.withQueue(Math.max(1, song.getQueueOrder() - 1), song.isInQueue()));
                showAdminSongs();
            });
            down.setOnClickListener(v -> {
                songRepository.save(song.withQueue(song.getQueueOrder() + 1, song.isInQueue()));
                showAdminSongs();
            });
            queue.setOnClickListener(v -> {
                songRepository.save(song.withQueue(song.getQueueOrder(), !song.isInQueue()));
                showAdminSongs();
            });
            archive.setOnClickListener(v -> {
                songRepository.save(song.withStatus(Song.ARCHIVED));
                showAdminSongs();
            });
            delete.setOnClickListener(v -> confirmDeleteSong(song));
            results.addView(card, matchWrap(0, 12));
        }
    }

    private void confirmDeleteSong(Song song) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir música")
                .setMessage("Deseja excluir definitivamente \"" + song.getTitle() + "\"?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    songRepository.delete(song.getId());
                    showAdminSongs();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showSongEditor(Song song) {
        if (!isAdmin()) {
            showCourse(level);
            return;
        }
        screen = Screen.ADMIN_EDITOR;
        root.removeAllViews();
        boolean editing = song != null;

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader(editing ? "Editar música" : "Nova música", this::showAdminSongs),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        EditText title = input(editing ? song.getTitle() : "", "Título");
        EditText artist = input(editing ? song.getArtist() : "", "Artista ou autor");
        EditText difficulty = input(editing ? song.getDifficulty() : "Iniciante", "Dificuldade");
        EditText bpm = input(editing ? String.valueOf(song.getRecommendedBpm()) : "", "BPM recomendado");
        bpm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText tablature = input(editing ? song.getTablature() : "", "Cole a tablatura ASCII");
        tablature.setSingleLine(false);
        tablature.setMinLines(8);
        tablature.setHorizontallyScrolling(true);
        tablature.setTypeface(Typeface.MONOSPACE);
        tablature.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        content.addView(title, matchWrap(0, 10));
        content.addView(artist, matchWrap(0, 10));
        content.addView(difficulty, matchWrap(0, 10));
        content.addView(bpm, matchWrap(0, 10));
        content.addView(tablature, matchWrap(0, 12));

        TextView preview = text("Prévia ainda não gerada", 13, TEXT, false, Gravity.START, 0, 10);
        preview.setTypeface(Typeface.MONOSPACE);
        HorizontalScrollView previewScroll = new HorizontalScrollView(this);
        previewScroll.addView(preview);
        content.addView(previewScroll, matchWrap(0, 12));

        TextView previewButton = button("VISUALIZAR INTERPRETAÇÃO", SOFT, DARK, 52);
        TextView saveDraft = button("SALVAR RASCUNHO", PALE, DARK, 52);
        TextView publish = button("PUBLICAR", DARK, WHITE, 56);
        content.addView(previewButton, matchWrap(0, 10));
        content.addView(saveDraft, matchWrap(0, 10));
        content.addView(publish, matchWrap(0, 0));

        previewButton.setOnClickListener(v -> updatePreview(preview, tablature.getText().toString(), parseBpmOrZero(bpm)));
        saveDraft.setOnClickListener(v -> saveSongFromEditor(song, title, artist, difficulty, bpm, tablature, Song.DRAFT));
        publish.setOnClickListener(v -> saveSongFromEditor(song, title, artist, difficulty, bpm, tablature, Song.PUBLISHED));

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void updatePreview(TextView preview, String tab, int bpm) {
        try {
            List<TablatureEvent> events = TablatureParser.parse(tab, bpm, TablatureParser.DEFAULT_COLUMNS_PER_BEAT);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < events.size(); i++) {
                TablatureEvent event = events.get(i);
                builder.append(i + 1)
                        .append(" | coluna ")
                        .append(event.getColumn())
                        .append(" | ")
                        .append(event.isChord() ? "acorde" : "nota")
                        .append(" | ")
                        .append(event.getInstantMs())
                        .append("ms");
                for (TablatureEvent.Note note : event.getNotes()) {
                    builder.append("\n  ")
                            .append(note.getStringName())
                            .append(" corda ")
                            .append(note.getStringIndex() + 1)
                            .append(" casa ")
                            .append(note.getFret())
                            .append(" = ")
                            .append(note.getMusicalNote());
                }
                builder.append("\n");
            }
            preview.setText(builder.toString());
        } catch (IllegalArgumentException ex) {
            preview.setText("Erro: " + ex.getMessage());
        }
    }

    private void saveSongFromEditor(
            Song existing,
            EditText title,
            EditText artist,
            EditText difficulty,
            EditText bpmInput,
            EditText tablature,
            String status
    ) {
        String titleValue = title.getText().toString().trim();
        int bpm = parseBpmOrZero(bpmInput);
        if (titleValue.isEmpty()) {
            title.setError("Informe o título.");
            return;
        }
        if (bpm <= 0) {
            bpmInput.setError("Informe o BPM recomendado.");
            return;
        }
        if (Song.PUBLISHED.equals(status)) {
            try {
                TablatureParser.parse(tablature.getText().toString(), bpm, TablatureParser.DEFAULT_COLUMNS_PER_BEAT);
            } catch (IllegalArgumentException ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        }
        long now = System.currentTimeMillis();
        Song song = new Song(
                existing == null ? "song_" + now : existing.getId(),
                titleValue,
                artist.getText().toString().trim(),
                difficulty.getText().toString().trim().isEmpty()
                        ? "Iniciante"
                        : difficulty.getText().toString().trim(),
                bpm,
                tablature.getText().toString(),
                existing == null ? songRepository.nextQueueOrder() : existing.getQueueOrder(),
                existing == null || existing.isInQueue(),
                status,
                existing == null ? now : existing.getCreatedAt(),
                now
        );
        songRepository.save(song);
        showAdminSongs();
    }

    private int parseBpmOrZero(EditText input) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void showProgress() {
        stopPractice();
        screen = Screen.PROGRESS;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Progresso", () -> showCourse(level)),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        content.addView(text("Sua evolução", 29, DARK, true, Gravity.START, 0, 7));
        content.addView(text(
                "O progresso fica salvo neste aparelho e funciona sem internet.",
                15, TEXT, false, Gravity.START, 0, 24
        ));

        addLevelProgress(content, LessonRepository.BEGINNER);
        addLevelProgress(content, LessonRepository.INTERMEDIATE);

        content.addView(text("Aulas concluídas", 21, DARK, true, Gravity.START, 12, 10));
        boolean anyCompleted = false;
        for (Lesson lesson : LessonRepository.getAll()) {
            if (progressStore.isCompleted(lesson.getId())) {
                anyCompleted = true;
                TextView completed = text(
                        "✓  " + lesson.getLevel() + " • Aula " + lesson.getOrder() + "\n    " + lesson.getTitle(),
                        15, DARK, true, Gravity.START, 0, 0
                );
                completed.setPadding(dp(14), dp(12), dp(14), dp(12));
                completed.setBackground(roundRect(SOFT, 14));
                completed.setOnClickListener(v -> showLessonDetail(lesson));
                content.addView(completed, matchWrap(0, 9));
            }
        }
        if (!anyCompleted) {
            content.addView(text(
                    "Conclua uma aula para vê-la aqui.",
                    15, TEXT, false, Gravity.START, 0, 16
            ));
        }

        TextView restore = button("RESTAURAR PROGRESSO INICIAL", WHITE, TEAL, 56);
        restore.setBackground(pressable(WHITE, SOFT, 16, TEAL));
        restore.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Restaurar progresso?")
                .setMessage("As conclusões serão removidas e apenas a primeira aula ficará concluída, como no protótipo original.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Restaurar", (dialog, which) -> {
                    progressStore.restoreInitialState();
                    syncAllProgress(true);
                    showProgress();
                })
                .show());
        content.addView(restore, matchWrap(18, 0));

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void addLevelProgress(LinearLayout content, String selectedLevel) {
        List<Lesson> lessons = LessonRepository.getLessons(selectedLevel);
        int completed = progressStore.completedCount(lessons);
        int percent = progressStore.progressPercent(lessons);
        LinearLayout card = column(SOFT, Gravity.TOP);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(roundRect(SOFT, 16));
        card.addView(text(selectedLevel, 18, DARK, true, Gravity.START, 0, 5));
        card.addView(text(
                completed + " de " + lessons.size() + " aulas",
                14, TEXT, false, Gravity.START, 0, 9
        ));
        card.addView(horizontalProgress(percent), new LinearLayout.LayoutParams(-1, dp(13)));
        card.addView(text(percent + "%", 13, TEAL, true, Gravity.END, 6, 0));
        card.setOnClickListener(v -> showCourse(selectedLevel));
        content.addView(card, matchWrap(0, 14));
    }

    private void showAbout() {
        stopPractice();
        screen = Screen.ABOUT;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(TEAL, Gravity.TOP);
        shell.addView(pageHeader("Sobre", () -> showCourse(level)),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(TEAL, Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(28), dp(28), dp(28), dp(36));
        content.addView(logo(156));
        content.addView(text("Dedilharte", 31, WHITE, true, Gravity.CENTER, 2, 8));
        content.addView(text(
                "Aprendizado visual e guiado de dedilhado no violão.",
                17, WHITE, true, Gravity.CENTER, 0, 28
        ));
        content.addView(infoCard(
                "SOBRE O PROJETO",
                "Aplicativo educacional desenvolvido como Trabalho de Conclusão de Curso. O conteúdo foi pensado para iniciantes e estudantes intermediários praticarem cordas, arpejos, coordenação e ritmo."
        ), matchWrap(0, 14));
        content.addView(infoCard(
                "TECNOLOGIA",
                "Aplicativo Android nativo em Java, com funcionamento offline, armazenamento local do progresso e compatibilidade a partir do Android 7.0."
        ), matchWrap(0, 14));
        content.addView(infoCard(
                "ACESSIBILIDADE",
                "Textos em português, controles grandes, contraste alto e treino em velocidade ajustável."
        ), matchWrap(0, 20));
        content.addView(text("Versão 1.0 • © Dedilharte", 13, WHITE, false, Gravity.CENTER, 0, 0));

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private LinearLayout mainHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), 0, dp(12), 0);
        bar.setBackgroundColor(DARK);

        TextView menu = text("☰", 38, WHITE, false, Gravity.CENTER, 0, 0);
        menu.setContentDescription("Abrir menu");
        menu.setOnClickListener(v -> openDrawer());
        bar.addView(menu, new LinearLayout.LayoutParams(dp(58), -1));

        TextView title = text("Dedilharte", 19, WHITE, true, Gravity.CENTER_VERTICAL, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        ImageView miniLogo = logo(50);
        miniLogo.setContentDescription("Logo do Dedilharte");
        bar.addView(miniLogo, new LinearLayout.LayoutParams(dp(54), dp(54)));
        return bar;
    }

    private LinearLayout pageHeader(String titleValue, Runnable backAction) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(14), 0);
        bar.setBackgroundColor(DARK);

        TextView back = text("‹", 46, WHITE, false, Gravity.CENTER, 0, 0);
        back.setContentDescription("Voltar");
        back.setOnClickListener(v -> backAction.run());
        bar.addView(back, new LinearLayout.LayoutParams(dp(58), -1));

        TextView title = text(titleValue, 19, WHITE, true, Gravity.CENTER_VERTICAL, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        return bar;
    }

    private void openDrawer() {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
        FrameLayout backdrop = new FrameLayout(this);
        backdrop.setBackgroundColor(Color.argb(135, 0, 0, 0));

        LinearLayout drawer = (LinearLayout) getLayoutInflater().inflate(R.layout.drawer_profile, backdrop, false);
        ImageView avatar = drawer.findViewById(R.id.drawerAvatar);
        TextView name = drawer.findViewById(R.id.drawerUserName);
        TextView currentLevel = drawer.findViewById(R.id.drawerLevel);
        TextView editLink = drawer.findViewById(R.id.drawerEditLink);
        TextView beginner = drawer.findViewById(R.id.drawerBeginner);
        TextView intermediate = drawer.findViewById(R.id.drawerIntermediate);
        TextView arpeggios = drawer.findViewById(R.id.drawerArpeggios);
        TextView songs = drawer.findViewById(R.id.drawerSongs);
        TextView admin = drawer.findViewById(R.id.drawerAdmin);
        View adminDivider = drawer.findViewById(R.id.drawerAdminDivider);
        TextView editProfile = drawer.findViewById(R.id.drawerEditProfile);
        TextView logout = drawer.findViewById(R.id.drawerLogout);
        TextView deleteAccount = drawer.findViewById(R.id.drawerDeleteAccount);

        bindProfilePhoto(avatar);
        name.setText(userName);
        currentLevel.setText(level);
        editLink.setPaintFlags(editLink.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);

        editLink.setOnClickListener(v -> {
            dialog.dismiss();
            showEditProfile();
        });
        beginner.setOnClickListener(v -> {
            dialog.dismiss();
            showCourse(LessonRepository.BEGINNER);
        });
        intermediate.setOnClickListener(v -> {
            dialog.dismiss();
            showCourse(LessonRepository.INTERMEDIATE);
        });
        arpeggios.setOnClickListener(v -> {
            dialog.dismiss();
            showArpeggios();
        });
        songs.setOnClickListener(v -> {
            dialog.dismiss();
            showSongs();
        });
        admin.setVisibility(isAdmin() ? View.VISIBLE : View.GONE);
        adminDivider.setVisibility(isAdmin() ? View.VISIBLE : View.GONE);
        admin.setOnClickListener(v -> {
            dialog.dismiss();
            showAdminSongs();
        });
        editProfile.setOnClickListener(v -> {
            dialog.dismiss();
            showEditProfile();
        });
        logout.setOnClickListener(v -> {
            dialog.dismiss();
            logout();
        });
        deleteAccount.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteAccount();
        });

        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * .72f),
                -1,
                Gravity.START
        );
        backdrop.addView(drawer, drawerParams);
        backdrop.setOnClickListener(v -> dialog.dismiss());
        drawer.setOnClickListener(v -> {
        });
        dialog.setContentView(backdrop);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        if (window != null) {
            window.setLayout(-1, -1);
        }
    }
    private void showEditProfile() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_edit_profile, null, false);
        TextView close = panel.findViewById(R.id.editClose);
        ImageView avatar = panel.findViewById(R.id.editAvatar);
        TextView photoButton = panel.findViewById(R.id.editPhotoButton);
        EditText name = panel.findViewById(R.id.editName);
        TextView save = panel.findViewById(R.id.editSave);
        TextView cancel = panel.findViewById(R.id.editCancel);

        profilePhotoPreview = avatar;
        bindProfilePhoto(profilePhotoPreview);
        name.setText(userName);

        close.setOnClickListener(v -> dialog.dismiss());
        photoButton.setOnClickListener(v -> openProfilePhotoPicker());
        save.setOnClickListener(v -> {
            String typed = name.getText().toString().trim();
            if (typed.isEmpty()) {
                name.setError("Digite seu nome");
                return;
            }
            userName = typed;
            markActiveUserUpdated();
            saveActiveProfile();
            syncActiveUser();
            dialog.dismiss();
            showCourse(level);
        });
        cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        if (window != null) {
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * .94f),
                    -2
            );
        }
    }
    private void openProfilePhotoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PROFILE_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROFILE_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri selectedPhoto = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    selectedPhoto,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
        }
        profilePhotoUri = selectedPhoto.toString();
        saveActiveProfile();
        if (profilePhotoPreview != null) {
            bindProfilePhoto(profilePhotoPreview);
        }
    }
    private TextView lessonCard(Lesson lesson) {
        boolean completed = progressStore.isCompleted(lesson.getId());
        String status = completed ? "CONCLUÍDA ✓" : "NÃO CONCLUÍDA";
        TextView card = text(
                lesson.getOrder() + "ª AULA  •  " + status + "\n" + lesson.getTitle(),
                17,
                WHITE,
                true,
                Gravity.CENTER_VERTICAL,
                0,
                0
        );
        card.setPadding(dp(17), dp(12), dp(17), dp(12));
        card.setMinHeight(dp(88));
        card.setContentDescription(
                "Aula " + lesson.getOrder() + ", " + lesson.getTitle() + ", " + status
        );
        card.setBackground(pressable(completed ? PALE : CYAN, DARK, 16, WHITE));
        return card;
    }

    private TextView songCard(String title, String songLevel, String chord) {
        TextView card = text(
                title + "\n" + songLevel + "  •  " + chord,
                16, WHITE, true, Gravity.CENTER_VERTICAL, 0, 0
        );
        card.setPadding(dp(16), dp(11), dp(16), dp(11));
        card.setMinHeight(dp(76));
        card.setBackground(pressable(DARK, CYAN, 15, WHITE));
        return card;
    }

    private View infoCard(String heading, String body) {
        LinearLayout card = column(SOFT, Gravity.TOP);
        card.setPadding(dp(17), dp(15), dp(17), dp(16));
        card.setBackground(roundRect(SOFT, 15));
        card.addView(text(heading, 13, TEAL, true, Gravity.START, 0, 6));
        card.addView(text(body, 15, TEXT, false, Gravity.START, 0, 0));
        return card;
    }

    private View stepCard(int number, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        row.setBackground(roundRect(SOFT, 14));

        TextView numberView = text(
                String.valueOf(number), 15, WHITE, true, Gravity.CENTER, 0, 0
        );
        numberView.setBackground(roundRect(TEAL, 24));
        row.addView(numberView, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView body = text(value, 15, TEXT, false, Gravity.START, 0, 0);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, -2, 1);
        bodyParams.setMargins(dp(12), 0, 0, 0);
        row.addView(body, bodyParams);
        return row;
    }

    private TextView statusPill(String label, int color) {
        TextView pill = text(label, 12, WHITE, true, Gravity.CENTER, 0, 0);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setBackground(roundRect(color, 20));
        return pill;
    }

    private ProgressBar horizontalProgress(int percent) {
        ProgressBar progress = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal
        );
        progress.setMax(100);
        progress.setProgress(percent);
        progress.getProgressDrawable().setColorFilter(CYAN, PorterDuff.Mode.SRC_IN);
        progress.setContentDescription(percent + "% concluído");
        return progress;
    }

    private Lesson firstIncomplete(List<Lesson> lessons) {
        for (Lesson lesson : lessons) {
            if (!progressStore.isCompleted(lesson.getId())) {
                return lesson;
            }
        }
        return null;
    }

    private void bindProfilePhoto(ImageView image) {
        image.clearColorFilter();
        if (profilePhotoUri == null || profilePhotoUri.trim().isEmpty()) {
            image.setImageResource(android.R.drawable.ic_menu_myplaces);
            image.setColorFilter(DARK);
            return;
        }
        try {
            InputStream stream = getContentResolver().openInputStream(Uri.parse(profilePhotoUri));
            Bitmap source = BitmapFactory.decodeStream(stream);
            if (stream != null) {
                stream.close();
            }
            if (source == null) {
                throw new RuntimeException("Foto inválida");
            }
            image.setImageBitmap(circleCrop(source));
        } catch (Exception ignored) {
            image.setImageResource(android.R.drawable.ic_menu_myplaces);
            image.setColorFilter(DARK);
        }
    }

    private Bitmap circleCrop(Bitmap source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        Bitmap squared = Bitmap.createBitmap(source, x, y, size, size);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(squared, new Rect(0, 0, size, size), new Rect(0, 0, size, size), paint);
        return output;
    }

    private ImageView logo(int size) {
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.logo_branca_of);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setAdjustViewBounds(true);
        image.setContentDescription("Dedilharte");
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return image;
    }

    private LinearLayout column(int background, int gravity) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(gravity);
        layout.setBackgroundColor(background);
        return layout;
    }

    private TextView text(
            String value,
            int size,
            int color,
            boolean bold,
            int gravity,
            int top,
            int bottom
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(gravity);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setPadding(0, dp(top), 0, dp(bottom));
        return view;
    }

    private TextView button(String label, int background, int foreground, int height) {
        TextView view = text(label, 17, foreground, true, Gravity.CENTER, 0, 0);
        view.setBackground(pressable(background, DARK, 16, foreground));
        view.setMinHeight(dp(height));
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription(label);
        return view;
    }

    private EditText input(String value, String hint) {
        EditText input = new EditText(this);
        input.setTextColor(DARK);
        input.setHintTextColor(Color.GRAY);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundRect(WHITE, 15));
        input.setMinHeight(dp(54));
        input.setHint(hint);
        input.setText(value);
        return input;
    }

    private StateListDrawable pressable(int normal, int pressed, int radius, int strokeColor) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_pressed},
                borderedRect(pressed, radius, strokeColor, 0)
        );
        states.addState(
                new int[]{android.R.attr.state_focused},
                borderedRect(normal, radius, strokeColor, 2)
        );
        states.addState(new int[]{}, borderedRect(normal, radius, strokeColor, 0));
        return states;
    }

    private GradientDrawable borderedRect(
            int color,
            int radius,
            int strokeColor,
            int strokeWidth
    ) {
        GradientDrawable drawable = roundRect(color, radius);
        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(top), 0, dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams wrapStart(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, dp(top), 0, dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams weightButton(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(left), 0, dp(right), 0);
        return params;
    }

    private FrameLayout.LayoutParams matchMatch() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int clampBpm(int bpm) {
        return Math.max(30, Math.min(240, bpm));
    }

    private void applySelectedBpm(Song song, int bpm, TextView bpmLabel) {
        if (practiceView != null) {
            practiceView.setBpm(bpm);
        }
        profile.edit().putInt("song_bpm_" + song.getId(), bpm).apply();
        bpmLabel.setText("BPM selecionado: " + bpm);
    }

    private void pauseForBpmChange(TextView state, TextView startPause, int[] lastPlayedPosition) {
        if (practiceView != null) {
            practiceView.pause();
        }
        if (guitarSoundPlayer != null) {
            guitarSoundPlayer.stopAll();
        }
        lastPlayedPosition[0] = -1;
        state.setText("Pausado");
        startPause.setText("▶");
        startPause.setContentDescription("Continuar música");
    }

    private void playCurrentPracticeEvent(TextView state) {
        if (practiceView == null || guitarSoundPlayer == null) {
            return;
        }
        if (!soundEnabled) {
            state.setText("Som desligado");
            return;
        }
        TablatureEvent event = practiceView.getCurrentTabEvent();
        if (guitarSoundPlayer.isReady()) {
            guitarSoundPlayer.stopAll();
            guitarSoundPlayer.play(event);
            state.setText("Reproduzindo");
        } else {
            state.setText(guitarSoundPlayer.statusText());
        }
    }

    private void updatePracticeProgress(TextView progress) {
        if (practiceView == null || practiceView.getEventCount() == 0) {
            progress.setText("Nota 0 de 0");
            return;
        }
        TablatureEvent event = practiceView.getCurrentTabEvent();
        String label = event != null && event.isChord() ? "Acorde " : "Nota ";
        progress.setText(label + (practiceView.getCurrentEventIndex() + 1) + " de " + practiceView.getEventCount());
    }

    private void updateNavigationButtons(TextView previous, TextView next) {
        if (practiceView == null || practiceView.getEventCount() == 0) {
            setNavigationEnabled(previous, false);
            setNavigationEnabled(next, false);
            return;
        }
        if (practiceView.isLooping()) {
            setNavigationEnabled(previous, true);
            setNavigationEnabled(next, true);
            return;
        }
        setNavigationEnabled(previous, practiceView.getCurrentEventIndex() > 0);
        setNavigationEnabled(next, practiceView.getCurrentEventIndex() < practiceView.getEventCount() - 1);
    }

    private void setNavigationEnabled(TextView button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : .38f);
    }

    private void updateBpmSelection(
            TextView half,
            TextView threeQuarter,
            TextView full,
            TextView custom,
            String selected
    ) {
        styleBpmOption(half, "50".equals(selected));
        styleBpmOption(threeQuarter, "75".equals(selected));
        styleBpmOption(full, "100".equals(selected));
        styleBpmOption(custom, "custom".equals(selected));
    }

    private void styleBpmOption(TextView view, boolean selected) {
        view.setTextColor(selected ? WHITE : DARK);
        view.setBackground(roundRect(selected ? TEAL : SOFT, 18));
        view.setSelected(selected);
    }

    private String firstName() {
        return userName.trim().isEmpty()
                ? "Estudante"
                : userName.trim().split("\\s+")[0];
    }

    private boolean isAdmin() {
        return ROLE_ADMIN.equals(userRole);
    }

    private String patternToText(int[] pattern) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pattern.length; i++) {
            if (i > 0) {
                builder.append(" – ");
            }
            builder.append(pattern[i]);
        }
        return builder.toString();
    }

    private String tabEventToText(com.example.dedilharte.model.TablatureEvent event) {
        if (event == null || event.getNotes().isEmpty()) {
            return "Sem nota selecionada";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(event.getNotes().size() > 1 ? "Acorde: " : "Nota: ");
        for (int i = 0; i < event.getNotes().size(); i++) {
            com.example.dedilharte.model.TablatureEvent.Note note = event.getNotes().get(i);
            if (i > 0) {
                builder.append(" + ");
            }
            builder.append(note.getStringName())
                    .append(" casa ")
                    .append(note.getFret())
                    .append(" (")
                    .append(note.getMusicalNote())
                    .append(")");
        }
        return builder.toString();
    }

    private String fingerForString(int stringNumber) {
        if (stringNumber >= 4) {
            return "Polegar (P)";
        }
        if (stringNumber == 3) {
            return "Indicador (I)";
        }
        if (stringNumber == 2) {
            return "Médio (M)";
        }
        return "Anelar (A)";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void stopPractice() {
        if (practiceView != null) {
            practiceView.pause();
            practiceView = null;
        }
        if (guitarSoundPlayer != null) {
            guitarSoundPlayer.release();
            guitarSoundPlayer = null;
        }
    }

    @Override
    public void onBackPressed() {
        switch (screen) {
            case WELCOME:
            case COURSE:
                super.onBackPressed();
                break;
            case LEVEL:
                showWelcome();
                break;
            case LESSON:
                showCourse(level);
                break;
            case PRACTICE:
                returnFromPractice();
                break;
            case ARPEGGIOS:
            case SONGS:
            case PROGRESS:
            case ABOUT:
            case ADMIN_SONGS:
            default:
                if (isAdmin()) {
                    showAdminSongs();
                } else {
                    showCourse(level);
                }
                break;
            case ADMIN_EDITOR:
                showAdminSongs();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        stopPractice();
        super.onDestroy();
    }
}



