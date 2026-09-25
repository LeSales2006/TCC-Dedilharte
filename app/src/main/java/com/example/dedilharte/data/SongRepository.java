package com.example.dedilharte.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.dedilharte.model.Song;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SongRepository {

    private static final String PREFS = "dedilharte_songs";
    private static final String SONGS = "songs";

    private final SharedPreferences preferences;

    public SongRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedIfEmpty();
    }

    public List<Song> all() {
        List<Song> songs = readAll();
        Collections.sort(songs, queueComparator());
        return songs;
    }

    public List<Song> publishedQueued() {
        List<Song> result = new ArrayList<>();
        for (Song song : readAll()) {
            if (Song.PUBLISHED.equals(song.getStatus()) && song.isInQueue()) {
                result.add(song);
            }
        }
        Collections.sort(result, queueComparator());
        return result;
    }

    public Song get(String id) {
        for (Song song : readAll()) {
            if (song.getId().equals(id)) {
                return song;
            }
        }
        return null;
    }

    public void save(Song song) {
        List<Song> songs = readAll();
        boolean replaced = false;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId().equals(song.getId())) {
                songs.set(i, song);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            songs.add(song);
        }
        writeAll(songs);
    }

    public void delete(String id) {
        List<Song> songs = readAll();
        for (int i = songs.size() - 1; i >= 0; i--) {
            if (songs.get(i).getId().equals(id)) {
                songs.remove(i);
            }
        }
        writeAll(songs);
    }

    public int nextQueueOrder() {
        int max = 0;
        for (Song song : readAll()) {
            max = Math.max(max, song.getQueueOrder());
        }
        return max + 1;
    }

    private void seedIfEmpty() {
        if (preferences.contains(SONGS)) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Song> songs = new ArrayList<>();
        songs.add(new Song(
                "seed_asa_branca",
                "Asa Branca - estudo inicial",
                "Luiz Gonzaga / Humberto Teixeira",
                "Iniciante",
                90,
                "E|--0---1---3---0---|\n" +
                        "B|--1---1---0---1---|\n" +
                        "G|--0---2---0---0---|\n" +
                        "D|--2---3---0---2---|\n" +
                        "A|--3---3---2---3---|\n" +
                        "E|----------3-------|",
                1,
                true,
                Song.PUBLISHED,
                now,
                now
        ));
        writeAll(songs);
    }

    private List<Song> readAll() {
        List<Song> songs = new ArrayList<>();
        String raw = preferences.getString(SONGS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                songs.add(new Song(
                        item.getString("id"),
                        item.getString("title"),
                        item.optString("artist", ""),
                        item.optString("difficulty", "Iniciante"),
                        item.optInt("recommendedBpm", 60),
                        item.optString("tablature", ""),
                        item.optInt("queueOrder", i + 1),
                        item.optBoolean("inQueue", true),
                        item.optString("status", Song.DRAFT),
                        item.optLong("createdAt", System.currentTimeMillis()),
                        item.optLong("updatedAt", System.currentTimeMillis())
                ));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(SONGS).apply();
        }
        return songs;
    }

    private void writeAll(List<Song> songs) {
        JSONArray array = new JSONArray();
        for (Song song : songs) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", song.getId());
                item.put("title", song.getTitle());
                item.put("artist", song.getArtist());
                item.put("difficulty", song.getDifficulty());
                item.put("recommendedBpm", song.getRecommendedBpm());
                item.put("tablature", song.getTablature());
                item.put("queueOrder", song.getQueueOrder());
                item.put("inQueue", song.isInQueue());
                item.put("status", song.getStatus());
                item.put("createdAt", song.getCreatedAt());
                item.put("updatedAt", song.getUpdatedAt());
                array.put(item);
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(SONGS, array.toString()).apply();
    }

    private Comparator<Song> queueComparator() {
        return (left, right) -> Integer.compare(left.getQueueOrder(), right.getQueueOrder());
    }
}
