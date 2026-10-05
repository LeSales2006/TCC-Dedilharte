package com.example.dedilharte.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.dedilharte.model.Song;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.SongListResponse;
import com.example.dedilharte.network.model.SongRequest;
import com.example.dedilharte.network.model.SongResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class SongRepository {

    private static final String PREFS = "dedilharte_songs";
    private static final String SONGS = "songs";
    private static final String PUBLISHED_CACHE = "published_cache";

    private final SharedPreferences preferences;
    private final DedilharteApiService api;

    public SongRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        api = DedilharteApiClient.service();
        seedIfEmpty();
        removeLegacySeedSongs();
    }

    public List<Song> all() {
        List<Song> songs = readAll();
        Collections.sort(songs, queueComparator());
        return songs;
    }

    public List<Song> publishedQueued() {
        List<Song> result = new ArrayList<>();
        List<Song> cachedPublic = readList(PUBLISHED_CACHE);
        List<Song> source = cachedPublic.isEmpty() ? readAll() : cachedPublic;
        for (Song song : source) {
            if (Song.PUBLISHED.equals(song.getStatus()) && song.isInQueue()) {
                result.add(song);
            }
        }
        Collections.sort(result, queueComparator());
        return result;
    }

    public void fetchPublishedQueued(SongListCallback callback) {
        api.getSongs().enqueue(new Callback<SongListResponse>() {
            @Override
            public void onResponse(Call<SongListResponse> call, Response<SongListResponse> response) {
                List<Song> songs = response.isSuccessful()
                        && response.body() != null
                        && response.body().items != null
                        ? fromResponses(response.body().items)
                        : publishedQueued();
                if (response.isSuccessful()) {
                    writeList(PUBLISHED_CACHE, songs);
                }
                if (callback != null) {
                    callback.onResult(songs, response.isSuccessful());
                }
            }

            @Override
            public void onFailure(Call<SongListResponse> call, Throwable t) {
                if (callback != null) {
                    callback.onResult(publishedQueued(), false);
                }
            }
        });
    }

    public void fetchAdmin(SongListCallback callback) {
        api.getAdminSongs().enqueue(new Callback<SongListResponse>() {
            @Override
            public void onResponse(Call<SongListResponse> call, Response<SongListResponse> response) {
                List<Song> songs = response.isSuccessful()
                        && response.body() != null
                        && response.body().items != null
                        ? fromResponses(response.body().items)
                        : all();
                if (response.isSuccessful()) {
                    writeAll(songs);
                }
                if (callback != null) {
                    callback.onResult(songs, response.isSuccessful());
                }
            }

            @Override
            public void onFailure(Call<SongListResponse> call, Throwable t) {
                if (callback != null) {
                    callback.onResult(all(), false);
                }
            }
        });
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
        Call<SongResponse> call = replaced
                ? api.updateSong(song.getId(), requestFromSong(song))
                : api.createSong(requestFromSong(song));
        call.enqueue(new Callback<SongResponse>() {
            @Override
            public void onResponse(Call<SongResponse> call, Response<SongResponse> response) {
            }

            @Override
            public void onFailure(Call<SongResponse> call, Throwable t) {
            }
        });
    }

    public void delete(String id) {
        List<Song> songs = readAll();
        for (int i = songs.size() - 1; i >= 0; i--) {
            if (songs.get(i).getId().equals(id)) {
                songs.remove(i);
            }
        }
        writeAll(songs);
        api.deleteSong(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
            }
        });
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
        writeAll(new ArrayList<>());
    }

    private void removeLegacySeedSongs() {
        List<Song> songs = readAll();
        boolean changed = false;
        for (int i = songs.size() - 1; i >= 0; i--) {
            if ("seed_asa_branca".equals(songs.get(i).getId())) {
                songs.remove(i);
                changed = true;
            }
        }
        if (changed) {
            writeAll(songs);
        }
    }

    private List<Song> readAll() {
        return readList(SONGS);
    }

    private List<Song> readList(String key) {
        List<Song> songs = new ArrayList<>();
        String raw = preferences.getString(key, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                songs.add(new Song(
                        item.getString("id"),
                        item.getString("title"),
                        item.optString("artist", ""),
                        Song.normalizeDifficulty(item.optString("difficulty", Song.EASY)),
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
            preferences.edit().remove(key).apply();
        }
        return songs;
    }

    private void writeAll(List<Song> songs) {
        writeList(SONGS, songs);
    }

    private void writeList(String key, List<Song> songs) {
        JSONArray array = new JSONArray();
        for (Song song : songs) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", song.getId());
                item.put("title", song.getTitle());
                item.put("artist", song.getArtist());
                item.put("difficulty", song.normalizedDifficulty());
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
        preferences.edit().putString(key, array.toString()).apply();
    }

    private Comparator<Song> queueComparator() {
        return (left, right) -> Integer.compare(left.getQueueOrder(), right.getQueueOrder());
    }

    private List<Song> fromResponses(List<SongResponse> responses) {
        List<Song> songs = new ArrayList<>();
        for (SongResponse response : responses) {
            if (response == null || response.id == null || response.title == null) {
                continue;
            }
            songs.add(new Song(
                    response.id,
                    response.title,
                    response.artist == null ? "" : response.artist,
                    Song.normalizeDifficulty(response.difficulty),
                    response.recommendedBpm,
                    response.tablature == null ? "" : response.tablature,
                    response.queueOrder,
                    response.inQueue,
                    response.status == null ? Song.DRAFT : response.status,
                    response.createdAt,
                    response.updatedAt
            ));
        }
        Collections.sort(songs, queueComparator());
        return songs;
    }

    private SongRequest requestFromSong(Song song) {
        return new SongRequest(
                song.getId(),
                song.getTitle(),
                song.getArtist(),
                song.normalizedDifficulty(),
                song.getRecommendedBpm(),
                song.getTablature(),
                song.getQueueOrder(),
                song.isInQueue(),
                song.getStatus()
        );
    }

    public interface SongListCallback {
        void onResult(List<Song> songs, boolean fromNetwork);
    }
}
