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
import android.widget.Switch;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.audio.GuitarSoundPlayer;
import com.example.dedilharte.data.ActivityStore;
import com.example.dedilharte.data.DashboardMetrics;
import com.example.dedilharte.data.DashboardRepository;
import com.example.dedilharte.data.ArpeggioRepository;
import com.example.dedilharte.data.LessonRepository;
import com.example.dedilharte.data.ProgressStore;
import com.example.dedilharte.data.SongProgressStore;
import com.example.dedilharte.data.SongRepository;
import com.example.dedilharte.data.StudentMetrics;
import com.example.dedilharte.data.ThemeStore;
import com.example.dedilharte.data.TablatureParser;
import com.example.dedilharte.model.Lesson;
import com.example.dedilharte.model.Song;
import com.example.dedilharte.model.TablatureEvent;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.model.LearningModuleItemResponse;
import com.example.dedilharte.network.model.LearningModuleItemRequest;
import com.example.dedilharte.network.model.LearningModuleListResponse;
import com.example.dedilharte.network.model.LearningModuleRequest;
import com.example.dedilharte.network.model.LearningModuleResponse;
import com.example.dedilharte.network.model.ManagedUserRequest;
import com.example.dedilharte.network.model.ModuleProgressRequest;
import com.example.dedilharte.network.model.ArpeggioNote;
import com.example.dedilharte.network.model.ArpeggioRequest;
import com.example.dedilharte.network.model.ArpeggioResponse;
import com.example.dedilharte.network.model.RoleUpdateRequest;
import com.example.dedilharte.network.model.SongProgressResponse;
import com.example.dedilharte.network.model.UserListResponse;
import com.example.dedilharte.network.model.UserResponse;
import com.example.dedilharte.sync.DedilharteSyncManager;
import com.example.dedilharte.view.GuitarPracticeView;
import com.example.dedilharte.view.ProgressDashboardView;
import com.example.dedilharte.view.MaterialIcons;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Aplicativo educacional offline para o ensino de dedilhado no violão.
 *oi
 * Compatibilidade mínima: Android 7.0 (API 24).
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PROFILE_PHOTO = 42;
    private static final String ACTIVE_USER_ID = "active_user_id";
    private static final String ACCOUNT_IDS = "account_ids";
    private static final String USER_PREFIX = "user_";
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_CONFIGURATOR = "configurator";
    private static final String ROLE_STUDENT = "student";
    private static final String MODULE_TRIVIA_BEGINNER = "module_trivia_beginner";
    private static final String MODULE_TRIVIA_INTERMEDIATE = "module_trivia_intermediate";
    private int TEAL;
    private int DARK;
    private int CYAN;
    private int PALE;
    private int SOFT;
    private int TEXT;
    private static final int WHITE = Color.WHITE;
    private int PAGE, PAGE_TEXT, PAGE_BODY, PAGE_ACCENT;

    private static final class Arpeggio {
        private final String id;
        private final String title;
        private final String chord;
        private final int[][] notes;
        private final int[] pattern;
        private final String tablature;
        private final String difficulty;

        private Arpeggio(String title, int[][] notes) {
            this("", title, "", notes, Song.EASY);
        }

        private Arpeggio(String id, String title, String chord, int[][] notes, String difficulty) {
            this.id = id;
            this.title = title;
            this.chord = chord;
            this.notes = notes;
            this.pattern = patternFromNotes(notes);
            this.tablature = tablatureFromNotes(notes);
            this.difficulty = Song.normalizeDifficulty(difficulty);
        }
    }

    private static Arpeggio arpeggioFromResponse(ArpeggioResponse response) {
        int[][] notes = new int[response.notes == null ? 0 : response.notes.size()][2];
        for (int i = 0; i < notes.length; i++) {
            ArpeggioNote note = response.notes.get(i);
            notes[i][0] = note.string;
            notes[i][1] = note.fret;
        }
        return new Arpeggio(response.id, response.title, response.chord, notes, response.difficulty);
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
        REGISTER,
        LEVEL,
        HOME,
        COURSE,
        MODULE,
        LESSON,
        MODULE_PREVIEW,
        PRACTICE,
        ARPEGGIOS,
        SONGS,
        ADMIN_SONGS,
        ADMIN_EDITOR,
        ADMIN_PANEL,
        ADMIN_ARPEGGIOS,
        ADMIN_ARPEGGIO_EDITOR,
        ADMIN_MODULES,
        ADMIN_MODULE_ITEMS,
        ADMIN_MODULE_ITEM_EDITOR,
        CONFIGURATOR_PANEL,
        USER_MANAGEMENT,
        MODULE_MANAGEMENT,
        PROGRESS,
        SETTINGS,
        EDIT_PROFILE,
        ABOUT
    }

    private FrameLayout root;
    private SessionManager sessionManager;
    private SharedPreferences profile;
    private ProgressStore progressStore;
    private ActivityStore activityStore;
    private SongProgressStore songProgressStore;
    private DashboardRepository dashboardRepository;
    private long songProgressRevision;
    private ThemeStore themeStore;
    private DedilharteSyncManager syncManager;
    private SongRepository songRepository;
    private ArpeggioRepository arpeggioRepository;
    private GuitarSoundPlayer guitarSoundPlayer;
    private boolean resumeAfterAudioLoad;
    private boolean previewAfterAudioLoad;
    private GuitarPracticeView practiceView;
    private Lesson currentLesson;
    private Song currentSong;
    private LearningModuleResponse currentModule;
    private LearningModuleItemResponse currentModuleItem;
    private Screen screen = Screen.WELCOME;
    private Screen practiceReturnScreen = Screen.PROGRESS;
    private String currentUserId = "";
    private String userName = "";
    private String level = LessonRepository.BEGINNER;
    private String courseLevel = LessonRepository.BEGINNER;
    private String userRole = ROLE_STUDENT;
    private String profilePhotoUri = "";
    private int weeklyGoal = 3;
    private boolean soundEnabled = true;
    private ImageView profilePhotoPreview;
    private Call<UserResponse> profileSaveCall;
    private int editingWeeklyGoal;
    private final List<LearningModuleResponse> dynamicModules = new ArrayList<>();
    private boolean remotePhotoRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        themeStore = new ThemeStore(this);
        themeStore.applySavedMode();
        super.onCreate(savedInstanceState);
        TEAL = getColor(R.color.theme_teal);
        DARK = getColor(R.color.theme_header);
        CYAN = getColor(R.color.theme_cyan);
        PALE = getColor(R.color.theme_pale);
        SOFT = getColor(R.color.theme_card);
        TEXT = getColor(R.color.theme_body);
        PAGE = getColor(R.color.theme_page);
        PAGE_TEXT = getColor(R.color.theme_page_text);
        PAGE_BODY = getColor(R.color.theme_page_body);
        PAGE_ACCENT = getColor(R.color.theme_page_accent);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_main);
        com.example.dedilharte.view.WindowInsetsHelper.activity(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });

        root = findViewById(R.id.root);
        DedilharteApiClient.configure(getApplicationContext());
        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            openLoginAndFinish();
            return;
        }

        profile = getSharedPreferences("dedilharte_profile", MODE_PRIVATE);
        migrateLegacyProfileIfNeeded();
        if (getIntent().getBooleanExtra("request_notifications", false)) {
            getIntent().removeExtra("request_notifications");
            com.example.dedilharte.auth.NotificationPermissions.requestIfNeeded(this);
        }
        currentUserId = sessionManager.getUserId();
        if (currentUserId.trim().isEmpty()) {
            sessionManager.clearSession();
            openLoginAndFinish();
            return;
        }
        ensureSessionProfileStored();
        loadActiveProfile();
        progressStore = new ProgressStore(this, currentUserId);
        activityStore = new ActivityStore(this, currentUserId);
        songProgressStore = new SongProgressStore(this, currentUserId);
        dashboardRepository = new DashboardRepository(this);
        syncManager = new DedilharteSyncManager();
        songRepository = new SongRepository(this);
        arpeggioRepository = new ArpeggioRepository(this);
        recordDailyAccess();
        updateCalculatedLevel();
        courseLevel = level;

        if (savedInstanceState != null) {
            String restoredLevel = savedInstanceState.getString("level");
            if (restoredLevel != null) {
                level = restoredLevel;
            }
            courseLevel = savedInstanceState.getString("course_level", level);
        }

        if (currentUserId.trim().isEmpty()) {
            showWelcome();
        } else if (isConfigurator()) {
            synchronizeCurrentUser(false);
            showConfiguratorDashboard();
        } else if (isAdmin()) {
            synchronizeCurrentUser(false);
            showAdminDashboard();
        } else {
            synchronizeCurrentUser(false);
            showProgress();
        }
        if (savedInstanceState != null && sessionManager.isLoggedIn()) {
            String previous = savedInstanceState.getString("screen", "");
            if (Screen.SETTINGS.name().equals(previous)) showSettings();
            if (Screen.EDIT_PROFILE.name().equals(previous)) {
                showEditProfile();
                editingWeeklyGoal = savedInstanceState.getInt("editing_weekly_goal", weeklyGoal);
                updateGoalLabel(findViewById(R.id.editGoalValue), editingWeeklyGoal);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("level", level);
        outState.putString("course_level", courseLevel);
        outState.putString("screen", screen.name());
        if (screen == Screen.EDIT_PROFILE) outState.putInt("editing_weekly_goal", editingWeeklyGoal);
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
                .putInt(accountKey(legacyId, "weekly_goal"), 3)
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
            weeklyGoal = 3;
            soundEnabled = true;
            return;
        }

        boolean sessionMatches = sessionManager != null && currentUserId.equals(sessionManager.getUserId());
        String sessionName = sessionMatches ? sessionManager.getName() : "";
        String sessionRole = sessionMatches ? sessionManager.getRole() : "";
        userName = !sessionName.trim().isEmpty()
                ? sessionName
                : profile.getString(accountKey(currentUserId, "name"), "");
        level = profile.getString(accountKey(currentUserId, "level"), LessonRepository.BEGINNER);
        userRole = !sessionRole.trim().isEmpty()
                ? sessionRole
                : profile.getString(accountKey(currentUserId, "role"), ROLE_STUDENT);
        weeklyGoal = StudentMetrics.clampGoal(profile.getInt(
                accountKey(currentUserId, "weekly_goal"),
                sessionManager == null ? 3 : sessionManager.getWeeklyGoal()
        ));
        profilePhotoUri = profile.getString(accountKey(currentUserId, "photo_uri"), "");
        soundEnabled = profile.getBoolean(accountKey(currentUserId, "sound_enabled"), true);
    }

    private void ensureSessionProfileStored() {
        if (currentUserId.trim().isEmpty()) {
            return;
        }
        Set<String> accounts = accountIds();
        accounts.add(currentUserId);
        SharedPreferences.Editor editor = profile.edit()
                .putStringSet(ACCOUNT_IDS, accounts)
                .putString(ACTIVE_USER_ID, currentUserId)
                .putString(accountKey(currentUserId, "name"), sessionManager.getName())
                .putString(accountKey(currentUserId, "role"), sessionManager.getRole())
                .putInt(accountKey(currentUserId, "weekly_goal"), sessionManager.getWeeklyGoal());
        if (!profile.contains(accountKey(currentUserId, "level"))) {
            editor.putString(accountKey(currentUserId, "level"), LessonRepository.BEGINNER);
        }
        if (!profile.contains(accountKey(currentUserId, "sound_enabled"))) {
            editor.putBoolean(accountKey(currentUserId, "sound_enabled"), true);
        }
        if (!profile.contains(accountKey(currentUserId, "updated_at"))) {
            editor.putLong(accountKey(currentUserId, "updated_at"), System.currentTimeMillis());
        }
        if (!profile.contains(accountKey(currentUserId, "created_at"))) {
            editor.putLong(accountKey(currentUserId, "created_at"), System.currentTimeMillis());
        }
        editor.apply();
    }

    private void saveActiveProfile() {
        if (currentUserId.trim().isEmpty()) {
            return;
        }
        profile.edit()
                .putString(accountKey(currentUserId, "name"), userName)
                .putString(accountKey(currentUserId, "level"), level)
                .putString(accountKey(currentUserId, "role"), userRole)
                .putInt(accountKey(currentUserId, "weekly_goal"), StudentMetrics.clampGoal(weeklyGoal))
                .putString(accountKey(currentUserId, "photo_uri"), profilePhotoUri)
                .putBoolean(accountKey(currentUserId, "sound_enabled"), soundEnabled)
                .apply();
        if (sessionManager != null && currentUserId.equals(sessionManager.getUserId())) {
            sessionManager.updateUser(userName, sessionManager.getEmail(), userRole, weeklyGoal);
        }
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

    private long activeUserCreatedAt() {
        if (currentUserId.trim().isEmpty()) {
            return System.currentTimeMillis();
        }
        return profile.getLong(accountKey(currentUserId, "created_at"), activeUserUpdatedAt());
    }

    private void setActiveUserUpdatedAt(long updatedAtMillis) {
        if (currentUserId.trim().isEmpty() || updatedAtMillis <= 0) {
            return;
        }
        profile.edit()
                .putLong(accountKey(currentUserId, "updated_at"), updatedAtMillis)
                .apply();
    }

    private void updateCalculatedLevel() {
        if (dashboardRepository == null) return;
        DashboardMetrics metrics = dashboardRepository.cached();
        if (metrics == null || !metrics.complete()) return;
        String calculated = metrics.level();
        if (!calculated.equals(level)) {
            level = calculated;
            saveActiveProfile();
        }
    }
    private int weightedLearnedSongs() {
        int total = 0;
        if (songProgressStore == null || songRepository == null) {
            return total;
        }
        for (Song song : songRepository.publishedQueued()) {
            if (songProgressStore.isLearned(song.getId())) {
                total += song.difficultyWeight();
            }
        }
        return total;
    }

    private int weightedTotalSongs() {
        int total = 0;
        if (songRepository == null) {
            return total;
        }
        for (Song song : songRepository.publishedQueued()) {
            total += song.difficultyWeight();
        }
        return total;
    }

    private void syncActiveUser() {
        if (syncManager == null || currentUserId.trim().isEmpty()) {
            return;
        }
        syncManager.syncUser(currentUserId, userName, weeklyGoal, activeUserUpdatedAt());
    }

    private void synchronizeCurrentUser(boolean includeAllProgress) {
        if (syncManager == null || currentUserId.trim().isEmpty()) {
            return;
        }
        // Profile edits are explicit server-confirmed writes; opening the app must not
        // upload an older profile over changes made on another device.
        final String profileOwner = currentUserId, profileToken = sessionManager.getToken();
        syncManager.fetchRemoteUser(currentUserId, user -> {
            if (isCurrentProgressSession(profileOwner, profileToken)) applyRemoteUserIfNewer(user);
        });
        syncAllProgress(includeAllProgress);
        final String progressOwner = currentUserId;
        final String progressToken = sessionManager.getToken();
        final long progressRevision = songProgressRevision;
        syncManager.fetchSongProgress(items -> {
            if (isCurrentProgressSession(progressOwner, progressToken) && progressRevision == songProgressRevision) {
                applyRemoteSongProgress(items);
            }
        });
        syncManager.fetchRemoteProgress(currentUserId, progressStore, () -> {
            if (screen == Screen.PROGRESS) {
                showProgress();
            } else if (screen == Screen.COURSE) {
                showProgress();
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
                || remoteUser.updatedAtMillis <= profile.getLong(accountKey(currentUserId, "server_updated_at"), 0)) {
            return;
        }
        userName = remoteUser.name == null ? userName : remoteUser.name;
        if (remoteUser.role != null) {
            userRole = remoteUser.role;
        }
        sessionManager.updatePreferences(remoteUser.initialLevel, remoteUser.songGoal, remoteUser.notificationsEnabled);
        profile.edit().putLong(accountKey(currentUserId, "server_updated_at"), remoteUser.updatedAtMillis).apply();
        weeklyGoal = StudentMetrics.clampGoal(remoteUser.weeklyGoal);
        setActiveUserUpdatedAt(remoteUser.updatedAtMillis);
        saveActiveProfile();
        updateCalculatedLevel();
        if (screen == Screen.PROGRESS) {
            showProgress();
        } else if (screen == Screen.COURSE) {
            showProgress();
        }
    }

    private void applyRemoteSongProgress(List<SongProgressResponse> items) {
        if (songProgressStore == null || items == null) {
            return;
        }
        for (SongProgressResponse item : items) {
            if (item != null && item.song_id != null) {
                songProgressStore.setLearned(item.song_id, item.learned);
            }
        }
        updateCalculatedLevel();
        if (screen == Screen.PROGRESS) {
            showProgress();
        } else if (screen == Screen.SONGS) {
            showSongs();
        } else if (screen == Screen.HOME) {
            showHome();
        }
    }

    private void recordDailyAccess() {
        if (activityStore == null) {
            return;
        }
        activityStore.recordToday();
        if (syncManager != null) {
            syncManager.recordActivity(() -> {
                if (!isDestroyed() && screen == Screen.PROGRESS) showProgress();
            });
        }
    }

    private void createAccount(String name, boolean admin) {
        currentUserId = "user_" + System.currentTimeMillis();
        userName = name;
        level = LessonRepository.BEGINNER;
        userRole = admin ? ROLE_ADMIN : ROLE_STUDENT;
        profilePhotoUri = "";
        weeklyGoal = 3;
        soundEnabled = true;

        Set<String> accounts = accountIds();
        accounts.add(currentUserId);
        profile.edit()
                .putStringSet(ACCOUNT_IDS, accounts)
                .putString(ACTIVE_USER_ID, currentUserId)
                .putLong(accountKey(currentUserId, "created_at"), System.currentTimeMillis())
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
        courseLevel = level;
        progressStore.setUserId(currentUserId);
        if (activityStore != null) {
            activityStore.setUserId(currentUserId);
        }
        if (songProgressStore != null) {
            songProgressStore.setUserId(currentUserId);
        }
        if (dashboardRepository != null) dashboardRepository.cancel();
        dashboardRepository = new DashboardRepository(this);
        synchronizeCurrentUser(false);
        if (isConfigurator()) {
            showConfiguratorDashboard();
        } else if (isAdmin()) {
            showAdminDashboard();
        } else {
            showProgress();
        }
    }

    private void logout() {
        stopPractice();
        currentLesson = null;
        currentUserId = "";
        if (sessionManager != null) {
            sessionManager.clearSession();
        }
        profile.edit().remove(ACTIVE_USER_ID).apply();
        loadActiveProfile();
        if (progressStore != null) {
            progressStore.setUserId(currentUserId);
        }
        openLoginAndFinish();
    }

    private void openLoginAndFinish() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void confirmDeleteAccount() {
        if (currentUserId.trim().isEmpty()) {
            showWelcome();
            return;
        }
        new AlertDialog.Builder(this)
                .setIcon(MaterialIcons.dialogWarning(this))
                .setTitle("Excluir sua conta?")
                .setMessage("Seu perfil e progresso serão removidos permanentemente.\nEsta ação não pode ser desfeita.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir conta", (dialog, which) -> deleteCurrentAccount())
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
                .remove(accountKey(deletedUserId, "weekly_goal"))
                .remove(accountKey(deletedUserId, "photo_uri"))
                .remove(accountKey(deletedUserId, "sound_enabled"))
                .remove(accountKey(deletedUserId, "updated_at"))
                .remove(accountKey(deletedUserId, "created_at"));
        if (deletedUserId.startsWith("legacy_")) {
            editor.remove("name")
                    .remove("level")
                    .remove("photo_uri")
                    .remove("sound_enabled");
        }
        editor.apply();

        syncManager.deleteUser(deletedUserId);
        progressStore.deleteUserData(deletedUserId);
        if (activityStore != null) {
            activityStore.deleteUserData(deletedUserId);
        }
        if (songProgressStore != null) {
            songProgressStore.deleteUserData(deletedUserId);
        }
        currentUserId = "";
        if (sessionManager != null) {
            sessionManager.clearSession();
        }
        loadActiveProfile();
        progressStore.setUserId(currentUserId);
        Toast.makeText(this, "Conta excluida", Toast.LENGTH_SHORT).show();
        openLoginAndFinish();
    }

    private void showLogin() {
        openLoginAndFinish();
    }

    private void showRegister(boolean admin) {
        startActivity(new Intent(this, RegisterActivity.class));
    }

    private void showWelcome() {
        showLogin();
    }

    private void showLegacyWelcome() {
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

        TextView continueButton = button("CRIAR CONTA", WHITE, DARK, 62, R.drawable.ic_ms_person);
        continueButton.setOnClickListener(v -> {
            String typed = nameInput.getText().toString().trim();
            if (typed.isEmpty()) {
                nameInput.setError("Digite seu nome");
                nameInput.requestFocus();
                return;
            }
            createAccount(typed, false);
            showProgress();
        });
        page.addView(continueButton, matchWrap(0, 0));
        root.addView(page, matchMatch());
    }

    // Compatibility entry points: legacy navigation must never render local lessons.
    private void showLevelChoice() {
        showProgress();
    }

    private void showCourse(String selectedLevel) {
        if (LessonRepository.INTERMEDIATE.equals(selectedLevel)) {
            openFixedTriviaModule(MODULE_TRIVIA_INTERMEDIATE, "Trivia Intermediário");
        } else {
            openFixedTriviaModule(MODULE_TRIVIA_BEGINNER, "Trivia Iniciante");
        }
    }

    private void showHome() {
        stopPractice();
        screen = Screen.HOME;
        currentLesson = null;
        currentModule = null;
        currentModuleItem = null;
        root.removeAllViews();
        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(mainHeader(), new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        content.addView(text("Olá, " + firstName() + "!", 18, PAGE_ACCENT, true, Gravity.START, 0, 8));
        content.addView(text("Continuar de onde parou", 27, PAGE_TEXT, true, Gravity.START, 0, 16));
        LinearLayout resume = column(WHITE, Gravity.TOP);
        content.addView(resume, matchWrap(0, 24));
        content.addView(text("Músicas que você pode gostar", 23, PAGE_TEXT, true, Gravity.START, 0, 8));
        content.addView(text("Sugestões pelo seu nível e pelas músicas que já aprendeu.",
                15, PAGE_BODY, false, Gravity.START, 0, 16));
        LinearLayout recommendations = column(WHITE, Gravity.TOP);
        recommendations.addView(text("Carregando músicas…", 16, PAGE_BODY, false, Gravity.START, 0, 12));
        content.addView(recommendations, matchWrap(0, 16));
        TextView repertoire = button("VER TODAS AS MÚSICAS", DARK, WHITE, 52, R.drawable.ic_ms_library_music);
        repertoire.setOnClickListener(v -> showSongs());
        content.addView(repertoire, matchWrap(0, 24));
        TextView beginner = button("TRIVIA INICIANTE", CYAN, WHITE, 52, R.drawable.ic_ms_school);
        beginner.setOnClickListener(v -> openFixedTriviaModule(MODULE_TRIVIA_BEGINNER, "Trivia Iniciante"));
        content.addView(beginner, matchWrap(0, 12));
        TextView intermediate = button("TRIVIA INTERMEDIÁRIO", DARK, WHITE, 52, R.drawable.ic_ms_quiz);
        intermediate.setOnClickListener(v -> openFixedTriviaModule(MODULE_TRIVIA_INTERMEDIATE, "Trivia Intermediário"));
        content.addView(intermediate, matchWrap(0, 12));
        TextView refresh = button("ATUALIZAR", SOFT, DARK, 48, R.drawable.ic_ms_refresh);
        refresh.setOnClickListener(v -> showHome());
        content.addView(refresh, matchWrap(0, 0));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
        loadHomeResume(resume, shell);
        songRepository.fetchPublishedQueuedWithErrors((songs, success, error) -> {
            if (isDestroyed() || shell.getParent() != root) return;
            recommendations.removeAllViews();
            if (!success) {
                recommendations.addView(text(songs.isEmpty() ? error : "Exibindo músicas salvas. " + error,
                        16, PAGE_BODY, false, Gravity.START, 0, 12));
                if (songs.isEmpty()) return;
            }
            List<Song> suggested = SongRepository.recommendations(songs, songProgressStore.learnedSongIds(), level);
            for (Song song : suggested) {
                TextView card = songCard(song.getTitle(), song.getDifficulty(), song.getArtist());
                card.setContentDescription("Praticar música: " + song.getTitle());
                card.setOnClickListener(v -> showSongPractice(song));
                recommendations.addView(card, matchWrap(0, 12));
            }
            if (suggested.isEmpty()) {
                recommendations.addView(text("Nenhuma nova música disponível no momento.",
                        16, PAGE_BODY, false, Gravity.START, 0, 12));
            }
        });
    }

    private void loadHomeResume(LinearLayout resume, View shell) {
        String lessonId = progressStore.lastLessonId();
        String moduleId = progressStore.lastModuleId();
        if (lessonId.isEmpty()) {
            resume.addView(infoCard("Sua próxima lição", "Escolha uma trivia ou uma aula para começar."));
            return;
        }
        if (!MODULE_TRIVIA_BEGINNER.equals(moduleId) && !MODULE_TRIVIA_INTERMEDIATE.equals(moduleId)) {
            resume.addView(infoCard("Sua próxima lição", "Escolha uma Trivia para continuar."));
            return;
        }
        resume.addView(text("Carregando sua última lição…", 16, PAGE_BODY, false, Gravity.START, 0, 12));
        DedilharteApiClient.service().getModules().enqueue(new Callback<LearningModuleListResponse>() {
            @Override
            public void onResponse(Call<LearningModuleListResponse> call, Response<LearningModuleListResponse> response) {
                if (isDestroyed() || shell.getParent() != root) return;
                resume.removeAllViews();
                if (!response.isSuccessful() || response.body() == null || response.body().items == null) {
                    showError();
                    return;
                }
                for (LearningModuleResponse module : response.body().items) {
                    if (module == null || !module.active || !moduleId.equals(module.id) || module.items == null) continue;
                    for (LearningModuleItemResponse item : module.items) {
                        if (item != null && item.active && lessonId.equals(item.id)) {
                            renderHomeResume(resume, item.title, module.name, () -> showModulePreview(module, item));
                            return;
                        }
                    }
                }
                resume.addView(infoCard("Lição indisponível", "A última lição acessada não está mais disponível."));
            }

            @Override
            public void onFailure(Call<LearningModuleListResponse> call, Throwable t) {
                if (!isDestroyed() && shell.getParent() == root) showError();
            }

            private void showError() {
                resume.removeAllViews();
                resume.addView(infoCard("Sua última lição", "Não foi possível carregar a lição. Toque em Atualizar para tentar novamente."));
            }
        });
    }

    private void renderHomeResume(LinearLayout resume, String title, String source, Runnable open) {
        resume.removeAllViews();
        resume.addView(infoCard(source == null ? "Sua última lição" : source, title == null ? "Lição" : title), matchWrap(0, 12));
        TextView continueLesson = button("CONTINUAR LIÇÃO", DARK, WHITE, 58);
        continueLesson.setOnClickListener(v -> open.run());
        resume.addView(continueLesson, matchWrap(0, 0));
    }
    private View trackSwitchPanel(String selectedLevel) {
        LinearLayout panel = column(SOFT, Gravity.TOP);
        panel.setPadding(dp(16), dp(14), dp(16), dp(16));
        panel.setBackground(roundRect(SOFT, 16));
        panel.addView(text("Trilhas fixas", 16, DARK, true, Gravity.START, 0, 10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(2f);

        boolean beginnerSelected = LessonRepository.BEGINNER.equals(selectedLevel);
        TextView beginner = button("INICIANTE", beginnerSelected ? DARK : WHITE, beginnerSelected ? WHITE : DARK, 48);
        beginner.setOnClickListener(v -> {
            if (!beginnerSelected) {
                openFixedTriviaModule(MODULE_TRIVIA_BEGINNER, "Trivia Iniciante");
            }
        });

        boolean intermediateSelected = LessonRepository.INTERMEDIATE.equals(selectedLevel);
        TextView intermediate = button("INTERMEDIÁRIO", intermediateSelected ? DARK : WHITE, intermediateSelected ? WHITE : DARK, 48);
        intermediate.setOnClickListener(v -> {
            if (!intermediateSelected) {
                openFixedTriviaModule(MODULE_TRIVIA_INTERMEDIATE, "Trivia Intermediário");
            }
        });

        row.addView(beginner, weightButton(0, 5));
        row.addView(intermediate, weightButton(5, 0));
        panel.addView(row, matchWrap(0, 0));
        return panel;
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
        progressStore.rememberLesson(null, lesson.getId());
        courseLevel = lesson.getLevel();
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Aula " + lesson.getOrder(), () -> showCourse(lesson.getLevel())),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(36));

        content.addView(statusPill(
                progressStore.isCompleted(lesson.getId()) ? "CONCLUÍDA" : lesson.getLevel().toUpperCase(Locale.ROOT),
                progressStore.isCompleted(lesson.getId()) ? PALE : CYAN,
                progressStore.isCompleted(lesson.getId()) ? R.drawable.ic_ms_check_circle : R.drawable.ic_ms_school
        ), wrapStart(0, 16));
        content.addView(text(lesson.getTitle(), 29, PAGE_TEXT, true, Gravity.START, 0, 8));
        content.addView(text(lesson.getSubtitle(), 16, PAGE_ACCENT, true, Gravity.START, 0, 24));

        content.addView(infoCard("OBJETIVO", lesson.getObjective()), matchWrap(0, 14));
        content.addView(infoCard("COMO FUNCIONA", lesson.getTheory()), matchWrap(0, 20));

        content.addView(text("Passo a passo", 21, PAGE_TEXT, true, Gravity.START, 0, 12));
        String[] steps = lesson.getSteps();
        for (int i = 0; i < steps.length; i++) {
            content.addView(stepCard(i + 1, steps[i]), matchWrap(0, 10));
        }

        content.addView(text("Padrão do exercício", 21, PAGE_TEXT, true, Gravity.START, 14, 8));
        content.addView(infoCard(
                lesson.getChord(),
                "Cordas: " + patternToText(lesson.getPattern()) + "\nVelocidade sugerida: " + lesson.getBpm() + " BPM"
        ), matchWrap(0, 18));

        TextView practiceButton = button("ABRIR MODO TREINO", DARK, WHITE, 62, R.drawable.ic_ms_play_arrow);
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
                completed ? "AULA CONCLUÍDA" : "MARCAR COMO CONCLUÍDA",
                completed ? PALE : CYAN,
                WHITE,
                62
        );
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        MaterialIcons.leading(button, R.drawable.ic_ms_check_circle, completed ? PALE : CYAN);
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
        content.addView(text(title, 25, PAGE_TEXT, true, Gravity.START, 0, 4));
        content.addView(text(chord, 15, PAGE_ACCENT, true, Gravity.START, 0, 6));

        practiceView = new GuitarPracticeView(this);
        practiceView.setPattern(pattern);
        practiceView.setBpm(suggestedBpm);
        practiceView.setBackground(roundRect(WHITE, 18));
        LinearLayout.LayoutParams practiceParams = new LinearLayout.LayoutParams(-1, -2);
        practiceParams.setMargins(0, dp(2), 0, dp(8));
        content.addView(practiceView, practiceParams);

        TextView status = text(
                "Corda " + pattern[0] + " • " + fingerForString(pattern[0]),
                16, PAGE_TEXT, true, Gravity.CENTER, 0, 12
        );
        content.addView(status);

        TextView bpmLabel = text(
                suggestedBpm + " BPM",
                17, PAGE_ACCENT, true, Gravity.CENTER, 0, 4
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
            if (position == 0 && practiceView != null && (practiceView.isRunning() || resumeAfterAudioLoad)) {
                repetition[0]++;
            }
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setWeightSum(2f);
        TextView startPause = button("INICIAR", DARK, WHITE, 58);
        TextView reset = button("REINICIAR", CYAN, WHITE, 58, R.drawable.ic_ms_replay);
        LinearLayout.LayoutParams halfLeft = new LinearLayout.LayoutParams(0, -2, 1f);
        halfLeft.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams halfRight = new LinearLayout.LayoutParams(0, -2, 1f);
        halfRight.setMargins(dp(6), 0, 0, 0);
        controls.addView(startPause, halfLeft);
        controls.addView(reset, halfRight);
        content.addView(controls, matchWrap(0, 12));
        guitarSoundPlayer = new GuitarSoundPlayer(this);
        bindPracticeAudio(status, startPause, "INICIAR");

        startPause.setOnClickListener(v -> {
            if (practiceView.isRunning() || resumeAfterAudioLoad) {
                practiceView.pause();
                cancelPracticeAudio();
                startPause.setText("CONTINUAR");
            } else {
                practiceView.start();
                startPause.setText("PAUSAR");
            }
        });
        reset.setOnClickListener(v -> {
            repetition[0] = 0;
            cancelPracticeAudio();
            practiceView.reset();
            startPause.setText("INICIAR");
        });

        practiceView.setOnTabEventListener(new GuitarPracticeView.OnTabEventListener() {
            @Override
            public void onEvent(int position, com.example.dedilharte.model.TablatureEvent event) {
                status.setText(tabEventToText(event));
                if (practiceView.isRunning()) playCurrentPracticeEvent(status);
            }

            @Override
            public void onFinished() {
                cancelPracticeAudio();
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
            if (!soundEnabled) cancelPracticeAudio();
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
        showSongPractice(song, null, null);
    }

    private void showSongPractice(Song song, LearningModuleResponse module, LearningModuleItemResponse item) {
        Screen sourceScreen = screen;
        stopPractice();
        screen = Screen.PRACTICE;
        currentModule = module;
        currentModuleItem = item;
        practiceReturnScreen = module == null
                ? (sourceScreen == Screen.HOME ? Screen.HOME : Screen.SONGS) : Screen.MODULE_PREVIEW;
        currentLesson = null;
        currentSong = song;
        root.removeAllViews();

        final int[] selectedBpm = {clampBpm(song.getRecommendedBpm())};
        final int[] lastPlayedPosition = {-1};
        final String[] bpmMode = {"100"};

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Tocar música", this::returnFromPractice),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        content.addView(text(song.getTitle(), 25, PAGE_TEXT, true, Gravity.START, 0, 4));
        content.addView(text(song.getArtist() + "  •  " + song.getDifficulty(), 15, PAGE_ACCENT, true, Gravity.START, 0, 10));
        content.addView(text("BPM recomendado: " + song.getRecommendedBpm(), 15, PAGE_BODY, false, Gravity.START, 0, 10));

        TextView state = text("Carregando sons...", 15, PAGE_TEXT, true, Gravity.CENTER, 0, 8);
        TextView bpmLabel = text("BPM selecionado: " + selectedBpm[0], 17, PAGE_ACCENT, true, Gravity.CENTER, 0, 4);
        TextView progress = text("Nota 1 de 1", 14, PAGE_BODY, true, Gravity.CENTER, 0, 8);
        content.addView(state);
        content.addView(bpmLabel);

        practiceView = new GuitarPracticeView(this);
        practiceView.setTablatureText(song.getTablature());
        practiceView.setBpm(selectedBpm[0]);
        practiceView.setBackground(roundRect(WHITE, 18));
        LinearLayout.LayoutParams practiceParams = new LinearLayout.LayoutParams(-1, -2);
        practiceParams.setMargins(0, dp(2), 0, dp(8));
        content.addView(practiceView, practiceParams);

        TextView status = text("Pronto para iniciar", 16, PAGE_TEXT, true, Gravity.CENTER, 0, 12);
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

        TextView customMode = text("Personalizado", 13, PAGE_TEXT, true, Gravity.CENTER, 0, 8);
        customMode.setBackground(roundRect(SOFT, 18));
        if (ThemeStore.isNight(this)) customMode.setTextColor(DARK);
        customMode.setPadding(dp(12), dp(7), dp(12), dp(7));
        content.addView(customMode, matchWrap(0, 6));

        TextView customValue = text(selectedBpm[0] + " BPM", 20, PAGE_ACCENT, true, Gravity.CENTER, 0, 6);
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
        TextView previous = button("", SOFT, DARK, 58);
        TextView startPause = button(guitarSoundPlayer.isReady() ? "" : "Tentar novamente", DARK, WHITE, 58);
        TextView next = button("", SOFT, DARK, 58);
        previous.setTextSize(24);
        startPause.setTextSize(24);
        next.setTextSize(24);
        MaterialIcons.iconOnly(previous, R.drawable.ic_ms_skip_previous, SOFT, "Voltar para a nota anterior");
        MaterialIcons.iconOnly(next, R.drawable.ic_ms_skip_next, SOFT, "Avançar para a próxima nota");
        if (guitarSoundPlayer.isReady()) MaterialIcons.iconOnly(startPause, R.drawable.ic_ms_play_arrow, DARK, "Continuar música");
        previous.setContentDescription("Voltar para a nota anterior");
        startPause.setContentDescription(guitarSoundPlayer.isReady() ? "Continuar música" : "Tentar novamente");
        next.setContentDescription("Avançar para a próxima nota");
        controls.addView(previous, weightButton(0, 6));
        controls.addView(startPause, weightButton(6, 6));
        controls.addView(next, weightButton(6, 0));
        content.addView(controls, matchWrap(0, 12));
        TextView reset = button("Reiniciar", CYAN, WHITE, 52, R.drawable.ic_ms_replay);
        content.addView(reset, matchWrap(0, 12));
        updatePracticeProgress(progress);
        updateNavigationButtons(previous, next);
        bindPracticeAudio(state, startPause, null);

        updateBpmSelection(half, threeQuarter, full, customMode, bpmMode[0]);
        View.OnClickListener bpmChoice = v -> {
            if (practiceView != null && (practiceView.isRunning() || resumeAfterAudioLoad)) {
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
                if (practiceView != null && (practiceView.isRunning() || resumeAfterAudioLoad)) {
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
                if (practiceView.isRunning()) {
                    lastPlayedPosition[0] = position;
                    playCurrentPracticeEvent(state);
                }
            }

            @Override
            public void onFinished() {
                if (guitarSoundPlayer != null) {
                    cancelPracticeAudio();
                }
                MaterialIcons.iconOnly(startPause, R.drawable.ic_ms_play_arrow, DARK, "Continuar música");
                startPause.setContentDescription("Continuar música");
                state.setText("Finalizado");
                updatePracticeProgress(progress);
                updateNavigationButtons(previous, next);
            }
        });

        startPause.setOnClickListener(v -> {
            if (practiceView.isRunning() || resumeAfterAudioLoad) {
                practiceView.pause();
                lastPlayedPosition[0] = -1;
                if (guitarSoundPlayer != null) {
                    cancelPracticeAudio();
                }
                state.setText("Pausado");
                MaterialIcons.iconOnly(startPause, R.drawable.ic_ms_play_arrow, DARK, "Continuar música");
                startPause.setContentDescription("Continuar música");
            } else {
                practiceView.start();
                MaterialIcons.iconOnly(startPause, R.drawable.ic_ms_pause, DARK, "Pausar música");
                startPause.setContentDescription("Pausar música");
            }
        });
        previous.setOnClickListener(v -> {
            if (guitarSoundPlayer != null) {
                cancelPracticeAudio();
            }
            practiceView.moveToPreviousEvent();
            lastPlayedPosition[0] = -1;
            state.setText("Pausado");
            MaterialIcons.iconOnly(startPause, R.drawable.ic_ms_play_arrow, DARK, "Continuar música");
            startPause.setContentDescription("Continuar música");
            updatePracticeProgress(progress);
            updateNavigationButtons(previous, next);
        });
        next.setOnClickListener(v -> {
            if (guitarSoundPlayer != null) {
                cancelPracticeAudio();
            }
            practiceView.moveToNextEvent();
            lastPlayedPosition[0] = -1;
            state.setText("Pausado");
            MaterialIcons.iconOnly(startPause, R.drawable.ic_ms_play_arrow, DARK, "Continuar música");
            startPause.setContentDescription("Continuar música");
            updatePracticeProgress(progress);
            updateNavigationButtons(previous, next);
        });
        reset.setOnClickListener(v -> {
            if (guitarSoundPlayer != null) {
                cancelPracticeAudio();
            }
            lastPlayedPosition[0] = -1;
            practiceView.reset();
            updatePracticeProgress(progress);
            updateNavigationButtons(previous, next);
            state.setText(guitarSoundPlayer == null ? "Erro" : guitarSoundPlayer.statusText());
            if (guitarSoundPlayer != null && guitarSoundPlayer.isReady()) {
                MaterialIcons.iconOnly(startPause, R.drawable.ic_ms_play_arrow, DARK, "Continuar música");
            } else {
                startPause.setCompoundDrawablesRelative(null, null, null, null);
                startPause.setTextSize(17);
                startPause.setText("Tentar novamente");
            }
            startPause.setContentDescription(guitarSoundPlayer != null && guitarSoundPlayer.isReady()
                    ? "Continuar música"
                    : "Tentar novamente");
        });

        TextView sound = button(soundEnabled ? "SOM: LIGADO" : "SOM: DESLIGADO", SOFT, DARK, 52);
        sound.setOnClickListener(v -> {
            soundEnabled = !soundEnabled;
            saveActiveProfile();
            if (!soundEnabled) cancelPracticeAudio();
            sound.setText(soundEnabled ? "SOM: LIGADO" : "SOM: DESLIGADO");
        });
        content.addView(sound, matchWrap(0, 12));

        if (item != null) {
            TextView complete = button("CONCLUIR AULA", CYAN, DARK, 54, R.drawable.ic_ms_check_circle);
            complete.setOnClickListener(v -> submitModuleProgress(item, null, complete));
            content.addView(complete, matchWrap(0, 12));
        } else {
            content.addView(songLearnedButton(song), matchWrap(0, 12));
        }

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void returnFromPractice() {
        stopPractice();
        if (practiceReturnScreen == Screen.MODULE_PREVIEW && currentModule != null && currentModuleItem != null) {
            showModulePreview(currentModule, currentModuleItem);
        } else if (practiceReturnScreen == Screen.LESSON && currentLesson != null) {
            showCourse(currentLesson.getLevel());
        } else if (practiceReturnScreen == Screen.ARPEGGIOS) {
            showArpeggios();
        } else if (practiceReturnScreen == Screen.SONGS) {
            showSongs();
        } else if (practiceReturnScreen == Screen.HOME) {
            showHome();
        } else {
            showProgress();
        }
    }

    private void showArpeggios() {
        stopPractice();
        screen = Screen.ARPEGGIOS;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Arpejos", this::showProgress),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        content.addView(text("Biblioteca de arpejos", 28, PAGE_TEXT, true, Gravity.START, 0, 7));
        content.addView(text(
                "Escolha um acorde e pratique o padrão P–I–M–A.",
                15, PAGE_BODY, false, Gravity.START, 0, 22
        ));

        content.addView(text("Carregando arpejos…", 16, PAGE_BODY, false, Gravity.START, 0, 0));
        if (arpeggioRepository != null) {
            arpeggioRepository.fetchPublic((items, fromNetwork) -> {
                if (screen == Screen.ARPEGGIOS && shell.getParent() == root) {
                    renderArpeggioCards(content, fromNetwork || !items.isEmpty() ? items : null);
                    if (!fromNetwork && !items.isEmpty()) {
                        content.addView(text("Exibindo arpejos salvos neste aparelho.", 14, PAGE_BODY, false, Gravity.START, 0, 8));
                    }
                }
            });
        }

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void renderArpeggioCards(LinearLayout content, List<ArpeggioResponse> items) {
        while (content.getChildCount() > 2) {
            content.removeViewAt(2);
        }
        TextView refresh = button("ATUALIZAR", DARK, WHITE, 48, R.drawable.ic_ms_refresh);
        refresh.setOnClickListener(v -> showArpeggios());
        content.addView(refresh, matchWrap(0, 12));
        if (items == null || items.isEmpty()) {
            content.addView(text(items == null ? "Não foi possível carregar os arpejos." : "Nenhum arpejo cadastrado.",
                    16, PAGE_BODY, false, Gravity.START, 0, 0));
            return;
        }
        List<ArpeggioResponse> source = items;
        for (ArpeggioResponse item : source) {
            if (!item.active) {
                continue;
            }
            Arpeggio arpeggio = arpeggioFromResponse(item);
            TextView card = button(arpeggio.title + "\n" + patternToText(arpeggio.pattern), DARK, WHITE, 78);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(18), dp(8), dp(18), dp(8));
            card.setOnClickListener(v -> showArpeggioPractice(arpeggio));
            content.addView(card, matchWrap(0, 12));
        }
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
        content.addView(text("Arpejo em " + arpeggio.title, 25, PAGE_TEXT, true, Gravity.START, 0, 4));
        content.addView(text(patternToText(arpeggio.pattern), 15, PAGE_ACCENT, true, Gravity.START, 0, 6));

        TextView state = text("Carregando sons...", 15, PAGE_TEXT, true, Gravity.CENTER, 0, 8);
        TextView bpmLabel = text("60 BPM", 17, PAGE_ACCENT, true, Gravity.CENTER, 0, 4);
        TextView progress = text("Nota 1 de 4", 14, PAGE_BODY, true, Gravity.CENTER, 0, 8);
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

        TextView status = text("Pronto para iniciar", 16, PAGE_TEXT, true, Gravity.CENTER, 0, 12);
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
                if (practiceView.isRunning()) {
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
                cancelPracticeAudio();
            }
            sound.setText(soundEnabled ? "SOM: LIGADO" : "SOM: DESLIGADO");
        });
        content.addView(sound, matchWrap(0, 12));

        bindPracticeAudio(state, startPause, "PLAY");

        startPause.setOnClickListener(v -> {
            if (practiceView.isRunning() || resumeAfterAudioLoad) {
                practiceView.pause();
                lastPlayedPosition[0] = -1;
                if (guitarSoundPlayer != null) {
                    cancelPracticeAudio();
                }
                state.setText("Pausado");
                startPause.setText("PLAY");
                startPause.setContentDescription("Continuar arpejo");
            } else {
                practiceView.start();
                startPause.setText("PAUSAR");
                startPause.setContentDescription("Pausar arpejo");
            }
        });
        previous.setOnClickListener(v -> {
            if (guitarSoundPlayer != null) {
                cancelPracticeAudio();
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
                cancelPracticeAudio();
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
        shell.addView(pageHeader("Repertório", this::showProgress),
                new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout content = column(TEAL, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        EditText search = input("", "Pesquise uma prática");
        search.setCompoundDrawablesRelative(null, null, MaterialIcons.drawable(this, R.drawable.ic_ms_search, DARK), null);
        content.addView(search, matchWrap(0, 16));
        content.addView(text(
                "Recomendadas para você, " + firstName() + "!",
                17, WHITE, true, Gravity.START, 0, 12
        ));

        ScrollView resultsScroll = new ScrollView(this);
        LinearLayout results = column(TEAL, Gravity.TOP);
        final List<Song> currentSongs = new ArrayList<>(songRepository.publishedQueued());
        search.setEnabled(!currentSongs.isEmpty());
        if (currentSongs.isEmpty()) results.addView(text("Carregando músicas…", 16, WHITE, false, Gravity.START, 0, 0));
        else renderSongs(results, "", currentSongs);
        TextView syncStatus = text("Atualizando músicas…", 14, WHITE, false, Gravity.START, 0, 8);
        content.addView(syncStatus);
        TextView refreshSongs = button("ATUALIZAR", DARK, WHITE, 48, R.drawable.ic_ms_refresh);
        refreshSongs.setOnClickListener(v -> showSongs());
        content.addView(refreshSongs, matchWrap(0, 12));
        resultsScroll.addView(results);
        content.addView(resultsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());

        songRepository.fetchPublishedQueuedWithErrors((songs, fromNetwork, error) -> {
            if (!isDestroyed() && shell.getParent() == root) {
                currentSongs.clear();
                if (!fromNetwork && songs.isEmpty()) {
                    results.removeAllViews();
                    search.setEnabled(false);
                    syncStatus.setText("");
                    results.addView(text(error,
                            16, WHITE, false, Gravity.START, 0, 0));
                    return;
                }
                currentSongs.addAll(songs);
                syncStatus.setText(fromNetwork ? "" : "Exibindo músicas salvas. " + error);
                search.setEnabled(true);
                renderSongs(results, search.getText().toString(), currentSongs);
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderSongs(results, s.toString(), currentSongs);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void renderSongs(LinearLayout results, String query) {
        renderSongs(results, query, songRepository.publishedQueued());
    }

    private void renderSongs(LinearLayout results, String query, List<Song> songs) {
        results.removeAllViews();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Song song : songs) {
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
                    "Nenhuma música publicada encontrada.",
                    16, WHITE, true, Gravity.CENTER, 28, 0
            ));
        }
    }

    private void showAdminDashboard() {
        stopPractice();
        if (!isAdmin()) {
            showProgress();
            return;
        }
        screen = Screen.ADMIN_PANEL;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Painel administrativo", this::showProgress),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(28), dp(24), dp(32));
        content.addView(text("PAINEL ADMINISTRATIVO", 25, PAGE_TEXT, true, Gravity.START, 0, 20));

        TextView songs = button("Gerenciar músicas", DARK, WHITE, 58, R.drawable.ic_ms_library_music);
        songs.setOnClickListener(v -> showAdminSongs());
        content.addView(songs, matchWrap(0, 12));

        TextView arpeggios = button("Gerenciar arpejos", DARK, WHITE, 58, R.drawable.ic_ms_music_note);
        arpeggios.setOnClickListener(v -> showAdminArpeggios());
        content.addView(arpeggios, matchWrap(0, 12));

        TextView trivia = button("Gerenciar módulos/conteúdo", DARK, WHITE, 58, R.drawable.ic_ms_school);
        trivia.setOnClickListener(v -> showAdminModules());
        content.addView(trivia, matchWrap(0, 12));

        TextView student = button("Visualizar app como aluno", SOFT, DARK, 54, R.drawable.ic_ms_person);
        student.setOnClickListener(v -> showProgress());
        content.addView(student, matchWrap(0, 12));

        TextView settings = button("Configurações", SOFT, DARK, 54, R.drawable.ic_ms_settings);
        settings.setOnClickListener(v -> showSettings());
        content.addView(settings, matchWrap(0, 0));

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void showConfiguratorDashboard() {
        stopPractice();
        if (!isConfigurator()) {
            showProgress();
            return;
        }
        screen = Screen.CONFIGURATOR_PANEL;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Painel configurador", this::showProgress),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(28), dp(24), dp(32));
        content.addView(text("PAINEL CONFIGURADOR", 25, PAGE_TEXT, true, Gravity.START, 0, 20));

        TextView users = button("Gerenciar usuários", DARK, WHITE, 58, R.drawable.ic_ms_group);
        users.setOnClickListener(v -> showUserManagement());
        content.addView(users, matchWrap(0, 12));

        TextView settings = button("Configurações", SOFT, DARK, 54, R.drawable.ic_ms_settings);
        settings.setOnClickListener(v -> showSettings());
        content.addView(settings, matchWrap(0, 0));

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void showAdminModules() {
        stopPractice();
        if (!isAdmin()) {
            showProgress();
            return;
        }
        screen = Screen.ADMIN_MODULES;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Gerenciar módulos/conteúdo", this::showAdminDashboard),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        content.addView(text("Módulos existentes", 24, PAGE_TEXT, true, Gravity.START, 0, 16));
        LinearLayout list = column(WHITE, Gravity.TOP);
        content.addView(list, matchWrap(0, 0));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());

        DedilharteApiClient.service().getStaffModules().enqueue(new Callback<LearningModuleListResponse>() {
            @Override
            public void onResponse(Call<LearningModuleListResponse> call, Response<LearningModuleListResponse> response) {
                List<LearningModuleResponse> modules = response.isSuccessful() && response.body() != null
                        ? response.body().items
                        : null;
                renderAdminModules(list, modules);
            }

            @Override
            public void onFailure(Call<LearningModuleListResponse> call, Throwable t) {
                renderAdminModules(list, null);
            }
        });
    }

    private void renderAdminModules(LinearLayout list, List<LearningModuleResponse> modules) {
        list.removeAllViews();
        if (modules == null) {
            list.addView(text("Não foi possível carregar os módulos. Tente novamente.", 16, PAGE_BODY, false, Gravity.START, 0, 0));
            return;
        }
        List<LearningModuleResponse> fixedModules = fixedTriviaModules(modules);
        if (fixedModules.isEmpty()) {
            list.addView(text("Nenhum módulo encontrado.", 16, PAGE_BODY, false, Gravity.START, 0, 0));
            return;
        }
        for (LearningModuleResponse module : fixedModules) {
            LinearLayout card = column(SOFT, Gravity.TOP);
            card.setPadding(dp(16), dp(14), dp(16), dp(16));
            card.setBackground(roundRect(SOFT, 14));
            card.addView(text(module.name, 18, DARK, true, Gravity.START, 0, 8));
            card.addView(text("Tipo: " + module.moduleType + "\nOrdem: " + module.sortOrder + "\nStatus: " + (module.active ? "ativo" : "inativo"),
                    14, TEXT, false, Gravity.START, 0, 12));
            TextView open = button("Abrir conteúdo", DARK, WHITE, 48);
            open.setOnClickListener(v -> showAdminModuleItems(module));
            card.addView(open, matchWrap(0, 0));
            list.addView(card, matchWrap(0, 12));
        }
    }

    private List<LearningModuleResponse> fixedTriviaModules(List<LearningModuleResponse> modules) {
        List<LearningModuleResponse> result = new ArrayList<>();
        LearningModuleResponse beginner = null;
        LearningModuleResponse intermediate = null;
        if (modules != null) {
            for (LearningModuleResponse module : modules) {
                if (module == null) {
                    continue;
                }
                if (MODULE_TRIVIA_BEGINNER.equals(module.id)) {
                    beginner = module;
                } else if (MODULE_TRIVIA_INTERMEDIATE.equals(module.id)) {
                    intermediate = module;
                }
            }
        }
        if (beginner != null) result.add(beginner);
        if (intermediate != null) result.add(intermediate);
        return result;
    }

    private void showAdminModuleItems(LearningModuleResponse module) {
        stopPractice();
        if (!isAdmin()) {
            showProgress();
            return;
        }
        screen = Screen.ADMIN_MODULE_ITEMS;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader(module.name == null ? "Módulo" : module.name, this::showAdminModules),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        TextView addQuestion = button("pergunta", DARK, WHITE, 52, R.drawable.ic_ms_add);
        addQuestion.setOnClickListener(v -> showModuleMultipleChoiceEditor(module, null));
        content.addView(addQuestion, matchWrap(0, 10));
        TextView addSong = button("música", SOFT, DARK, 52, R.drawable.ic_ms_add);
        addSong.setOnClickListener(v -> showModuleSongItemEditor(module, null));
        content.addView(addSong, matchWrap(0, 14));

        LinearLayout list = column(WHITE, Gravity.TOP);
        content.addView(list, matchWrap(0, 0));
        renderAdminModuleItems(list, module);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void renderAdminModuleItems(LinearLayout list, LearningModuleResponse module) {
        list.removeAllViews();
        if (module.items == null || module.items.isEmpty()) {
            list.addView(text("Nenhum item cadastrado neste módulo.", 16, PAGE_BODY, false, Gravity.START, 0, 0));
            return;
        }
        for (LearningModuleItemResponse item : module.items) {
            LinearLayout card = column(SOFT, Gravity.TOP);
            card.setPadding(dp(14), dp(12), dp(14), dp(14));
            card.setBackground(roundRect(SOFT, 14));
            String title = item.title == null || item.title.trim().isEmpty()
                    ? ("song".equals(item.itemType) ? "Música" : "Pergunta")
                    : item.title;
            card.addView(text(title, 17, DARK, true, Gravity.START, 0, 6));
            card.addView(text("Tipo: " + item.itemType + "\nOrdem: " + item.sortOrder + "\nStatus: " + (item.active ? "ativo" : "inativo"),
                    14, TEXT, false, Gravity.START, 0, 10));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setWeightSum(3f);
            TextView edit = button("Editar", DARK, WHITE, 44);
            TextView deactivate = button("Arquivar", Color.rgb(210, 130, 60), WHITE, 44);
            TextView delete = button("Excluir", Color.rgb(210, 75, 75), WHITE, 44);
            row.addView(edit, weightButton(0, 5));
            row.addView(deactivate, weightButton(5, 5));
            row.addView(delete, weightButton(5, 0));
            card.addView(row, matchWrap(0, 0));
            edit.setOnClickListener(v -> {
                if ("song".equals(item.itemType)) {
                    showModuleSongItemEditor(module, item);
                } else {
                    showModuleMultipleChoiceEditor(module, item);
                }
            });
            deactivate.setOnClickListener(v -> confirmDeactivateModuleItem(module, item));
            delete.setOnClickListener(v -> confirmDeleteModuleItem(module, item));
            list.addView(card, matchWrap(0, 12));
        }
    }

    private void confirmDeactivateModuleItem(LearningModuleResponse module, LearningModuleItemResponse item) {
        new AlertDialog.Builder(this)
                .setIcon(MaterialIcons.dialogWarning(this))
                .setTitle("Desativar item?")
                .setMessage("O item deixará de aparecer para os alunos.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Desativar", (dialog, which) -> saveModuleItem(module, item, false))
                .show();
    }

    private void confirmDeleteModuleItem(LearningModuleResponse module, LearningModuleItemResponse item) {
        new AlertDialog.Builder(this)
                .setIcon(MaterialIcons.dialogWarning(this))
                .setTitle("Excluir item?")
                .setMessage("O item sera removido do modulo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> DedilharteApiClient.service()
                        .deleteModuleItem(item.id)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {
                                Toast.makeText(MainActivity.this, response.isSuccessful() ? "Item excluido" : "Nao foi possivel excluir", Toast.LENGTH_SHORT).show();
                                if (response.isSuccessful()) {
                                    reloadAdminModule(module.id);
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                Toast.makeText(MainActivity.this, "Falha de conexao", Toast.LENGTH_SHORT).show();
                            }
                        }))
                .show();
    }

    private void showModuleMultipleChoiceEditor(LearningModuleResponse module, LearningModuleItemResponse existing) {
        if (!isAdmin()) {
            showProgress();
            return;
        }
        screen = Screen.ADMIN_MODULE_ITEM_EDITOR;
        root.removeAllViews();
        boolean editing = existing != null;
        List<String> existingOptions = existing != null && existing.multipleChoice != null && existing.multipleChoice.options != null
                ? existing.multipleChoice.options
                : new ArrayList<>();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader(editing ? "Editar pergunta" : "Nova pergunta", () -> showAdminModuleItems(module)),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));

        EditText title = input(editing ? safeText(existing.title) : "", "Título do item");
        EditText instructions = input(editing ? safeText(existing.instructions) : "", "Instruções");
        EditText question = input(editing && existing.multipleChoice != null ? safeText(existing.multipleChoice.question) : "", "Pergunta");
        EditText optionA = input(optionAt(existingOptions, 0), "Alternativa A");
        EditText optionB = input(optionAt(existingOptions, 1), "Alternativa B");
        EditText optionC = input(optionAt(existingOptions, 2), "Alternativa C");
        EditText optionD = input(optionAt(existingOptions, 3), "Alternativa D");
        Spinner correct = correctAnswerSpinner(0);
        if (editing && existing.multipleChoice != null) {
            correct.setSelection(Math.max(0, Math.min(3, existing.multipleChoice.correctIndex)));
        }
        EditText explanation = input(editing && existing.multipleChoice != null ? safeText(existing.multipleChoice.explanation) : "", "Explicação opcional");
        EditText sortOrder = input(editing ? String.valueOf(existing.sortOrder) : "", "Ordem");
        sortOrder.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        content.addView(title, matchWrap(0, 10));
        content.addView(instructions, matchWrap(0, 10));
        content.addView(question, matchWrap(0, 10));
        content.addView(optionA, matchWrap(0, 8));
        content.addView(optionB, matchWrap(0, 8));
        content.addView(optionC, matchWrap(0, 8));
        content.addView(optionD, matchWrap(0, 10));
        content.addView(correct, matchWrap(0, 10));
        content.addView(explanation, matchWrap(0, 10));
        content.addView(sortOrder, matchWrap(0, 12));

        TextView save = button("Salvar", DARK, WHITE, 56, R.drawable.ic_ms_save);
        save.setOnClickListener(v -> saveModuleMultipleChoiceItem(
                module, existing, title, instructions, question, optionA, optionB, optionC, optionD, correct, explanation, sortOrder, true
        ));
        content.addView(save, matchWrap(0, 0));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private static final class ModuleSongDraft {
        String title = "";
        String instructions = "";
        String order = "0";
        String songId;
    }

    private void showModuleSongItemEditor(LearningModuleResponse module, LearningModuleItemResponse existing) {
        ModuleSongDraft draft = new ModuleSongDraft();
        if (existing != null) {
            draft.title = safeText(existing.title);
            draft.instructions = safeText(existing.instructions);
            draft.order = String.valueOf(existing.sortOrder);
            draft.songId = existing.songId;
        }
        showModuleSongItemEditor(module, existing, draft);
    }

    private void showModuleSongItemEditor(LearningModuleResponse module, LearningModuleItemResponse existing, ModuleSongDraft draft) {
        if (!isAdmin()) { showProgress(); return; }
        screen = Screen.ADMIN_MODULE_ITEM_EDITOR;
        root.removeAllViews();
        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader(existing != null ? "Editar música do módulo" : "Nova música no módulo", () -> showAdminModuleItems(module)),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        EditText title = input(draft.title, "Título do item (opcional)");
        EditText instructions = input(draft.instructions, "Instruções");
        EditText sortOrder = input(draft.order, "Ordem");
        sortOrder.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        content.addView(title, matchWrap(0, 10));
        content.addView(text("Música", 16, PAGE_TEXT, true, Gravity.START, 0, 8));
        Spinner selector = simpleSpinner(new String[]{"Selecione uma música"});
        content.addView(selector, matchWrap(0, 10));
        TextView status = text("Carregando músicas…", 15, PAGE_BODY, false, Gravity.START, 0, 10);
        content.addView(status);
        TextView retry = button("RECARREGAR MÚSICAS", SOFT, DARK, 48, R.drawable.ic_ms_refresh);
        content.addView(retry, matchWrap(0, 10));
        TextView create = button("NOVA MÚSICA", CYAN, DARK, 48, R.drawable.ic_ms_add);
        content.addView(create, matchWrap(0, 10));
        content.addView(instructions, matchWrap(0, 10));
        content.addView(sortOrder, matchWrap(0, 12));
        TextView save = button("Salvar", DARK, WHITE, 56, R.drawable.ic_ms_save);
        content.addView(save, matchWrap(0, 0));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
        List<Song> choices = new ArrayList<>();
        Runnable load = () -> {
            selector.setEnabled(false);
            save.setEnabled(false);
            retry.setEnabled(false);
            status.setText("Carregando músicas…");
            songRepository.fetchAdmin((songs, success) -> {
                if (isDestroyed() || shell.getParent() != root) return;
                retry.setEnabled(true);
                choices.clear();
                if (!success) {
                    status.setText("Não foi possível carregar as músicas. Tente recarregar.");
                    return;
                }
                choices.addAll(songs);
                List<String> labels = new ArrayList<>();
                labels.add("Selecione uma música");
                int selected = 0;
                for (Song song : choices) {
                    labels.add(song.getTitle() + (song.getArtist().trim().isEmpty() ? "" : " - " + song.getArtist()));
                    if (song.getId().equals(draft.songId)) selected = labels.size() - 1;
                }
                ArrayAdapter<String> adapter = themedSpinnerAdapter(labels.toArray(new String[0]));
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                selector.setAdapter(adapter);
                selector.setSelection(selected);
                selector.setEnabled(!choices.isEmpty());
                save.setEnabled(!choices.isEmpty());
                status.setText(choices.isEmpty() ? "Nenhuma música cadastrada."
                        : draft.songId != null && selected == 0 ? "A música selecionada não está mais disponível. Selecione outra." : "");
            });
        };
        retry.setOnClickListener(v -> load.run());
        create.setOnClickListener(v -> {
            draft.title = title.getText().toString();
            draft.instructions = instructions.getText().toString();
            draft.order = sortOrder.getText().toString();
            int selected = selector.getSelectedItemPosition() - 1;
            if (selected >= 0 && selected < choices.size()) draft.songId = choices.get(selected).getId();
            showSongEditor(null, () -> showModuleSongItemEditor(module, existing, draft), saved -> {
                draft.songId = saved.getId();
                showModuleSongItemEditor(module, existing, draft);
            });
        });
        save.setOnClickListener(v -> {
            int selected = selector.getSelectedItemPosition() - 1;
            if (selected < 0 || selected >= choices.size()) {
                status.setText("Selecione uma música cadastrada.");
                return;
            }
            int order;
            try {
                order = Integer.parseInt(sortOrder.getText().toString().trim());
                if (order < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sortOrder.setError("Informe uma ordem inteira entre 0 e 2147483647.");
                return;
            }
            Song selectedSong = choices.get(selected);
            LearningModuleItemRequest request = new LearningModuleItemRequest("song", selectedSong.getId(),
                    title.getText().toString().trim().isEmpty() ? selectedSong.getTitle() : title.getText().toString().trim(),
                    instructions.getText().toString().trim(), order, existing == null || existing.active,
                    null, null, null, null);
            sendModuleItem(module, existing, request, save, status, retry, create, selector);
        });
        load.run();
    }
    private void saveModuleMultipleChoiceItem(
            LearningModuleResponse module,
            LearningModuleItemResponse existing,
            EditText title,
            EditText instructions,
            EditText question,
            EditText optionA,
            EditText optionB,
            EditText optionC,
            EditText optionD,
            Spinner correct,
            EditText explanation,
            EditText sortOrder,
            boolean active
    ) {
        List<String> options = new ArrayList<>();
        addOption(options, optionA);
        addOption(options, optionB);
        addOption(options, optionC);
        addOption(options, optionD);
        if (question.getText().toString().trim().isEmpty()) {
            question.setError("Informe a pergunta.");
            return;
        }
        if (options.size() < 2) {
            Toast.makeText(this, "Informe pelo menos duas alternativas.", Toast.LENGTH_SHORT).show();
            return;
        }
        LearningModuleItemRequest request = new LearningModuleItemRequest(
                "multiple_choice",
                null,
                title.getText().toString().trim(),
                instructions.getText().toString().trim(),
                parseIntOrZero(sortOrder.getText().toString()),
                active,
                question.getText().toString().trim(),
                options,
                correct.getSelectedItemPosition(),
                explanation.getText().toString().trim()
        );
        sendModuleItem(module, existing, request);
    }

    private void saveModuleItem(LearningModuleResponse module, LearningModuleItemResponse existing, boolean active) {
        if ("song".equals(existing.itemType)) {
            LearningModuleItemRequest request = new LearningModuleItemRequest(
                    "song", existing.songId, existing.title, existing.instructions, existing.sortOrder, active,
                    null, null, null, null
            );
            sendModuleItem(module, existing, request);
            return;
        }
        List<String> options = existing.multipleChoice == null || existing.multipleChoice.options == null
                ? new ArrayList<>()
                : existing.multipleChoice.options;
        LearningModuleItemRequest request = new LearningModuleItemRequest(
                "multiple_choice",
                null,
                existing.title,
                existing.instructions,
                existing.sortOrder,
                active,
                existing.multipleChoice == null ? "" : existing.multipleChoice.question,
                options,
                existing.multipleChoice == null ? 0 : existing.multipleChoice.correctIndex,
                existing.multipleChoice == null ? "" : existing.multipleChoice.explanation
        );
        sendModuleItem(module, existing, request);
    }

    private void sendModuleItem(LearningModuleResponse module, LearningModuleItemResponse existing, LearningModuleItemRequest request) {
        sendModuleItem(module, existing, request, null, null);
    }

    private void sendModuleItem(LearningModuleResponse module, LearningModuleItemResponse existing, LearningModuleItemRequest request,
                                TextView save, TextView status, View... controls) {
        for (View control : controls) control.setEnabled(false);
        if (save != null) { save.setEnabled(false); save.setText("Salvando…"); }
        if (status != null) status.setText("Salvando vínculo…");
        View editor = root.getChildAt(0);
        Call<LearningModuleItemResponse> call = existing == null
                ? DedilharteApiClient.service().createModuleItem(module.id, request)
                : DedilharteApiClient.service().updateModuleItem(existing.id, request);
        call.enqueue(new Callback<LearningModuleItemResponse>() {
            @Override
            public void onResponse(Call<LearningModuleItemResponse> call, Response<LearningModuleItemResponse> response) {
                if (isDestroyed() || editor.getParent() != root) return;
                if (save != null) { save.setEnabled(true); save.setText("Salvar"); }
                for (View control : controls) control.setEnabled(true);
                String message = response.isSuccessful() ? "Item salvo"
                        : com.example.dedilharte.network.ApiErrors.message(response, "Não foi possível salvar o vínculo.");
                if (status != null) status.setText(message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                if (response.isSuccessful()) {
                    reloadAdminModule(module.id);
                }
            }

            @Override
            public void onFailure(Call<LearningModuleItemResponse> call, Throwable t) {
                if (isDestroyed() || editor.getParent() != root) return;
                if (save != null) { save.setEnabled(true); save.setText("Salvar"); }
                for (View control : controls) control.setEnabled(true);
                if (status != null) status.setText("Falha de conexão. Tente novamente.");
                Toast.makeText(MainActivity.this, "Falha de conexão", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void reloadAdminModule(String moduleId) {
        DedilharteApiClient.service().getStaffModules().enqueue(new Callback<LearningModuleListResponse>() {
            @Override
            public void onResponse(Call<LearningModuleListResponse> call, Response<LearningModuleListResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().items != null) {
                    for (LearningModuleResponse module : response.body().items) {
                        if (moduleId.equals(module.id)) {
                            showAdminModuleItems(module);
                            return;
                        }
                    }
                }
                showAdminModules();
            }

            @Override
            public void onFailure(Call<LearningModuleListResponse> call, Throwable t) {
                showAdminModules();
            }
        });
    }

    private void addOption(List<String> options, EditText input) {
        String value = input.getText().toString().trim();
        if (!value.isEmpty()) {
            options.add(value);
        }
    }

    private String optionAt(List<String> options, int index) {
        return options != null && index >= 0 && index < options.size() ? options.get(index) : "";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private void showAdminSongs() {
        stopPractice();
        if (!isAdmin()) {
            showProgress();
            return;
        }
        screen = Screen.ADMIN_SONGS;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Gerenciar músicas", this::showAdminDashboard),
                new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        TextView newSong = button("NOVA MÚSICA", DARK, WHITE, 56, R.drawable.ic_ms_add);
        content.addView(newSong, matchWrap(0, 12));
        EditText search = input("", "Pesquisar por título");
        content.addView(search, matchWrap(0, 12));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setWeightSum(3f);
        TextView all = button("TODAS", SOFT, DARK, 46);
        TextView published = button("PUBLICADAS", SOFT, DARK, 46);
        TextView drafts = button("ARQUIVADAS", SOFT, DARK, 46);
        filters.addView(all, weightButton(0, 4));
        filters.addView(published, weightButton(4, 4));
        filters.addView(drafts, weightButton(4, 0));
        content.addView(filters, matchWrap(0, 12));

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = column(WHITE, Gravity.TOP);
        final String[] statusFilter = {""};
        final List<Song> currentSongs = new ArrayList<>();
        results.addView(text("Carregando músicas…", 16, PAGE_BODY, false, Gravity.START, 0, 0));
        scroll.addView(results);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());

        songRepository.fetchAdmin((songs, fromNetwork) -> {
            if (screen == Screen.ADMIN_SONGS && shell.getParent() == root) {
                currentSongs.clear();
                if (!fromNetwork) {
                    results.removeAllViews();
                    results.addView(text("Não foi possível carregar as músicas. Reabra esta tela para tentar novamente.",
                            16, PAGE_BODY, false, Gravity.START, 0, 0));
                    return;
                }
                currentSongs.addAll(songs);
                renderAdminSongs(results, search.getText().toString(), statusFilter[0], currentSongs);
            }
        });

        newSong.setOnClickListener(v -> showSongEditor(null));
        all.setOnClickListener(v -> {
            statusFilter[0] = "";
            renderAdminSongs(results, search.getText().toString(), statusFilter[0], currentSongs);
        });
        published.setOnClickListener(v -> {
            statusFilter[0] = Song.PUBLISHED;
            renderAdminSongs(results, search.getText().toString(), statusFilter[0], currentSongs);
        });
        drafts.setOnClickListener(v -> {
            statusFilter[0] = Song.ARCHIVED;
            renderAdminSongs(results, search.getText().toString(), statusFilter[0], currentSongs);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderAdminSongs(results, s.toString(), statusFilter[0], currentSongs);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void renderAdminSongs(LinearLayout results, String query, String statusFilter) {
        renderAdminSongs(results, query, statusFilter, songRepository.all());
    }

    private void renderAdminSongs(LinearLayout results, String query, String statusFilter, List<Song> songs) {
        results.removeAllViews();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Song song : songs) {
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
                    song.isActive() ? "publicada • no repertório" : "arquivada • fora do repertório",
                    13, TEAL, true, Gravity.START, 0, 10
            ));

            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setWeightSum(2f);
            TextView edit = button("EDITAR", DARK, WHITE, 44);
            TextView publish = button(song.isActive() ? "DESPUBLICAR" : "PUBLICAR", CYAN, WHITE, 44);
            row1.addView(edit, weightButton(0, 5));
            row1.addView(publish, weightButton(5, 0));
            card.addView(row1, matchWrap(8, 8));


            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setWeightSum(2f);

            TextView up = button("SUBIR", SOFT, DARK, 42);
            TextView down = button("DESCER", SOFT, DARK, 42);

            row2.addView(up, weightButton(0, 4));
            row2.addView(down, weightButton(4, 0));

            card.addView(row2, matchWrap(0, 8));


            LinearLayout row3 = new LinearLayout(this);
            row3.setOrientation(LinearLayout.HORIZONTAL);
            row3.setWeightSum(2f);
            TextView archive = button(song.isActive() ? "ARQUIVAR" : "RESTAURAR", PALE, DARK, 42);
            TextView delete = button("EXCLUIR", Color.rgb(210, 75, 75), WHITE, 42);
            row3.addView(archive, weightButton(0, 5));
            row3.addView(delete, weightButton(5, 0));
            card.addView(row3, matchWrap(0, 0));

            edit.setOnClickListener(v -> showSongEditor(song));
            publish.setOnClickListener(v -> {
                saveSongAndReload(song.withActive(!song.isActive()));
            });
            up.setOnClickListener(v -> {
                saveSongAndReload(song.withQueue(Math.max(0, song.getQueueOrder() - 1), song.isInQueue()));
            });

            down.setOnClickListener(v -> {
                saveSongAndReload(song.withQueue(
                        song.getQueueOrder() + 1,
                        song.isInQueue()
                ));
            });

            archive.setOnClickListener(v -> {
                saveSongAndReload(song.withActive(!song.isActive()));
            });

            delete.setOnClickListener(v -> confirmDeleteSong(song));

            results.addView(card, matchWrap(0, 12));
            shown++;
        }

        if (shown == 0) {
            results.addView(text(
                    "Nenhuma musica encontrada.",
                    16, PAGE_BODY, false, Gravity.CENTER, 24, 0
            ));
        }
    }


    private void saveSongAndReload(Song song) {
        songRepository.save(song, this::onSongOperationComplete);
    }

    private void onSongOperationComplete(boolean success) {
        if (isDestroyed()) return;
        if (success) {
            showAdminSongs();
        } else {
            Toast.makeText(this, "Não foi possível salvar a alteração no servidor. Tente novamente.", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDeleteSong(Song song) {
        new AlertDialog.Builder(this)
                .setIcon(MaterialIcons.dialogWarning(this))
                .setTitle("Excluir música")
                .setMessage("Deseja excluir definitivamente \"" + song.getTitle() + "\"?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    songRepository.delete(song.getId(), (success, error) -> {
                        if (isDestroyed()) return;
                        if (success) showAdminSongs();
                        else Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAdminArpeggios() {
        stopPractice();
        if (!isAdmin()) {
            showProgress();
            return;
        }
        screen = Screen.ADMIN_ARPEGGIOS;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Gerenciar arpejos", this::showAdminDashboard),
                new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        TextView add = button("NOVO ARPEJO", DARK, WHITE, 56, R.drawable.ic_ms_add);
        add.setOnClickListener(v -> showArpeggioEditor(null));
        content.addView(add, matchWrap(0, 12));

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = column(WHITE, Gravity.TOP);
        results.addView(text("Carregando arpejos…", 16, PAGE_BODY, false, Gravity.START, 0, 0));
        scroll.addView(results);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());

        if (arpeggioRepository != null) {
            arpeggioRepository.fetchAdmin((items, fromNetwork) -> {
                if (screen == Screen.ADMIN_ARPEGGIOS && shell.getParent() == root) {
                    if (!fromNetwork) {
                        results.removeAllViews();
                        results.addView(text("Não foi possível carregar os arpejos. Reabra esta tela para tentar novamente.",
                                16, PAGE_BODY, false, Gravity.START, 0, 0));
                        return;
                    }
                    renderAdminArpeggios(results, items);
                }
            });
        }
    }

    private void renderAdminArpeggios(LinearLayout results, List<ArpeggioResponse> items) {
        results.removeAllViews();
        if (items == null || items.isEmpty()) {
            results.addView(text("Nenhum arpejo cadastrado.", 16, PAGE_BODY, false, Gravity.CENTER, 24, 0));
            return;
        }
        for (ArpeggioResponse item : items) {
            LinearLayout card = column(SOFT, Gravity.TOP);
            card.setPadding(dp(14), dp(12), dp(14), dp(14));
            card.setBackground(roundRect(SOFT, 14));
            card.addView(text(item.title + (item.chord == null || item.chord.trim().isEmpty() ? "" : " • " + item.chord), 17, DARK, true, Gravity.START, 0, 6));
            card.addView(text(Song.difficultyLabel(item.difficulty) + (item.active ? " • ativo" : " • inativo"), 14, TEXT, false, Gravity.START, 0, 10));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setWeightSum(2f);
            TextView edit = button("EDITAR", DARK, WHITE, 44);
            TextView delete = button("EXCLUIR", Color.rgb(210, 75, 75), WHITE, 44);
            row.addView(edit, weightButton(0, 5));
            row.addView(delete, weightButton(5, 0));
            card.addView(row, matchWrap(0, 0));
            edit.setOnClickListener(v -> showArpeggioEditor(item));
            delete.setOnClickListener(v -> confirmDeleteArpeggio(item));
            results.addView(card, matchWrap(0, 12));
        }
    }

    private void confirmDeleteArpeggio(ArpeggioResponse arpeggio) {
        if (arpeggio == null || arpeggio.id == null || arpeggio.id.trim().isEmpty()) {
            Toast.makeText(this, "Arpejo invalido para exclusao", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setIcon(MaterialIcons.dialogWarning(this))
                .setTitle("Excluir arpejo \"" + safeText(arpeggio.title) + "\"?")
                .setMessage("Esta acao nao pode ser desfeita.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> {
                    if (arpeggioRepository != null) {
                        arpeggioRepository.delete(arpeggio.id, (success, code) -> {
                            Toast.makeText(this, success ? "Arpejo excluído" : "Não foi possível excluir", Toast.LENGTH_SHORT).show();
                            if (success) {
                                showAdminArpeggios();
                            }
                        });
                    }
                })
                .show();
    }

    private void showArpeggioEditor(ArpeggioResponse existing) {
        if (!isAdmin()) {
            showProgress();
            return;
        }
        screen = Screen.ADMIN_ARPEGGIO_EDITOR;
        root.removeAllViews();
        List<ArpeggioNote> notes = new ArrayList<>();
        if (existing != null && existing.notes != null) {
            for (ArpeggioNote note : existing.notes) {
                notes.add(new ArpeggioNote(note.string, note.fret));
            }
        }

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader(existing == null ? "Novo arpejo" : "Editar arpejo", this::showAdminArpeggios),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        EditText title = input(existing == null ? "" : existing.title, "Nome");
        EditText chord = input(existing == null ? "" : existing.chord, "Acorde");
        Spinner difficulty = difficultySpinner(existing == null ? Song.EASY : existing.difficulty);
        Switch active = new Switch(this);
        active.setText("Ativo");
        active.setTextColor(PAGE_TEXT);
        active.setTextSize(16);
        active.setChecked(existing == null || existing.active);
        LinearLayout notesBox = column(WHITE, Gravity.TOP);
        TextView preview = text("", 13, PAGE_BODY, false, Gravity.START, 0, 10);
        preview.setTypeface(Typeface.MONOSPACE);
        Runnable[] renderNotes = new Runnable[1];
        renderNotes[0] = () -> {
            notesBox.removeAllViews();
            notesBox.addView(text("Notas", 18, PAGE_TEXT, true, Gravity.START, 0, 10));
            for (int i = 0; i < notes.size(); i++) {
                final int index = i;
                ArpeggioNote note = notes.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setWeightSum(3f);
                row.addView(text("Corda " + note.string + " | Casa " + note.fret, 15, PAGE_TEXT, true, Gravity.CENTER_VERTICAL, 0, 0), new LinearLayout.LayoutParams(0, dp(46), 2f));
                TextView remove = button("EXCLUIR", Color.rgb(210, 75, 75), WHITE, 42);
                remove.setOnClickListener(v -> {
                    notes.remove(index);
                    renderNotes[0].run();
                });
                row.addView(remove, new LinearLayout.LayoutParams(0, dp(46), 1f));
                notesBox.addView(row, matchWrap(0, 8));
            }
            preview.setText(notes.isEmpty() ? "Prévia indisponível." : tablatureFromNotes(notesToMatrix(notes)));
        };

        EditText stringInput = input("", "Corda 1..6");
        stringInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText fretInput = input("", "Casa 0..");
        fretInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        TextView addNote = button("adicionar nota", SOFT, DARK, 48, R.drawable.ic_ms_add);
        addNote.setOnClickListener(v -> {
            int stringNumber = parseIntOrZero(stringInput.getText().toString());
            int fret = parseIntOrNegative(fretInput.getText().toString());
            if (stringNumber < 1 || stringNumber > 6 || fret < 0) {
                Toast.makeText(this, "Informe corda 1..6 e casa 0 ou maior.", Toast.LENGTH_SHORT).show();
                return;
            }
            notes.add(new ArpeggioNote(stringNumber, fret));
            stringInput.setText("");
            fretInput.setText("");
            renderNotes[0].run();
        });

        content.addView(title, matchWrap(0, 10));
        content.addView(chord, matchWrap(0, 10));
        content.addView(difficulty, matchWrap(0, 10));
        content.addView(active, matchWrap(0, 12));
        content.addView(stringInput, matchWrap(0, 8));
        content.addView(fretInput, matchWrap(0, 8));
        content.addView(addNote, matchWrap(0, 12));
        content.addView(notesBox, matchWrap(0, 8));
        content.addView(text("Preview da tablatura", 16, PAGE_TEXT, true, Gravity.START, 0, 8));
        content.addView(preview, matchWrap(0, 12));
        TextView save = button("SALVAR", DARK, WHITE, 56, R.drawable.ic_ms_save);
        save.setOnClickListener(v -> saveArpeggio(existing, title, chord, difficulty, active.isChecked(), notes));
        content.addView(save, matchWrap(0, 0));
        renderNotes[0].run();
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void saveArpeggio(ArpeggioResponse existing, EditText title, EditText chord, Spinner difficulty, boolean active, List<ArpeggioNote> notes) {
        String titleValue = title.getText().toString().trim();
        if (titleValue.isEmpty()) {
            title.setError("Informe o nome.");
            return;
        }
        if (notes.isEmpty()) {
            Toast.makeText(this, "Adicione pelo menos uma nota.", Toast.LENGTH_SHORT).show();
            return;
        }
        ArpeggioRequest request = new ArpeggioRequest(existing == null ? null : existing.id, titleValue, chord.getText().toString().trim(), notes, selectedDifficulty(difficulty), active, existing == null ? 0 : existing.sortOrder);
        arpeggioRepository.save(existing, request, (success, code) -> {
            Toast.makeText(this, success ? "Arpejo salvo" : "Não foi possível salvar", Toast.LENGTH_SHORT).show();
            if (success) {
                showAdminArpeggios();
            }
        });
    }

    private int[][] notesToMatrix(List<ArpeggioNote> notes) {
        int[][] matrix = new int[notes.size()][2];
        for (int i = 0; i < notes.size(); i++) {
            matrix[i][0] = notes.get(i).string;
            matrix[i][1] = notes.get(i).fret;
        }
        return matrix;
    }

    private void showSongEditor(Song song) {
        showSongEditor(song, this::showAdminSongs, saved -> showAdminSongs());
    }

    private void showSongEditor(Song song, Runnable onBack, java.util.function.Consumer<Song> onSaved) {
        if (!isAdmin()) {
            showProgress();
            return;
        }
        screen = Screen.ADMIN_EDITOR;
        root.removeAllViews();
        boolean editing = song != null;

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader(editing ? "Editar música" : "Nova música", onBack),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        EditText title = input(editing ? song.getTitle() : "", "Título");
        EditText artist = input(editing ? song.getArtist() : "", "Artista ou autor");
        Spinner difficulty = difficultySpinner(editing ? song.normalizedDifficulty() : Song.EASY);
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

        TextView preview = text("Prévia ainda não gerada", 13, PAGE_BODY, false, Gravity.START, 0, 10);
        preview.setTypeface(Typeface.MONOSPACE);
        HorizontalScrollView previewScroll = new HorizontalScrollView(this);
        previewScroll.addView(preview);
        content.addView(previewScroll, matchWrap(0, 12));

        TextView previewButton = button("Visualizar interpretação", SOFT, DARK, 52);
        TextView saveDraft = button("SALVAR ARQUIVADA", PALE, DARK, 52);
        TextView publish = button("PUBLICAR", DARK, WHITE, 56);
        content.addView(previewButton, matchWrap(0, 10));
        content.addView(saveDraft, matchWrap(0, 10));
        content.addView(publish, matchWrap(0, 0));

        previewButton.setOnClickListener(v -> updatePreview(preview, tablature.getText().toString(), parseBpmOrZero(bpm)));
        saveDraft.setOnClickListener(v -> saveSongFromEditor(song, title, artist, difficulty, bpm, tablature, Song.ARCHIVED, onSaved, saveDraft, publish));
        publish.setOnClickListener(v -> saveSongFromEditor(song, title, artist, difficulty, bpm, tablature, Song.PUBLISHED, onSaved, saveDraft, publish));

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
            preview.setText(friendlyTablatureError());
        }
    }

    private String friendlyTablatureError() {
        return "A tablatura nao esta em um formato valido. Verifique se as linhas utilizam | para delimitar as cordas.";
    }

    private void saveSongFromEditor(
            Song existing,
            EditText title,
            EditText artist,
            Spinner difficulty,
            EditText bpmInput,
            EditText tablature,
            String status,
            java.util.function.Consumer<Song> onSaved,
            TextView saveDraft,
            TextView publish
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
        if (tablature.getText().toString().trim().isEmpty()) {
            tablature.setError("Informe a tablatura ASCII.");
            return;
        }
        {
            try {
                TablatureParser.parse(tablature.getText().toString(), bpm, TablatureParser.DEFAULT_COLUMNS_PER_BEAT);
            } catch (IllegalArgumentException ex) {
                String message = friendlyTablatureError();
                tablature.setError(message);
                tablature.requestFocus();
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                return;
            }
        }
        long now = System.currentTimeMillis();
        Song song = new Song(
                existing == null ? null : existing.getId(),
                titleValue,
                artist.getText().toString().trim(),
                selectedDifficulty(difficulty),
                bpm,
                tablature.getText().toString(),
                existing == null ? songRepository.nextQueueOrder() : existing.getQueueOrder(),
                Song.PUBLISHED.equals(status),
                status,
                existing == null ? now : existing.getCreatedAt(),
                now
        );
        saveDraft.setEnabled(false);
        publish.setEnabled(false);
        TextView selectedButton = Song.PUBLISHED.equals(status) ? publish : saveDraft;
        selectedButton.setText("Salvando…");
        View editor = root.getChildAt(0);
        songRepository.save(song, existing == null, (saved, error) -> {
            if (isDestroyed() || editor.getParent() != root) return;
            saveDraft.setEnabled(true);
            publish.setEnabled(true);
            saveDraft.setText("SALVAR ARQUIVADA");
            publish.setText("PUBLICAR");
            if (saved != null) onSaved.accept(saved);
            else Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
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
        currentModule = null;
        currentModuleItem = null;
        root.removeAllViews();
        showProgressDashboard();
    }
    private void showProgressDashboard() {
        updateCalculatedLevel();
        LinearLayout shell = column(WHITE, Gravity.TOP);
        LinearLayout header = mainHeader();
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        ProgressDashboardView dashboard = new ProgressDashboardView(this, userName, level, weeklyGoal, sessionManager.getSongGoal(), sessionManager.getInitialLevel(),
                () -> openFixedTriviaModule(MODULE_TRIVIA_BEGINNER, "Trivia Iniciante"),
                () -> openFixedTriviaModule(MODULE_TRIVIA_INTERMEDIATE, "Trivia Intermediário"),
                this::showProgress);
        bindProfilePhoto(dashboard.avatar);
        scroll.addView(dashboard);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
        dashboardRepository.refresh((data, loading, notice) -> {
            if (isDestroyed() || shell.getParent() != root) return;
            dashboard.render(data, loading, notice);
            if (data != null && !loading && notice.isEmpty()) updateCalculatedLevel();
        });
    }
    private TextView dashboardSectionTitle(String value) {
        return text(value, 14, PAGE_ACCENT, true, Gravity.START, 6, 10);
    }

    private void showDynamicModule(LearningModuleResponse module) {
        stopPractice();
        screen = Screen.MODULE;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(mainHeader(), new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(16), dp(24), dp(16), dp(32));
        content.addView(text("Olá, " + firstName() + "!", 18, PAGE_ACCENT, false, Gravity.START, 0, 8));
        String moduleLevel = MODULE_TRIVIA_INTERMEDIATE.equals(module.id) ? LessonRepository.INTERMEDIATE : LessonRepository.BEGINNER;
        content.addView(text("Trilha " + moduleLevel, 30, PAGE_TEXT, false, Gravity.START, 0, 10));
        if (module.description != null && !module.description.trim().isEmpty()) {
            content.addView(text(module.description, 15, PAGE_BODY, false, Gravity.START, 0, 18));
        }
        List<LearningModuleItemResponse> lessons = new ArrayList<>();
        if (module.items != null) {
            for (LearningModuleItemResponse item : module.items) {
                if (item != null && item.active) lessons.add(item);
            }
        }
        java.util.Collections.sort(lessons, (left, right) -> Integer.compare(left.sortOrder, right.sortOrder));
        LinearLayout progress = column(WHITE, Gravity.TOP);
        content.addView(progress, matchWrap(0, 18));
        TextView resume = button("CONTINUAR", DARK, WHITE, 58);
        resume.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        resume.setVisibility(View.GONE);
        content.addView(resume, matchWrap(0, 24));
        content.addView(text("Aulas", 22, PAGE_TEXT, false, Gravity.START, 0, 12));
        LinearLayout cards = column(WHITE, Gravity.TOP);
        content.addView(cards, matchWrap(0, 16));
        TextView refresh = button("ATUALIZAR", DARK, WHITE, 48, R.drawable.ic_ms_refresh);
        refresh.setOnClickListener(v -> openFixedTriviaModule(module.id, module.name));
        content.addView(refresh, matchWrap(0, 16));
        content.addView(trackSwitchPanel(moduleLevel), matchWrap(0, 0));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
        if (lessons.isEmpty()) {
            progress.addView(progressPanel(0, 0, 0));
            cards.addView(text("Nenhum item ativo neste módulo.", 16, PAGE_BODY, false, Gravity.START, 0, 0));
            return;
        }
        progress.addView(infoCard("Seu progresso", "Carregando progresso…"));
        renderModuleLessonCards(cards, module, lessons, null);
        DedilharteApiClient.service().getModuleProgress().enqueue(new Callback<com.example.dedilharte.network.model.ModuleProgressListResponse>() {
            @Override
            public void onResponse(Call<com.example.dedilharte.network.model.ModuleProgressListResponse> call,
                                   Response<com.example.dedilharte.network.model.ModuleProgressListResponse> response) {
                if (isDestroyed() || shell.getParent() != root) return;
                if (!response.isSuccessful() || response.body() == null || response.body().items == null) {
                    showProgressError();
                    return;
                }
                Set<String> completed = new HashSet<>();
                for (com.example.dedilharte.network.model.ModuleProgressListResponse.Item record : response.body().items) {
                    if (record != null && record.completed) completed.add(record.itemId);
                }
                int count = 0;
                LearningModuleItemResponse next = null;
                int nextOrder = 0;
                for (int i = 0; i < lessons.size(); i++) {
                    LearningModuleItemResponse item = lessons.get(i);
                    if (completed.contains(item.id)) count++;
                    else if (next == null) { next = item; nextOrder = i + 1; }
                }
                progress.removeAllViews();
                progress.addView(progressPanel(count, lessons.size(), Math.round(count * 100f / lessons.size())));
                renderModuleLessonCards(cards, module, lessons, completed);
                if (next != null) {
                    LearningModuleItemResponse nextLesson = next;
                    resume.setText("CONTINUAR: AULA " + nextOrder);
                    resume.setContentDescription(resume.getText());
                    resume.setOnClickListener(v -> showModulePreview(module, nextLesson));
                    resume.setVisibility(View.VISIBLE);
                }
            }
            private void showProgressError() {
                progress.removeAllViews();
                progress.addView(infoCard("Seu progresso", "Não foi possível carregar o progresso. Toque em Atualizar para tentar novamente."));
                renderModuleLessonCards(cards, module, lessons, null);
            }
            @Override
            public void onFailure(Call<com.example.dedilharte.network.model.ModuleProgressListResponse> call, Throwable t) {
                if (!isDestroyed() && shell.getParent() == root) showProgressError();
            }
        });
    }

    private void renderModuleLessonCards(LinearLayout cards, LearningModuleResponse module,
                                         List<LearningModuleItemResponse> lessons, Set<String> completed) {
        cards.removeAllViews();
        for (int i = 0; i < lessons.size(); i++) {
            LearningModuleItemResponse item = lessons.get(i);
            boolean done = completed != null && completed.contains(item.id);
            String status = completed == null ? "STATUS INDISPONÍVEL" : done ? "CONCLUÍDA" : "NÃO CONCLUÍDA";
            String title = item.title == null || item.title.trim().isEmpty() ? "Aula" : item.title;
            TextView card = text((i + 1) + "ª AULA  •  " + status + "\n" + title,
                    17, WHITE, false, Gravity.CENTER_VERTICAL, 0, 0);
            card.setPadding(dp(17), dp(12), dp(17), dp(12));
            card.setMinHeight(dp(88));
            if (ThemeStore.isNight(this)) card.setTextColor(DARK);
            card.setBackground(pressable(done ? PALE : CYAN, DARK, 16, WHITE));
            MaterialIcons.leading(card, done ? R.drawable.ic_ms_check_circle : R.drawable.ic_ms_school, done ? PALE : CYAN);
            card.setContentDescription("Abrir aula: " + title);
            card.setFocusable(true);
            card.setOnClickListener(v -> showModulePreview(module, item));
            cards.addView(card, matchWrap(0, 14));
        }
    }
    private void showModulePreview(LearningModuleResponse module, LearningModuleItemResponse item) {
        stopPractice();
        screen = Screen.MODULE_PREVIEW;
        currentModule = module;
        currentModuleItem = item;
        currentLesson = null;
        progressStore.rememberLesson(module.id, item.id);
        root.removeAllViews();
        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Conteúdo da aula", () -> openFixedTriviaModule(module.id, module.name)),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(20), dp(24), dp(20), dp(32));
        content.addView(text(item.title == null ? "Aula" : item.title, 29, PAGE_TEXT, true, Gravity.START, 0, 16));
        if (item.instructions != null && !item.instructions.trim().isEmpty()) {
            content.addView(infoCard("INSTRUÇÕES", item.instructions), matchWrap(0, 16));
        }
        LinearLayout preview = column(WHITE, Gravity.TOP);
        content.addView(preview, matchWrap(0, 0));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
        if ("song".equals(item.itemType)) {
            preview.addView(text("Carregando conteúdo…", 16, PAGE_BODY, false, Gravity.START, 0, 16));
            if (item.song != null && item.song.isValid() && item.song.isPublished()
                    && item.song.id.equals(item.songId)) {
                renderModuleSongPreview(preview, module, item, songFromModule(item.song));
            } else {
                // Compatibility with servers that return only the linked song ID.
                songRepository.fetchPublishedQueuedWithErrors((songs, fromNetwork, error) -> {
                    if (isDestroyed() || shell.getParent() != root) return;
                    if (!songs.isEmpty()) {
                        for (Song song : songs) {
                            if (song.getId().equals(item.songId)) {
                                renderModuleSongPreview(preview, module, item, song);
                                return;
                            }
                        }
                    }
                    preview.removeAllViews();
                    preview.addView(text(fromNetwork
                            ? "A música vinculada não está disponível. Verifique a publicação da música."
                            : error,
                            16, PAGE_BODY, false, Gravity.START, 0, 16));
                    TextView retry = button("TENTAR NOVAMENTE", DARK, WHITE, 50, R.drawable.ic_ms_refresh);
                    retry.setOnClickListener(v -> showModulePreview(module, item));
                    preview.addView(retry);
                });
            }
        } else if (item.multipleChoice != null) {
            preview.addView(infoCard("CONTEÚDO", item.multipleChoice.question), matchWrap(0, 16));
            TextView start = button("INICIAR AULA", DARK, WHITE, 58, R.drawable.ic_ms_play_arrow);
            start.setOnClickListener(v -> {
                start.setVisibility(View.GONE);
                if (item.multipleChoice.options != null) {
                    for (int i = 0; i < item.multipleChoice.options.size(); i++) {
                        final int selected = i;
                        TextView option = button(item.multipleChoice.options.get(i), SOFT, DARK, 50);
                        option.setOnClickListener(answer -> submitModuleProgress(item, selected, option));
                        preview.addView(option, matchWrap(0, 10));
                    }
                }
            });
            preview.addView(start);
        } else {
            preview.addView(text("Esta aula ainda não tem conteúdo disponível.", 16, PAGE_BODY, false, Gravity.START, 0, 0));
        }
    }

    private Song songFromModule(com.example.dedilharte.network.model.SongResponse song) {
        return SongRepository.fromResponse(song);
    }

    private void renderModuleSongPreview(LinearLayout preview, LearningModuleResponse module,
                                         LearningModuleItemResponse item, Song song) {
        preview.removeAllViews();
        preview.addView(text(song.getTitle(), 23, PAGE_TEXT, true, Gravity.START, 0, 8));
        preview.addView(text(song.getArtist() + " • " + song.getDifficulty(), 16, PAGE_ACCENT, false, Gravity.START, 0, 8));
        preview.addView(text("BPM recomendado: " + song.getRecommendedBpm(), 16, PAGE_BODY, false, Gravity.START, 0, 16));
        if (song.getTablature() == null || song.getTablature().trim().isEmpty()) {
            preview.addView(text("Esta música ainda não possui tablatura cadastrada.", 16, PAGE_BODY, false, Gravity.START, 0, 0));
            return;
        }
        try {
            TablatureParser.parse(song.getTablature(), song.getRecommendedBpm(), TablatureParser.DEFAULT_COLUMNS_PER_BEAT);
        } catch (IllegalArgumentException invalid) {
            preview.addView(text("Tablatura inválida: " + invalid.getMessage(), 16, PAGE_BODY, false, Gravity.START, 0, 0));
            return;
        }
        preview.addView(text("Tablatura", 21, PAGE_TEXT, true, Gravity.START, 0, 12));
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        TextView tab = text(song.getTablature(), 16, PAGE_BODY, false, Gravity.START, 12, 12);
        tab.setTypeface(Typeface.MONOSPACE);
        tabScroll.addView(tab);
        preview.addView(tabScroll, matchWrap(0, 20));
        TextView start = button("INICIAR AULA", DARK, WHITE, 58, R.drawable.ic_ms_play_arrow);
        start.setOnClickListener(v -> showSongPractice(song, module, item));
        preview.addView(start);
    }

    private void submitModuleProgress(LearningModuleItemResponse item, Integer selected, TextView button) {
        final String owner = currentUserId;
        final String token = sessionManager.getToken();
        button.setEnabled(false);
        DedilharteApiClient.service().upsertModuleProgress(item.id, new ModuleProgressRequest(true, selected))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (!isCurrentProgressSession(owner, token)) return;
                        button.setEnabled(true);
                        if (response.isSuccessful()) {
                            dashboardRepository.invalidate();
                            button.setText(selected == null ? "AULA CONCLUÍDA" : "RESPOSTA ENVIADA");
                            MaterialIcons.leading(button, R.drawable.ic_ms_check_circle, selected == null ? CYAN : SOFT);
                            if (screen == Screen.PROGRESS) showProgress();
                        } else {
                            Toast.makeText(MainActivity.this, com.example.dedilharte.network.ApiErrors.message(
                                    response, "Não foi possível salvar o progresso."), Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        if (!isCurrentProgressSession(owner, token)) return;
                        button.setEnabled(true);
                        Toast.makeText(MainActivity.this, "Falha de conexão ao salvar o progresso. Tente novamente.", Toast.LENGTH_LONG).show();
                    }
                });
    }
    private void showSettings() {
        stopPractice();
        screen = Screen.SETTINGS;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Configurações", this::showProgress),
                new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView editProfile = settingsItem("Editar perfil", DARK, R.drawable.ic_ms_edit);
        editProfile.setOnClickListener(v -> showEditProfile());
        content.addView(editProfile, matchWrap(0, 12));

        LinearLayout darkRow = new LinearLayout(this);
        darkRow.setOrientation(LinearLayout.HORIZONTAL);
        darkRow.setGravity(Gravity.CENTER_VERTICAL);
        darkRow.setPadding(dp(16), 0, dp(16), 0);
        darkRow.setMinimumHeight(dp(64));
        darkRow.setBackground(roundRect(SOFT, 14));
        TextView darkLabel = text("Modo noturno", 17, DARK, true, Gravity.CENTER_VERTICAL, 0, 0);
        MaterialIcons.leading(darkLabel, R.drawable.ic_ms_dark_mode, SOFT);
        darkRow.addView(darkLabel, new LinearLayout.LayoutParams(0, -2, 1));
        androidx.appcompat.widget.SwitchCompat darkSwitch = new androidx.appcompat.widget.SwitchCompat(this);
        darkSwitch.setId(R.id.settingsDarkMode);
        darkSwitch.setMinHeight(dp(48));
        darkSwitch.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{DARK, WHITE}));
        darkSwitch.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{TEAL, Color.rgb(105, 132, 130)}));
        darkSwitch.setChecked(themeStore != null && themeStore.isDarkModeEnabled());
        darkSwitch.setContentDescription("Modo noturno");
        darkSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (themeStore != null) {
                themeStore.setDarkModeEnabled(isChecked);
            }
        });
        darkRow.addView(darkSwitch, new LinearLayout.LayoutParams(-2, -2));
        darkRow.setOnClickListener(v -> darkSwitch.toggle());
        content.addView(darkRow, matchWrap(0, 12));

        TextView logoutButton = settingsItem("Sair da conta", DARK, R.drawable.ic_ms_logout);
        logoutButton.setOnClickListener(v -> confirmLogout());
        content.addView(logoutButton, matchWrap(0, 12));

        TextView deleteButton = settingsItem("Excluir conta", ThemeStore.isNight(this) ? Color.rgb(139, 30, 30) : Color.rgb(188, 48, 48), R.drawable.ic_ms_delete);
        deleteButton.setOnClickListener(v -> confirmDeleteAccount());
        content.addView(deleteButton, matchWrap(0, 0));

        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setIcon(MaterialIcons.dialogWarning(this))
                .setTitle("Sair da conta?")
                .setMessage("Sua sessão local será encerrada.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair", (dialog, which) -> logout())
                .show();
    }

    private void showUserManagement() {
        stopPractice();
        if (!isConfigurator()) {
            showProgress();
            return;
        }
        screen = Screen.USER_MANAGEMENT;
        root.removeAllViews();
        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Gerenciar usuários", this::showConfiguratorDashboard),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        content.addView(text("Usuários cadastrados", 24, PAGE_TEXT, true, Gravity.START, 0, 12));
        LinearLayout list = column(WHITE, Gravity.TOP);
        content.addView(list, matchWrap(0, 0));
        list.addView(text("Carregando usuários...", 16, PAGE_BODY, false, Gravity.START, 0, 0));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());

        DedilharteApiClient.service().getManagedUsers().enqueue(new Callback<UserListResponse>() {
            @Override
            public void onResponse(Call<UserListResponse> call, Response<UserListResponse> response) {
                if (!response.isSuccessful()) {
                    renderManagedUsersError(
                            list,
                            response.code() == 401 || response.code() == 403
                                    ? "Sua conta nao tem permissao para listar usuarios."
                                    : "Nao foi possivel carregar os usuarios."
                    );
                    return;
                }
                List<UserResponse> users = response.body() == null ? new ArrayList<>() : response.body().items;
                renderManagedUsers(list, users == null ? new ArrayList<>() : users);
            }

            @Override
            public void onFailure(Call<UserListResponse> call, Throwable t) {
                renderManagedUsersError(list, "Nao foi possivel carregar os usuarios.");
            }
        });
    }

    private void renderManagedUsersError(LinearLayout list, String message) {
        list.removeAllViews();
        list.addView(text(message, 16, PAGE_BODY, false, Gravity.START, 0, 0));
    }

    private void renderManagedUsers(LinearLayout list, List<UserResponse> users) {
        list.removeAllViews();
        if (users == null) {
            list.addView(text("Não foi possível carregar os usuários.", 16, PAGE_BODY, false, Gravity.START, 0, 0));
            return;
        }
        if (users.isEmpty()) {
            list.addView(text("Nenhum usuário encontrado.", 16, PAGE_BODY, false, Gravity.START, 0, 0));
            return;
        }
        for (UserResponse user : users) {
            LinearLayout card = column(SOFT, Gravity.TOP);
            card.setPadding(dp(16), dp(14), dp(16), dp(16));
            card.setBackground(roundRect(SOFT, 14));
            String name = safeText(user.name).trim().isEmpty() ? "Usuário" : safeText(user.name);
            String email = safeText(user.email).trim().isEmpty() ? "Sem login" : safeText(user.email);
            String password = managedPassword(user);
            card.addView(text(name, 18, DARK, true, Gravity.START, 0, 6));
            card.addView(text(
                    "Login: " + email + "\nSenha: " + password,
                    14, TEXT, false, Gravity.START, 0, 12
            ));

            Spinner role = managedRoleSpinner(user.role);
            card.addView(role, matchWrap(0, 10));
            TextView save = button("SALVAR PERFIL", DARK, WHITE, 48, R.drawable.ic_ms_save);
            save.setOnClickListener(v -> updateManagedRole(user, selectedManagedRole(role)));
            card.addView(save, matchWrap(0, 0));
            list.addView(card, matchWrap(0, 12));
        }
    }

    private String managedPassword(UserResponse user) {
        if (user == null) {
            return "não disponível";
        }
        String password = safeText(user.temporaryPassword);
        if (password.trim().isEmpty()) {
            password = safeText(user.password);
        }
        return password.trim().isEmpty() ? "não disponível" : password;
    }

    private Spinner managedRoleSpinner(String selectedRole) {
        Spinner spinner = simpleSpinner(new String[]{"Aluno", "Admin", "Configurador"});
        if (ROLE_ADMIN.equals(selectedRole)) {
            spinner.setSelection(1);
        } else if (ROLE_CONFIGURATOR.equals(selectedRole)) {
            spinner.setSelection(2);
        } else {
            spinner.setSelection(0);
        }
        spinner.setMinimumHeight(dp(54));
        spinner.setBackground(roundRect(WHITE, 15));
        return spinner;
    }

    private String selectedManagedRole(Spinner spinner) {
        int position = spinner == null ? 0 : spinner.getSelectedItemPosition();
        if (position == 1) {
            return ROLE_ADMIN;
        }
        if (position == 2) {
            return ROLE_CONFIGURATOR;
        }
        return ROLE_STUDENT;
    }

    private void updateManagedRole(UserResponse user, String role) {
        if (user == null || user.id == null || user.id.trim().isEmpty()) {
            Toast.makeText(this, "Usuário inválido", Toast.LENGTH_SHORT).show();
            return;
        }
        DedilharteApiClient.service()
                .updateManagedRole(user.id, new RoleUpdateRequest(role))
                .enqueue(new Callback<UserResponse>() {
                    @Override
                    public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                        Toast.makeText(MainActivity.this,
                                response.isSuccessful() ? "Perfil atualizado" : "Não foi possível atualizar",
                                Toast.LENGTH_SHORT).show();
                        if (response.isSuccessful()) {
                            showUserManagement();
                        }
                    }

                    @Override
                    public void onFailure(Call<UserResponse> call, Throwable t) {
                        Toast.makeText(MainActivity.this, "Falha de conexão", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showCreateManagedUser() {
        stopPractice();
        root.removeAllViews();
        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Criar usuário", this::showConfiguratorDashboard),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        EditText name = input("", "Nome");
        EditText email = input("", "E-mail");
        EditText password = input("", "Senha temporária");
        Spinner role = simpleSpinner(new String[]{ROLE_STUDENT, ROLE_ADMIN, ROLE_CONFIGURATOR});
        content.addView(name, matchWrap(0, 10));
        content.addView(email, matchWrap(0, 10));
        content.addView(password, matchWrap(0, 10));
        content.addView(role, matchWrap(0, 12));
        TextView save = button("CRIAR", DARK, WHITE, 56, R.drawable.ic_ms_add);
        save.setOnClickListener(v -> DedilharteApiClient.service().createManagedUser(new ManagedUserRequest(
                name.getText().toString().trim(),
                email.getText().toString().trim(),
                password.getText().toString(),
                role.getSelectedItem().toString()
        )).enqueue(new ToastCallback<>("Usuário criado")));
        content.addView(save, matchWrap(0, 0));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private void showModuleManagement() {
        showAdminModules();
        if (System.currentTimeMillis() >= 0) {
            return;
        }
        stopPractice();
        if (!isAdmin()) {
            showConfiguratorDashboard();
            return;
        }
        screen = Screen.MODULE_MANAGEMENT;
        root.removeAllViews();
        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Gerenciar módulos", this::showAdminDashboard),
                new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(WHITE, Gravity.TOP);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        content.addView(text("Criar módulo", 24, PAGE_TEXT, true, Gravity.START, 0, 14));
        EditText name = input("", "Nome do módulo");
        Spinner type = simpleSpinner(new String[]{"trivia", "exercises"});
        EditText description = input("", "Descrição");
        EditText order = input("", "Ordem");
        order.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        content.addView(name, matchWrap(0, 10));
        content.addView(type, matchWrap(0, 10));
        content.addView(description, matchWrap(0, 10));
        content.addView(order, matchWrap(0, 12));
        TextView create = button("Criar módulo", DARK, WHITE, 56, R.drawable.ic_ms_add);
        create.setOnClickListener(v -> {
            int sortOrder = parseIntOrZero(order.getText().toString());
            DedilharteApiClient.service().createModule(new LearningModuleRequest(
                    name.getText().toString().trim(),
                    type.getSelectedItem().toString(),
                    description.getText().toString().trim(),
                    sortOrder,
                    true
            )).enqueue(new Callback<LearningModuleResponse>() {
                @Override
                public void onResponse(Call<LearningModuleResponse> call, Response<LearningModuleResponse> response) {
                    Toast.makeText(MainActivity.this, response.isSuccessful() ? "Módulo criado" : "Não foi possível criar", Toast.LENGTH_SHORT).show();
                    if (response.isSuccessful()) {
                        showModuleManagement();
                    }
                }

                @Override
                public void onFailure(Call<LearningModuleResponse> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "Falha de conexão", Toast.LENGTH_SHORT).show();
                }
            });
        });
        content.addView(create, matchWrap(0, 22));
        content.addView(text("Módulos ativos", 24, PAGE_TEXT, true, Gravity.START, 0, 16));
        LinearLayout list = column(WHITE, Gravity.TOP);
        content.addView(list, matchWrap(0, 0));
        fetchDynamicModules(() -> {
            list.removeAllViews();
            for (LearningModuleResponse module : dynamicModules) {
                list.addView(infoCard(module.name, "Tipo: " + module.moduleType + "\nOrdem: " + module.sortOrder), matchWrap(0, 10));
            }
        });
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
    }

    private TextView settingsItem(String label, int foreground, int icon) {
        TextView item = text(label, 17, foreground, true, Gravity.CENTER_VERTICAL, 0, 0);
        item.setMinHeight(dp(64));
        item.setPadding(dp(16), dp(12), dp(16), dp(12));
        item.setBackground(pressable(SOFT, PALE, 14, foreground));
        MaterialIcons.leading(item, icon, SOFT);
        item.setClickable(true);
        item.setFocusable(true);
        return item;
    }

    private void showAbout() {
        stopPractice();
        screen = Screen.ABOUT;
        currentLesson = null;
        root.removeAllViews();

        LinearLayout shell = column(TEAL, Gravity.TOP);
        shell.addView(pageHeader("Sobre", this::showProgress),
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

        TextView menu = text("", 0, WHITE, false, Gravity.CENTER, 0, 0);
        MaterialIcons.iconOnly(menu, R.drawable.ic_ms_menu, DARK, "Abrir menu");
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

        TextView back = text("", 0, WHITE, false, Gravity.CENTER, 0, 0);
        MaterialIcons.iconOnly(back, R.drawable.ic_ms_arrow_back, DARK, "Voltar");
        back.setContentDescription("Voltar");
        back.setOnClickListener(v -> backAction.run());
        bar.addView(back, new LinearLayout.LayoutParams(dp(58), -1));

        TextView title = text(titleValue, 19, WHITE, true, Gravity.CENTER_VERTICAL, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        return bar;
    }

    private void openDrawer() {
        final Dialog dialog = new Dialog(this, ThemeStore.isNight(this) ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        FrameLayout backdrop = new FrameLayout(this);
        backdrop.setBackgroundColor(Color.argb(135, 0, 0, 0));

        LinearLayout drawer = (LinearLayout) getLayoutInflater().inflate(R.layout.drawer_profile, backdrop, false);
        ImageView avatar = drawer.findViewById(R.id.drawerAvatar);
        TextView name = drawer.findViewById(R.id.drawerUserName);
        TextView currentLevel = drawer.findViewById(R.id.drawerLevel);
        ScrollView dynamicScroll = drawer.findViewById(R.id.drawerDynamicScroll);
        View dynamicTopDivider = drawer.findViewById(R.id.drawerDynamicTopDivider);
        View dynamicBottomDivider = drawer.findViewById(R.id.drawerDynamicBottomDivider);
        LinearLayout dynamic = drawer.findViewById(R.id.drawerDynamicModules);
        TextView progress = drawer.findViewById(R.id.drawerProgress);
        TextView course = drawer.findViewById(R.id.drawerCourse);
        TextView home = drawer.findViewById(R.id.drawerHome);

        home.setVisibility(View.VISIBLE);
        course.setVisibility(View.GONE);

        TextView arpeggios = drawer.findViewById(R.id.drawerArpeggios);
        TextView songs = drawer.findViewById(R.id.drawerSongs);
        TextView triviaBeginner = drawer.findViewById(R.id.drawerTriviaBeginner);
        TextView triviaIntermediate = drawer.findViewById(R.id.drawerTriviaIntermediate);
        TextView settings = drawer.findViewById(R.id.drawerSettings);
        TextView admin = drawer.findViewById(R.id.drawerAdmin);
        View adminDivider = drawer.findViewById(R.id.drawerAdminDivider);

        bindProfilePhoto(avatar);
        name.setText(userName);
        currentLevel.setText("Nível: " + level);


        hideDrawerModules(dynamicScroll, dynamicTopDivider, dynamicBottomDivider, dynamic);
        progress.setOnClickListener(v -> {
            dialog.dismiss();
            showProgress();
        });
        home.setOnClickListener(v -> {
            dialog.dismiss();
            showHome();
        });

        arpeggios.setOnClickListener(v -> {
            dialog.dismiss();
            showArpeggios();
        });
        songs.setOnClickListener(v -> {
            dialog.dismiss();
            showSongs();
        });
        triviaBeginner.setOnClickListener(v -> {
            dialog.dismiss();
            openFixedTriviaModule(MODULE_TRIVIA_BEGINNER, "Trivia Iniciante");
        });
        triviaIntermediate.setOnClickListener(v -> {
            dialog.dismiss();
            openFixedTriviaModule(MODULE_TRIVIA_INTERMEDIATE, "Trivia Intermediário");
        });
        settings.setOnClickListener(v -> {
            dialog.dismiss();
            showSettings();
        });
        admin.setText(isConfigurator() ? "Painel configurador" : "Administração");
        admin.setVisibility(isStaff() ? View.VISIBLE : View.GONE);
        adminDivider.setVisibility(isStaff() ? View.VISIBLE : View.GONE);
        admin.setOnClickListener(v -> {
            dialog.dismiss();
            if (isConfigurator()) {
                showConfiguratorDashboard();
            } else {
                showAdminDashboard();
            }
        });

        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * .72f),
                -1,
                Gravity.START
        );
        ScrollView drawerScroll = new ScrollView(this);
        drawerScroll.setFillViewport(true);
        drawerScroll.setBackgroundColor(DARK);
        drawerScroll.addView(drawer, new ScrollView.LayoutParams(-1, -2));
        backdrop.addView(drawerScroll, drawerParams);
        backdrop.setOnClickListener(v -> dialog.dismiss());
        drawer.setOnClickListener(v -> {
        });
        dialog.setContentView(backdrop);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        com.example.dedilharte.view.WindowInsetsHelper.dialog(dialog);
        if (window != null) {
            window.setLayout(-1, -1);
        }
    }

    private void hideDrawerModules(View dynamicScroll, View topDivider, View bottomDivider, LinearLayout dynamic) {
        if (dynamic != null) {
            dynamic.removeAllViews();
        }
        if (dynamicScroll != null) {
            dynamicScroll.setVisibility(View.GONE);
        }
        if (topDivider != null) {
            topDivider.setVisibility(View.GONE);
        }
        if (bottomDivider != null) {
            bottomDivider.setVisibility(View.GONE);
        }
    }

    private void openFixedTriviaModule(String moduleId, String moduleTitle) {
        stopPractice();
        screen = Screen.MODULE;
        currentLesson = null;
        root.removeAllViews();
        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(mainHeader(),
                new LinearLayout.LayoutParams(-1, dp(72)));
        shell.addView(text(moduleTitle, 24, PAGE_TEXT, true, Gravity.START, 24, 16));
        TextView status = text("Carregando conteúdo…", 16, PAGE_BODY, false, Gravity.START, 24, 24);
        shell.addView(status);
        TextView retry = button("TENTAR NOVAMENTE", DARK, WHITE, 48, R.drawable.ic_ms_refresh);
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> openFixedTriviaModule(moduleId, moduleTitle));
        shell.addView(retry);
        root.addView(shell, matchMatch());
        // Every opening/reload reads the server; never display cached or local lessons.
        DedilharteApiClient.service().getModules().enqueue(new Callback<LearningModuleListResponse>() {
            @Override
            public void onResponse(Call<LearningModuleListResponse> call, Response<LearningModuleListResponse> response) {
                if (isDestroyed() || shell.getParent() != root) return;
                if (!response.isSuccessful() || response.body() == null || response.body().items == null) {
                    status.setText("Não foi possível carregar o conteúdo.");
                    retry.setVisibility(View.VISIBLE);
                    return;
                }
                for (LearningModuleResponse module : response.body().items) {
                    if (module != null && module.active && moduleId.equals(module.id)) {
                        showDynamicModule(module);
                        return;
                    }
                }
                status.setText("Nenhum conteúdo cadastrado para esta trilha.");
                retry.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(Call<LearningModuleListResponse> call, Throwable t) {
                if (isDestroyed() || shell.getParent() != root) return;
                status.setText("Não foi possível carregar o conteúdo. Verifique sua conexão.");
                retry.setVisibility(View.VISIBLE);
            }
        });
    }

    private void fetchDynamicModules(Runnable onFinished) {
        DedilharteApiClient.service().getModules().enqueue(new Callback<LearningModuleListResponse>() {
            @Override
            public void onResponse(Call<LearningModuleListResponse> call, Response<LearningModuleListResponse> response) {
                dynamicModules.clear();
                if (response.isSuccessful() && response.body() != null && response.body().items != null) {
                    dynamicModules.addAll(fixedTriviaModules(response.body().items));
                }
                if (onFinished != null) {
                    onFinished.run();
                }
            }

            @Override
            public void onFailure(Call<LearningModuleListResponse> call, Throwable t) {
                dynamicModules.clear();
                if (onFinished != null) {
                    onFinished.run();
                }
            }
        });
    }

    private void renderDrawerModules(View dynamicScroll, View topDivider, View bottomDivider, LinearLayout dynamic, Dialog dialog) {
        if (dynamic == null) {
            return;
        }
        dynamic.removeAllViews();
        boolean hasVisibleModules = false;
        for (LearningModuleResponse module : dynamicModules) {
            if (module == null || !module.active) {
                continue;
            }
            hasVisibleModules = true;
            TextView item = text(module.name == null ? "Modulo" : module.name, 17, WHITE, true, Gravity.CENTER_VERTICAL, 0, 0);
            item.setMinHeight(dp(58));
            item.setPadding(dp(22), 0, dp(18), 0);
            item.setBackgroundResource(R.drawable.bg_drawer_item);
            item.setOnClickListener(v -> {
                dialog.dismiss();
                openDynamicModule(module);
            });
            dynamic.addView(item, new LinearLayout.LayoutParams(-1, dp(58)));
            View divider = new View(this);
            divider.setBackgroundColor(Color.argb(210, 255, 255, 255));
            dynamic.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        int visibility = hasVisibleModules ? View.VISIBLE : View.GONE;
        if (dynamicScroll != null) {
            dynamicScroll.setVisibility(visibility);
        }
        if (topDivider != null) {
            topDivider.setVisibility(visibility);
        }
        if (bottomDivider != null) {
            bottomDivider.setVisibility(visibility);
        }
    }
    private void openDynamicModule(LearningModuleResponse module) {
        if (module == null) {
            return;
        }

        openFixedTriviaModule(module.id, module.name);
    }
    private void showEditProfile() {
        stopPractice();
        screen = Screen.EDIT_PROFILE;
        root.removeAllViews();
        LinearLayout shell = column(WHITE, Gravity.TOP);
        shell.addView(pageHeader("Editar perfil", this::leaveEditProfile), new LinearLayout.LayoutParams(-1, dp(64)));
        View panel = getLayoutInflater().inflate(R.layout.dialog_edit_profile, null, false);
        shell.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(shell, matchMatch());
        com.example.dedilharte.view.PreferencesFields preferences = new com.example.dedilharte.view.PreferencesFields(this, sessionManager.getInitialLevel(), weeklyGoal, sessionManager.getSongGoal(), sessionManager.notificationsEnabled(), false);
        ((LinearLayout) panel.findViewById(R.id.editPreferences)).addView(preferences);
        panel.findViewById(R.id.editPassword).setOnClickListener(v -> com.example.dedilharte.view.PasswordDialogs.change(this, DedilharteApiClient.service(), this::logout));
        ImageView avatar = panel.findViewById(R.id.editAvatar);
        TextView photoButton = panel.findViewById(R.id.editPhotoButton);
        EditText name = panel.findViewById(R.id.editName);
        TextView goalMinus = panel.findViewById(R.id.editGoalMinus);
        TextView goalValue = panel.findViewById(R.id.editGoalValue);
        TextView goalPlus = panel.findViewById(R.id.editGoalPlus);
        MaterialIcons.iconOnly(goalMinus, R.drawable.ic_ms_remove, DARK, "Diminuir meta semanal");
        MaterialIcons.iconOnly(goalPlus, R.drawable.ic_ms_add, DARK, "Aumentar meta semanal");
        TextView save = panel.findViewById(R.id.editSave);
        MaterialIcons.leading(photoButton, R.drawable.ic_ms_edit, DARK);
        // The primary action is text-only; compound icons used to touch the rounded edge.
        TextView cancel = panel.findViewById(R.id.editCancel);
        editingWeeklyGoal = StudentMetrics.clampGoal(weeklyGoal);

        profilePhotoPreview = avatar;
        bindProfilePhoto(profilePhotoPreview);
        name.setText(userName);
        updateGoalLabel(goalValue, editingWeeklyGoal);

        photoButton.setOnClickListener(v -> openProfilePhotoPicker());
        goalMinus.setOnClickListener(v -> {
            editingWeeklyGoal = Math.max(1, editingWeeklyGoal - 1);
            updateGoalLabel(goalValue, editingWeeklyGoal);
        });
        goalPlus.setOnClickListener(v -> {
            editingWeeklyGoal = Math.min(7, editingWeeklyGoal + 1);
            updateGoalLabel(goalValue, editingWeeklyGoal);
        });
        final String owner = currentUserId, token = sessionManager.getToken();
        save.setOnClickListener(v -> {
            String typed = name.getText().toString().trim();
            if (typed.length() < 2 || typed.length() > 180) { name.setError("Use de 2 a 180 caracteres"); return; }
            int songs = preferences.songs(); if (songs < 1) return;
            com.example.dedilharte.network.model.UserRequest request = new com.example.dedilharte.network.model.UserRequest(owner, typed, editingWeeklyGoal, System.currentTimeMillis());
            request.initialLevel = preferences.level(); request.songGoal = songs; request.notificationsEnabled = preferences.notifications.isChecked();
            save.setEnabled(false); save.setText("Salvando…"); name.setEnabled(false); preferences.setInputsEnabled(false);
            goalMinus.setEnabled(false); goalPlus.setEnabled(false); panel.findViewById(R.id.editPassword).setEnabled(false);
            retrofit2.Call<UserResponse> call = DedilharteApiClient.service().updateProfile(request, "Bearer " + token); profileSaveCall = call;
            call.enqueue(new retrofit2.Callback<UserResponse>() {
                private boolean available() { return isCurrentProgressSession(owner, token) && !isDestroyed()
                        && screen == Screen.EDIT_PROFILE && shell.getParent() == root; }
                private void failed(String message) {
                    if (!available()) return;
                    save.setEnabled(true); save.setText("Salvar Edições"); name.setEnabled(true); preferences.setInputsEnabled(true);
                    goalMinus.setEnabled(true); goalPlus.setEnabled(true); panel.findViewById(R.id.editPassword).setEnabled(true);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
                @Override public void onResponse(retrofit2.Call<UserResponse> call, retrofit2.Response<UserResponse> response) {
                    if (!available()) return;
                    UserResponse user = response.body();
                    if (!response.isSuccessful() || user == null || !owner.equals(user.id)) {
                        failed(com.example.dedilharte.network.ApiErrors.message(response, "Não foi possível salvar o perfil. Tente novamente.")); return;
                    }
                    userName = user.name; weeklyGoal = StudentMetrics.clampGoal(user.weeklyGoal);
                    sessionManager.updatePreferences(user.initialLevel, user.songGoal, user.notificationsEnabled);
                    profile.edit().putLong(accountKey(currentUserId, "server_updated_at"), user.updatedAtMillis).apply();
                    setActiveUserUpdatedAt(user.updatedAtMillis); saveActiveProfile();
                    dashboardRepository.updateWeeklyGoal(weeklyGoal);
                    profileSaveCall = null; profilePhotoPreview = null; showProgress();
                    if (user.notificationsEnabled) com.example.dedilharte.auth.NotificationPermissions.requestIfNeeded(MainActivity.this);
                }
                @Override public void onFailure(retrofit2.Call<UserResponse> call, Throwable error) {
                    if (!call.isCanceled()) failed("Sem conexão. As alterações não foram salvas. Tente novamente.");
                }
            });
        });
        cancel.setOnClickListener(v -> leaveEditProfile());
    }

    private void leaveEditProfile() {
        if (profileSaveCall != null) profileSaveCall.cancel();
        profileSaveCall = null;
        profilePhotoPreview = null;
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), root)
                .hide(androidx.core.view.WindowInsetsCompat.Type.ime());
        showSettings();
    }

    private void updateGoalLabel(TextView view, int goal) {
        view.setText(goal + (goal == 1 ? " dia por semana" : " dias por semana"));
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
        uploadSelectedProfilePhoto(selectedPhoto);
        if (profilePhotoPreview != null) {
            bindProfilePhoto(profilePhotoPreview);
        }
    }
    private TextView lessonCard(Lesson lesson) {
        boolean completed = progressStore.isCompleted(lesson.getId());
        String status = completed ? "Concluída" : "Não concluída";
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
        if (ThemeStore.isNight(this)) card.setTextColor(DARK);
        card.setBackground(pressable(completed ? PALE : CYAN, DARK, 16, WHITE));
        MaterialIcons.leading(card, completed ? R.drawable.ic_ms_check_circle : R.drawable.ic_ms_school, completed ? PALE : CYAN);
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

    private TextView songLearnedButton(Song song) {
        boolean learned = songProgressStore != null && songProgressStore.isLearned(song.getId());
        TextView view = button(
                learned ? "MÚSICA APRENDIDA" : "APRENDI ESTA MÚSICA",
                learned ? PALE : CYAN,
                learned ? DARK : WHITE,
                54
        );
        view.setPadding(dp(14), dp(8), dp(14), dp(8));
        MaterialIcons.leading(view, R.drawable.ic_ms_check_circle, learned ? PALE : CYAN);
        view.setOnClickListener(v -> {
            boolean newValue = songProgressStore == null || !songProgressStore.isLearned(song.getId());
            final String owner = currentUserId;
            final String token = sessionManager.getToken();
            view.setEnabled(false);
            DedilharteApiClient.service().upsertSongProgress(
                    new com.example.dedilharte.network.model.SongProgressRequest(song.getId(), newValue))
                    .enqueue(new Callback<SongProgressResponse>() {
                        @Override public void onResponse(Call<SongProgressResponse> call, Response<SongProgressResponse> response) {
                            if (!isCurrentProgressSession(owner, token)) return;
                            view.setEnabled(true);
                            SongProgressResponse saved = response.body();
                            if (!response.isSuccessful() || saved == null || !owner.equals(saved.user_id)
                                    || !song.getId().equals(saved.song_id)) {
                                showError(); return;
                            }
                            songProgressRevision++;
                            songProgressStore.setLearned(song.getId(), saved.learned);
                            dashboardRepository.invalidate();
                            view.setText(saved.learned ? "MÚSICA APRENDIDA" : "APRENDI ESTA MÚSICA");
                            view.setTextColor(ThemeStore.isNight(MainActivity.this) || saved.learned ? DARK : WHITE);
                            view.setBackground(pressable(saved.learned ? PALE : CYAN, DARK, 16, saved.learned ? DARK : WHITE));
                            MaterialIcons.leading(view, R.drawable.ic_ms_check_circle, saved.learned ? PALE : CYAN);
                            if (screen == Screen.PROGRESS) showProgress();
                        }
                        @Override public void onFailure(Call<SongProgressResponse> call, Throwable error) {
                            if (!isCurrentProgressSession(owner, token)) return;
                            view.setEnabled(true); showError();
                        }
                        private void showError() {
                            Toast.makeText(MainActivity.this, "Não foi possível salvar. Verifique a conexão e tente novamente.", Toast.LENGTH_LONG).show();
                        }
                    });
        });
        return view;
    }

    private boolean isCurrentProgressSession(String owner, String token) {
        return !isDestroyed() && owner.equals(currentUserId) && owner.equals(sessionManager.getUserId())
                && token.equals(sessionManager.getToken());
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
        if (ThemeStore.isNight(this)) numberView.setTextColor(DARK);
        row.addView(numberView, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView body = text(value, 15, TEXT, false, Gravity.START, 0, 0);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, -2, 1);
        bodyParams.setMargins(dp(12), 0, 0, 0);
        row.addView(body, bodyParams);
        return row;
    }

    private TextView statusPill(String label, int color, int icon) {
        TextView pill = text(label, 12, WHITE, true, Gravity.CENTER, 0, 0);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setBackground(roundRect(color, 20));
        if (ThemeStore.isNight(this)) pill.setTextColor(ThemeStore.foreground(this, color));
        MaterialIcons.leading(pill, icon, color);
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
            image.setImageDrawable(MaterialIcons.drawable(this, R.drawable.ic_ms_person, DARK));
            image.setScaleType(ImageView.ScaleType.CENTER);
            image.setColorFilter(DARK);
            fetchRemoteProfilePhoto(image);
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
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(circleCrop(source));
        } catch (Exception ignored) {
            image.setImageDrawable(MaterialIcons.drawable(this, R.drawable.ic_ms_person, DARK));
            image.setScaleType(ImageView.ScaleType.CENTER);
            image.setColorFilter(DARK);
        }
    }

    private void uploadSelectedProfilePhoto(Uri selectedPhoto) {
        try {
            InputStream stream = getContentResolver().openInputStream(selectedPhoto);
            Bitmap source = BitmapFactory.decodeStream(stream);
            if (stream != null) {
                stream.close();
            }
            if (source == null) {
                return;
            }
            Bitmap scaled = scaleBitmap(source, 512);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, output);
            byte[] bytes = output.toByteArray();
            RequestBody body = RequestBody.create(bytes, MediaType.parse("image/jpeg"));
            MultipartBody.Part part = MultipartBody.Part.createFormData("photo", "profile.jpg", body);
            DedilharteApiClient.service().uploadMyPhoto(part).enqueue(new Callback<UserResponse>() {
                @Override
                public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                    if (!response.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "Foto salva localmente; upload falhou", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<UserResponse> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "Foto salva localmente; sem conexão", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception ignored) {
            Toast.makeText(this, "Não foi possível preparar a foto", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap scaleBitmap(Bitmap source, int maxSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxSize && height <= maxSize) {
            return source;
        }
        float ratio = Math.min(maxSize / (float) width, maxSize / (float) height);
        return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(width * ratio)), Math.max(1, Math.round(height * ratio)), true);
    }

    private void fetchRemoteProfilePhoto(ImageView image) {
        if (remotePhotoRequested) {
            return;
        }
        remotePhotoRequested = true;
        DedilharteApiClient.service().getMyPhoto().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        return;
                    }
                    byte[] bytes = response.body().bytes();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        image.clearColorFilter();
                        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        image.setImageBitmap(bitmap);
                    }
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
        });
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
        layout.setBackgroundColor(background == WHITE || (ThemeStore.isNight(this) && background == TEAL) ? PAGE : background);
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
        view.setTextColor(ThemeStore.isNight(this) && color == TEAL ? DARK : color);
        view.setGravity(gravity);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setPadding(0, dp(top), 0, dp(bottom));
        return view;
    }

    private TextView button(String label, int background, int foreground, int height) {
        TextView view = text(label, 17, foreground, true, Gravity.CENTER, 0, 0);
        view.setBackground(pressable(background, DARK, 16, foreground));
        if (ThemeStore.isNight(this)) view.setTextColor(ThemeStore.foreground(this, background == WHITE ? SOFT : background));
        view.setMinHeight(dp(height));
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription(label);
        return view;
    }

    private TextView button(String label, int background, int foreground, int height, int icon) {
        TextView view = button(label, background, foreground, height);
        view.setPadding(dp(14), dp(8), dp(14), dp(8));
        MaterialIcons.leading(view, icon, background);
        return view;
    }

    private EditText input(String value, String hint) {
        EditText input = new EditText(this);
        input.setTextColor(DARK);
        input.setHintTextColor(ThemeStore.isNight(this) ? DARK : Color.GRAY);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundRect(WHITE, 15));
        input.setMinHeight(dp(54));
        input.setHint(hint);
        input.setText(value);
        return input;
    }

    private Spinner difficultySpinner(String selectedDifficulty) {
        Spinner spinner = new Spinner(this);
        String[] labels = {"Fácil", "Média", "Difícil"};
        ArrayAdapter<String> adapter = themedSpinnerAdapter(labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String normalized = Song.normalizeDifficulty(selectedDifficulty);
        if (Song.MEDIUM.equals(normalized)) {
            spinner.setSelection(1);
        } else if (Song.HARD.equals(normalized)) {
            spinner.setSelection(2);
        } else {
            spinner.setSelection(0);
        }
        spinner.setMinimumHeight(dp(54));
        spinner.setBackground(roundRect(WHITE, 15));
        return spinner;
    }

    private ArrayAdapter<String> themedSpinnerAdapter(String[] labels) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            private View tint(View view) {
                if (ThemeStore.isNight(MainActivity.this)) {
                    ((TextView) view).setTextColor(DARK);
                    view.setBackgroundColor(SOFT);
                }
                return view;
            }
            @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                return tint(super.getView(position, recycled, parent));
            }
            @Override public View getDropDownView(int position, View recycled, android.view.ViewGroup parent) {
                return tint(super.getDropDownView(position, recycled, parent));
            }
        };
    }

    private Spinner simpleSpinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = themedSpinnerAdapter(labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private Spinner correctAnswerSpinner(int selectedIndex) {
        Spinner spinner = new Spinner(this);
        String[] labels = {"Alternativa A", "Alternativa B", "Alternativa C", "Alternativa D"};
        ArrayAdapter<String> adapter = themedSpinnerAdapter(labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(3, selectedIndex)));
        spinner.setMinimumHeight(dp(54));
        spinner.setBackground(roundRect(WHITE, 15));
        return spinner;
    }

    private String selectedDifficulty(Spinner spinner) {
        int position = spinner == null ? 0 : spinner.getSelectedItemPosition();
        if (position == 1) {
            return Song.MEDIUM;
        }
        if (position == 2) {
            return Song.HARD;
        }
        return Song.EASY;
    }

    private int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int parseIntOrNegative(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private StateListDrawable pressable(int normal, int pressed, int radius, int strokeColor) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_pressed},
                borderedRect(ThemeStore.isNight(this) ? normal : pressed, radius, strokeColor, 0)
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
        drawable.setColor(color == WHITE && ThemeStore.isNight(this) ? SOFT : color);
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
            cancelPracticeAudio();
        }
        lastPlayedPosition[0] = -1;
        state.setText("Pausado");
        MaterialIcons.iconOnly(startPause, R.drawable.ic_ms_play_arrow, DARK, "Continuar música");
        startPause.setContentDescription("Continuar música");
    }

    private void bindPracticeAudio(TextView state, TextView startPause, String playLabel) {
        GuitarSoundPlayer player = guitarSoundPlayer;
        GuitarPracticeView view = practiceView;
        player.prepare(view.getEvents(), view.getCurrentEventIndex(), view.isLooping());
        player.setOnStateChangeListener(audioState -> {
            if (guitarSoundPlayer != player || practiceView != view) return;
            if (audioState == GuitarSoundPlayer.State.READY && resumeAfterAudioLoad) {
                resumeAfterAudioLoad = false;
                view.start();
            } else if (audioState == GuitarSoundPlayer.State.READY && previewAfterAudioLoad) {
                previewAfterAudioLoad = false;
                playCurrentPracticeEvent(state);
            } else if (!view.isRunning()) {
                state.setText(player.statusText());
                if (!resumeAfterAudioLoad) {
                    setPracticePlayIcon(startPause, playLabel);
                    startPause.setContentDescription(playLabel == null ? "Continuar música"
                            : "PLAY".equals(playLabel) ? "Continuar arpejo" : "Iniciar treino");
                }
            }
            if (audioState == GuitarSoundPlayer.State.ERROR) {
                view.pause();
                cancelPracticeAudio();
                state.setText(player.statusText());
                setPracticePlayIcon(startPause, playLabel);
            }
        });
    }

    private void setPracticePlayIcon(TextView button, String label) {
        if (label == null) MaterialIcons.iconOnly(button, R.drawable.ic_ms_play_arrow, DARK, "Continuar música");
        else button.setText(label);
    }

    private void cancelPracticeAudio() {
        resumeAfterAudioLoad = false;
        previewAfterAudioLoad = false;
        if (guitarSoundPlayer != null) guitarSoundPlayer.stopAll();
    }

    private void playCurrentPracticeEvent(TextView state) {
        if (practiceView == null || guitarSoundPlayer == null) {
            return;
        }
        guitarSoundPlayer.prepare(practiceView.getEvents(), practiceView.getCurrentEventIndex(), practiceView.isLooping());
        if (!soundEnabled) {
            state.setText("Som desligado");
            return;
        }
        TablatureEvent event = practiceView.getCurrentTabEvent();
        if (guitarSoundPlayer.isReady()) {
            guitarSoundPlayer.play(event);
            state.setText("Reproduzindo");
        } else {
            if (guitarSoundPlayer.getState() == GuitarSoundPlayer.State.LOADING) {
                resumeAfterAudioLoad = practiceView.isRunning();
                previewAfterAudioLoad = !resumeAfterAudioLoad;
            }
            practiceView.pause();
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
        view.setTextColor(ThemeStore.isNight(this) ? DARK : selected ? WHITE : DARK);
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

    private boolean isConfigurator() {
        return ROLE_CONFIGURATOR.equals(userRole);
    }

    private boolean isStaff() {
        return isAdmin() || isConfigurator();
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
        cancelPracticeAudio();
        if (practiceView != null) {
            practiceView.pause();
            practiceView = null;
        }
        if (guitarSoundPlayer != null) {
            guitarSoundPlayer.release();
            guitarSoundPlayer = null;
        }
    }

    private final class ToastCallback<T> implements Callback<T> {
        private final String successMessage;

        private ToastCallback(String successMessage) {
            this.successMessage = successMessage;
        }

        @Override
        public void onResponse(Call<T> call, Response<T> response) {
            Toast.makeText(MainActivity.this,
                    response.isSuccessful() ? successMessage : "Operação não concluída",
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onFailure(Call<T> call, Throwable t) {
            Toast.makeText(MainActivity.this, "Falha de conexão", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleBackNavigation() {
        switch (screen) {
            case EDIT_PROFILE:
                leaveEditProfile();
                break;
            case HOME:
                showProgress();
                break;
            case MODULE_PREVIEW:
                openFixedTriviaModule(currentModule.id, currentModule.name);
                break;
            case WELCOME:
                finish();
                break;
            case LEVEL:
            case COURSE:
            case MODULE:
                showProgress();
                break;
            case REGISTER:
                showWelcome();
                break;
            case LESSON:
                showProgress();
                break;
            case PRACTICE:
                returnFromPractice();
                break;
            case ARPEGGIOS:
            case SONGS:
            case SETTINGS:
            case ABOUT:
            default:
                if (isConfigurator()) {
                    showConfiguratorDashboard();
                } else if (isAdmin()) {
                    showAdminDashboard();
                } else {
                    showProgress();
                }
                break;
            case PROGRESS:
                if (isConfigurator()) {
                    showConfiguratorDashboard();
                } else if (isAdmin()) {
                    showAdminDashboard();
                } else {
                    finish();
                }
                break;
            case ADMIN_PANEL:
                finish();
                break;
            case ADMIN_SONGS:
            case ADMIN_ARPEGGIOS:
            case ADMIN_MODULES:
                showAdminDashboard();
                break;
            case ADMIN_MODULE_ITEMS:
            case ADMIN_MODULE_ITEM_EDITOR:
                showAdminModules();
                break;
            case ADMIN_EDITOR:
                showAdminSongs();
                break;
            case ADMIN_ARPEGGIO_EDITOR:
                showAdminArpeggios();
                break;
            case CONFIGURATOR_PANEL:
            case USER_MANAGEMENT:
                showConfiguratorDashboard();
                break;
            case MODULE_MANAGEMENT:
                showAdminDashboard();
                break;
        }
    }

    @Override
    protected void onPause() {
        if (practiceView != null) practiceView.pause();
        cancelPracticeAudio();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (profileSaveCall != null) profileSaveCall.cancel();
        if (dashboardRepository != null) dashboardRepository.cancel();
        stopPractice();
        super.onDestroy();
    }
}
