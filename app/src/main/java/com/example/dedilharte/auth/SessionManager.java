package com.example.dedilharte.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.dedilharte.network.model.AuthenticatedUser;

public final class SessionManager {

    private static final String FILE_NAME = "dedilharte_session";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";
    private static final String KEY_WEEKLY_GOAL = "weekly_goal";
    private static final String KEY_PRIMARY_CONFIGURATOR = "primary_configurator";

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, AuthenticatedUser user) {
        if (user == null) {
            return;
        }
        saveSession(token, user.id, user.name, user.email, user.role, user.weeklyGoal);
        updatePreferences(user.initialLevel, user.songGoal, user.notificationsEnabled);
        preferences.edit().putBoolean(KEY_PRIMARY_CONFIGURATOR, user.primaryConfigurator).apply();
    }

    public void saveSession(String token, String userId, String name, String email, String role) {
        saveSession(token, userId, name, email, role, 3);
    }

    public void saveSession(String token, String userId, String name, String email, String role, int weeklyGoal) {
        preferences.edit()
                .putString(KEY_TOKEN, safe(token))
                .putString(KEY_USER_ID, safe(userId))
                .putString(KEY_NAME, safe(name))
                .putString(KEY_EMAIL, safe(email))
                .putString(KEY_ROLE, normalizeRole(role))
                .putInt(KEY_WEEKLY_GOAL, clampWeeklyGoal(weeklyGoal))
                .putString("initial_level", "beginner")
                .putInt("song_goal", 10)
                .putBoolean("notifications_enabled", false)
                .apply();
    }

    public void updateUser(String name, String email, String role) {
        updateUser(name, email, role, getWeeklyGoal());
    }

    public void updateUser(String name, String email, String role, int weeklyGoal) {
        preferences.edit()
                .putString(KEY_NAME, safe(name))
                .putString(KEY_EMAIL, safe(email))
                .putString(KEY_ROLE, normalizeRole(role))
                .putInt(KEY_WEEKLY_GOAL, clampWeeklyGoal(weeklyGoal))
                .apply();
    }

    public String getToken() {
        return preferences.getString(KEY_TOKEN, "");
    }

    public String getUserId() {
        return preferences.getString(KEY_USER_ID, "");
    }

    public String getName() {
        return preferences.getString(KEY_NAME, "");
    }

    public String getEmail() {
        return preferences.getString(KEY_EMAIL, "");
    }

    public String getRole() {
        return preferences.getString(KEY_ROLE, "student");
    }

    public boolean isPrimaryConfigurator() {
        return preferences.getBoolean(KEY_PRIMARY_CONFIGURATOR, false);
    }

    public int getWeeklyGoal() {
        return clampWeeklyGoal(preferences.getInt(KEY_WEEKLY_GOAL, 3));
    }

    public boolean isLoggedIn() {
        return !getToken().trim().isEmpty() && !getUserId().trim().isEmpty();
    }

    public void updatePreferences(String level, int songs, boolean notifications) {
        preferences.edit().putString("initial_level", "intermediate".equals(level) ? level : "beginner")
                .putInt("song_goal", Math.max(1, songs)).putBoolean("notifications_enabled", notifications).apply();
    }
    public String getInitialLevel() { return preferences.getString("initial_level", "beginner"); }
    public int getSongGoal() { return Math.max(1, preferences.getInt("song_goal", 10)); }
    public boolean notificationsEnabled() { return preferences.getBoolean("notifications_enabled", false); }

    // Only a previously authenticated, unexpired session may open cached content.
    // Server authorization still validates the signature, account and token version.
    public boolean canUseOfflineSession() {
        if (!isLoggedIn()) return false;
        try {
            String[] parts = getToken().split("\\.");
            if (parts.length != 3) return false;
            String payload = new String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE),
                    java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONObject claims = new org.json.JSONObject(payload);
            return getUserId().equals(claims.optString("sub"))
                    && claims.optLong("exp", 0) > System.currentTimeMillis() / 1000;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void clearSession() {
        preferences.edit().clear().apply();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeRole(String role) {
        if ("admin".equals(role)) {
            return "admin";
        }
        if ("configurator".equals(role)) {
            return "configurator";
        }
        return "student";
    }

    private int clampWeeklyGoal(int weeklyGoal) {
        return Math.max(1, Math.min(7, weeklyGoal));
    }
}
