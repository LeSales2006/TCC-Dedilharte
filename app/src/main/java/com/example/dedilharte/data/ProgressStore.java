package com.example.dedilharte.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.dedilharte.model.Lesson;

import java.util.List;

/**
 * Salva o progresso localmente para o aplicativo funcionar sem internet.
 */
public final class ProgressStore {

    private static final String FILE_NAME = "dedilharte_progress";
    private static final String INITIALIZED = "initial_state_created";
    private static final String PREFIX = "completed_";
    private static final String UPDATED_PREFIX = "updated_at_";

    private final SharedPreferences preferences;
    private String userId;

    public ProgressStore(Context context, String userId) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        setUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = userId == null || userId.trim().isEmpty() ? "default" : userId;
        initializePrototypeState();
    }

    private void initializePrototypeState() {
        if (!preferences.getBoolean(initializedKey(), false)) {
            preferences.edit()
                    .putBoolean(initializedKey(), true)
                    .putBoolean(completedKey("ini_1"), true)
                    .putLong(updatedAtKey("ini_1"), System.currentTimeMillis())
                    .apply();
        }
    }

    public boolean isCompleted(String lessonId) {
        if (preferences.getBoolean(completedKey(lessonId), false)) {
            return true;
        }
        return userId.startsWith("legacy_") && preferences.getBoolean(PREFIX + lessonId, false);
    }

    public void setCompleted(String lessonId, boolean completed) {
        setCompleted(lessonId, completed, System.currentTimeMillis());
    }

    public void setCompleted(String lessonId, boolean completed, long updatedAtMillis) {
        preferences.edit()
                .putBoolean(completedKey(lessonId), completed)
                .putLong(updatedAtKey(lessonId), updatedAtMillis)
                .apply();
    }

    public long updatedAt(String lessonId) {
        return preferences.getLong(updatedAtKey(lessonId), 0L);
    }

    public ProgressRecord progressRecord(String lessonId) {
        return new ProgressRecord(lessonId, isCompleted(lessonId), updatedAt(lessonId));
    }

    public List<ProgressRecord> progressRecords(List<Lesson> lessons) {
        java.util.ArrayList<ProgressRecord> records = new java.util.ArrayList<>();
        for (Lesson lesson : lessons) {
            records.add(progressRecord(lesson.getId()));
        }
        return records;
    }

    public int completedCount(List<Lesson> lessons) {
        int total = 0;
        for (Lesson lesson : lessons) {
            if (isCompleted(lesson.getId())) {
                total++;
            }
        }
        return total;
    }

    public int progressPercent(List<Lesson> lessons) {
        if (lessons.isEmpty()) {
            return 0;
        }
        return Math.round(completedCount(lessons) * 100f / lessons.size());
    }

    public void restoreInitialState() {
        SharedPreferences.Editor editor = preferences.edit();
        String userPrefix = PREFIX + userId + "_";
        for (String key : preferences.getAll().keySet()) {
            boolean legacyProgressKey = userId.startsWith("legacy_")
                    && key.startsWith(PREFIX)
                    && !key.startsWith(userPrefix);
            if (key.equals(initializedKey())
                    || key.startsWith(userPrefix)
                    || key.startsWith(UPDATED_PREFIX + userId + "_")
                    || legacyProgressKey) {
                editor.remove(key);
            }
        }
        editor.putBoolean(initializedKey(), true);
        editor.putBoolean(completedKey("ini_1"), true);
        editor.putLong(updatedAtKey("ini_1"), System.currentTimeMillis());
        editor.apply();
    }

    public void deleteUserData(String deletedUserId) {
        String resolvedUserId = deletedUserId == null || deletedUserId.trim().isEmpty()
                ? "default"
                : deletedUserId;
        SharedPreferences.Editor editor = preferences.edit();
        String userPrefix = PREFIX + resolvedUserId + "_";
        String initialized = INITIALIZED + "_" + resolvedUserId;
        for (String key : preferences.getAll().keySet()) {
            if (key.equals(initialized)
                    || key.startsWith(userPrefix)
                    || key.startsWith(UPDATED_PREFIX + resolvedUserId + "_")) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    private String initializedKey() {
        return INITIALIZED + "_" + userId;
    }

    private String completedKey(String lessonId) {
        return PREFIX + userId + "_" + lessonId;
    }

    private String updatedAtKey(String lessonId) {
        return UPDATED_PREFIX + userId + "_" + lessonId;
    }

    public static final class ProgressRecord {
        private final String lessonId;
        private final boolean completed;
        private final long updatedAtMillis;

        private ProgressRecord(String lessonId, boolean completed, long updatedAtMillis) {
            this.lessonId = lessonId;
            this.completed = completed;
            this.updatedAtMillis = updatedAtMillis;
        }

        public String getLessonId() {
            return lessonId;
        }

        public boolean isCompleted() {
            return completed;
        }

        public long getUpdatedAtMillis() {
            return updatedAtMillis;
        }
    }
}
