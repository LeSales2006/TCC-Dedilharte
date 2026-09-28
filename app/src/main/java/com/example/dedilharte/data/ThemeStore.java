package com.example.dedilharte.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeStore {

    private static final String FILE_NAME = "dedilharte_theme";
    private static final String KEY_DARK_MODE = "dark_mode";

    private final SharedPreferences preferences;

    public ThemeStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public boolean isDarkModeEnabled() {
        return preferences.getBoolean(KEY_DARK_MODE, false);
    }

    public void setDarkModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
        apply(enabled);
    }

    public void applySavedMode() {
        apply(isDarkModeEnabled());
    }

    public static void apply(boolean enabled) {
        AppCompatDelegate.setDefaultNightMode(enabled
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
