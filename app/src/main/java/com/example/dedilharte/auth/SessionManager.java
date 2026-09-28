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

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, AuthenticatedUser user) {
        if (user == null) {
            return;
        }
        saveSession(token, user.id, user.name, user.email, user.role);
    }

    public void saveSession(String token, String userId, String name, String email, String role) {
        preferences.edit()
                .putString(KEY_TOKEN, safe(token))
                .putString(KEY_USER_ID, safe(userId))
                .putString(KEY_NAME, safe(name))
                .putString(KEY_EMAIL, safe(email))
                .putString(KEY_ROLE, normalizeRole(role))
                .apply();
    }

    public void updateUser(String name, String email, String role) {
        preferences.edit()
                .putString(KEY_NAME, safe(name))
                .putString(KEY_EMAIL, safe(email))
                .putString(KEY_ROLE, normalizeRole(role))
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

    public boolean isLoggedIn() {
        return !getToken().trim().isEmpty() && !getUserId().trim().isEmpty();
    }

    public void clearSession() {
        preferences.edit().clear().apply();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeRole(String role) {
        return "admin".equals(role) ? "admin" : "student";
    }
}
