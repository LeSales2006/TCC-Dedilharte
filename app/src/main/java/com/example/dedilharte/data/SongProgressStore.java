package com.example.dedilharte.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.dedilharte.model.Song;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SongProgressStore {

    private static final String FILE_NAME = "dedilharte_song_progress";
    private static final String LEARNED_PREFIX = "learned_";

    private final SharedPreferences preferences;
    private String userId;

    public SongProgressStore(Context context, String userId) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        setUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = userId == null || userId.trim().isEmpty() ? "default" : userId;
    }

    public boolean isLearned(String songId) {
        return preferences.getBoolean(key(songId), false);
    }

    public void setLearned(String songId, boolean learned) {
        preferences.edit().putBoolean(key(songId), learned).apply();
    }

    public Set<String> learnedSongIds() {
        Set<String> ids = new HashSet<>();
        String prefix = LEARNED_PREFIX + userId + "_";
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix) && preferences.getBoolean(key, false)) {
                ids.add(key.substring(prefix.length()));
            }
        }
        return ids;
    }

    public int learnedCount(List<Song> songs) {
        int total = 0;
        for (Song song : songs) {
            if (isLearned(song.getId())) {
                total++;
            }
        }
        return total;
    }

    public int learnedCountByDifficulty(List<Song> songs, String difficulty) {
        int total = 0;
        for (Song song : songs) {
            if (isLearned(song.getId()) && Song.normalizeDifficulty(difficulty).equals(song.normalizedDifficulty())) {
                total++;
            }
        }
        return total;
    }

    public void deleteUserData(String deletedUserId) {
        String resolved = deletedUserId == null || deletedUserId.trim().isEmpty() ? "default" : deletedUserId;
        String prefix = LEARNED_PREFIX + resolved + "_";
        SharedPreferences.Editor editor = preferences.edit();
        for (String existingKey : preferences.getAll().keySet()) {
            if (existingKey.startsWith(prefix)) {
                editor.remove(existingKey);
            }
        }
        editor.apply();
    }

    private String key(String songId) {
        return LEARNED_PREFIX + userId + "_" + songId;
    }
}
