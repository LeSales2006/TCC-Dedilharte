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
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class SongRepository {

    private static final String PREFS = "dedilharte_songs";
    private static final String SONGS = "songs";
    private static final String PUBLISHED_CACHE = "published_cache";

    private final SharedPreferences preferences;
    private final DedilharteApiService api;
    private final UserCacheScope scope;
    private static final java.util.Map<SharedPreferences, java.util.concurrent.atomic.AtomicLong> REQUESTS = new java.util.WeakHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong publicRequest;

    public SongRepository(Context context) {
        scope = new UserCacheScope(context);
        preferences = scope.preferences(context, PREFS);
        synchronized (REQUESTS) {
            publicRequest = REQUESTS.computeIfAbsent(preferences, key -> new java.util.concurrent.atomic.AtomicLong());
        }
        DedilharteApiClient.configure(context);
        api = DedilharteApiClient.service();
    }

    public List<Song> all() {
        List<Song> songs = readAll();
        Collections.sort(songs, queueComparator());
        return songs;
    }

    public List<Song> publishedQueued() {
        List<Song> result = new ArrayList<>();
        List<Song> source = readList(PUBLISHED_CACHE);
        for (Song song : source) {
            if (song.isActive()) {
                result.add(song);
            }
        }
        Collections.sort(result, queueComparator());
        return result;
    }

    public void fetchPublishedQueued(SongListCallback callback) {
        fetchPublishedQueuedWithErrors((songs, fromNetwork, error) -> {
            if (callback != null) callback.onResult(songs, fromNetwork);
        });
    }

    public static List<Song> recommendations(List<Song> songs, Set<String> learnedIds, String level) {
        Set<String> familiarArtists = new HashSet<>();
        List<Song> candidates = new ArrayList<>();
        for (Song song : songs) {
            if (!song.isActive()) continue;
            if (learnedIds.contains(song.getId())) {
                if (!song.getArtist().trim().isEmpty()) {
                    familiarArtists.add(song.getArtist().trim().toLowerCase(Locale.ROOT));
                }
            } else {
                candidates.add(song);
            }
        }
        int targetDifficulty = Song.difficultyWeight(level);
        candidates.sort(Comparator
                .comparingInt((Song song) -> Math.abs(song.difficultyWeight() - targetDifficulty))
                .thenComparingInt(song -> familiarArtists.contains(song.getArtist().trim().toLowerCase(Locale.ROOT)) ? 0 : 1)
                .thenComparingInt(Song::getQueueOrder)
                .thenComparing(Song::getId));
        return new ArrayList<>(candidates.subList(0, Math.min(3, candidates.size())));
    }

    public interface PublishedSongsCallback {
        void onResult(List<Song> songs, boolean fromNetwork, String error);
    }

    public void fetchPublishedQueuedWithErrors(PublishedSongsCallback callback) {
        if (!scope.isCurrent()) {
            if (callback != null) callback.onResult(Collections.emptyList(), false, "Entre novamente.");
            return;
        }
        final long request = publicRequest.incrementAndGet();
        api.getSongs().enqueue(new Callback<SongListResponse>() {
            @Override
            public void onResponse(Call<SongListResponse> call, Response<SongListResponse> response) {
                if (!scope.isCurrent() || request != publicRequest.get()) return;
                boolean valid = response.isSuccessful()
                        && response.body() != null
                        && validResponses(response.body().items);
                List<Song> songs;
                if (valid) {
                    songs = fromResponses(response.body().items);
                    songs.removeIf(song -> !song.isActive());
                    writeList(PUBLISHED_CACHE, songs);
                } else if (response.code() == 401 || response.code() == 403) {
                    // Authorization was explicitly revoked. Do not offer an offline bypass.
                    preferences.edit().putBoolean("public_access_denied", true).apply();
                    songs = Collections.emptyList();
                } else {
                    songs = publishedQueued();
                }
                if (callback != null) {
                    String error = null;
                    if (!response.isSuccessful()) {
                        error = com.example.dedilharte.network.ApiErrors.message(response,
                                "Não foi possível carregar a música. Tente novamente.");
                    } else if (!valid) {
                        error = "O servidor retornou um conteúdo inválido. Tente novamente mais tarde.";
                    }
                    callback.onResult(songs, valid, error);
                }
            }

            @Override
            public void onFailure(Call<SongListResponse> call, Throwable t) {
                if (!scope.isCurrent() || request != publicRequest.get()) return;
                if (callback != null) {
                    callback.onResult(publishedQueued(), false,
                            "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.");
                }
            }
        });
    }

    public void fetchAdmin(SongListCallback callback) {
        if (!scope.isAdmin()) {
            if (callback != null) callback.onResult(Collections.emptyList(), false);
            return;
        }
        api.getAdminSongs().enqueue(new Callback<SongListResponse>() {
            @Override
            public void onResponse(Call<SongListResponse> call, Response<SongListResponse> response) {
                if (!scope.isAdmin()) return;
                boolean valid = response.isSuccessful()
                        && response.body() != null
                        && validResponses(response.body().items);
                List<Song> songs = valid ? fromResponses(response.body().items) : Collections.emptyList();
                if (valid) {
                    writeAll(songs);
                }
                if (callback != null) {
                    callback.onResult(songs, valid);
                }
            }

            @Override
            public void onFailure(Call<SongListResponse> call, Throwable t) {
                if (callback != null) {
                    callback.onResult(Collections.emptyList(), false);
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

    public void save(Song song, OperationCallback callback) {
        save(song, song.getId() == null, (saved, error) -> callback.onComplete(saved != null));
    }

    public void save(Song song, boolean creating, SaveResultCallback callback) {
        if (!scope.isAdmin()) {
            callback.onComplete(null, "Entre novamente como administrador.");
            return;
        }
        Call<SongResponse> call = creating
                ? api.createSong(requestFromSong(song))
                : api.updateSong(song.getId(), requestFromSong(song));
        call.enqueue(new Callback<SongResponse>() {
            @Override
            public void onResponse(Call<SongResponse> call, Response<SongResponse> response) {
                if (!scope.isAdmin()) return;
                List<Song> saved = response.isSuccessful() && response.body() != null
                        ? fromResponses(Collections.singletonList(response.body())) : Collections.emptyList();
                if (!saved.isEmpty()) {
                    removePublished(saved.get(0).getId());
                    List<Song> current = readAll();
                    current.removeIf(song -> song.getId().equals(saved.get(0).getId()));
                    current.add(saved.get(0));
                    writeAll(current);
                }
                callback.onComplete(saved.isEmpty() ? null : saved.get(0), saved.isEmpty()
                        ? com.example.dedilharte.network.ApiErrors.message(response, "Não foi possível salvar a música.") : null);
            }

            @Override
            public void onFailure(Call<SongResponse> call, Throwable t) {
                callback.onComplete(null, "Falha de conexão. Tente novamente.");
            }
        });
    }

    public interface SaveResultCallback {
        void onComplete(Song saved, String error);
    }

    public void delete(String id, OperationCallback callback) {
        delete(id, (success, error) -> callback.onComplete(success));
    }

    public interface DeleteResultCallback {
        void onComplete(boolean success, String error);
    }

    public void delete(String id, DeleteResultCallback callback) {
        if (!scope.isAdmin()) {
            callback.onComplete(false, "Entre novamente como administrador.");
            return;
        }
        api.deleteSong(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!scope.isAdmin()) return;
                if (response.isSuccessful()) {
                    removePublished(id);
                    List<Song> current = readAll();
                    current.removeIf(song -> song.getId().equals(id));
                    writeAll(current);
                }
                callback.onComplete(response.isSuccessful(),
                        com.example.dedilharte.network.ApiErrors.message(response, "Não foi possível excluir a música."));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onComplete(false, "Falha de conexão. Tente novamente.");
            }
        });
    }

    public interface OperationCallback {
        void onComplete(boolean success);
    }
    public int nextQueueOrder() {
        int max = 0;
        for (Song song : readAll()) {
            max = Math.max(max, song.getQueueOrder());
        }
        return max + 1;
    }

    private List<Song> readAll() {
        return scope.isAdmin() ? readList(SONGS) : new ArrayList<>();
    }

    private List<Song> readList(String key) {
        List<Song> songs = new ArrayList<>();
        if (!scope.isCurrent() || (PUBLISHED_CACHE.equals(key)
                && preferences.getBoolean("public_access_denied", false))) return songs;
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
            // Leave the stored data untouched; a subsequent valid response repairs it.
        }
        return songs;
    }

    private void writeAll(List<Song> songs) {
        writeList(SONGS, songs);
    }

    private void writeList(String key, List<Song> songs) {
        if (!scope.isCurrent()) return;
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
        SharedPreferences.Editor editor = preferences.edit().putString(key, array.toString());
        if (PUBLISHED_CACHE.equals(key)) editor.remove("public_access_denied");
        editor.apply();
    }

    private void removePublished(String id) {
        publicRequest.incrementAndGet(); // An older GET must not undo a confirmed deletion/archive.
        List<Song> published = publishedQueued();
        published.removeIf(song -> song.getId().equals(id));
        writeList(PUBLISHED_CACHE, published);
    }

    private boolean validResponses(List<SongResponse> responses) {
        if (responses == null) return false;
        for (SongResponse response : responses) {
            if (response == null || !response.isValid()) return false;
        }
        return true;
    }

    private Comparator<Song> queueComparator() {
        return (left, right) -> Integer.compare(left.getQueueOrder(), right.getQueueOrder());
    }

    private List<Song> fromResponses(List<SongResponse> responses) {
        List<Song> songs = new ArrayList<>();
        for (SongResponse response : responses) {
            if (response == null || !response.isValid()) {
                continue;
            }
            songs.add(fromResponse(response));
        }
        Collections.sort(songs, queueComparator());
        return songs;
    }

    public static Song fromResponse(SongResponse response) {
        return new Song(
                    response.id,
                    response.title,
                    response.artist == null ? "" : response.artist,
                    Song.normalizeDifficulty(response.difficulty),
                    response.bpm,
                    response.tablature == null ? "" : response.tablature,
                    response.sortOrder,
                    response.isPublished(),
                    response.isPublished() ? Song.PUBLISHED : Song.ARCHIVED,
                    0,
                    0
            );
    }

    private SongRequest requestFromSong(Song song) {
        return new SongRequest(
                song.getTitle(),
                song.getArtist(),
                song.normalizedDifficulty(),
                song.getRecommendedBpm(),
                song.getTablature(),
                song.getQueueOrder(),
                song.isActive()
        );
    }

    public interface SongListCallback {
        void onResult(List<Song> songs, boolean fromNetwork);
    }
}
