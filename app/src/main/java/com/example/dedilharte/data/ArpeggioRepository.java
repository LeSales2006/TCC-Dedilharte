package com.example.dedilharte.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.dedilharte.model.Song;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.ArpeggioListResponse;
import com.example.dedilharte.network.model.ArpeggioNote;
import com.example.dedilharte.network.model.ArpeggioRequest;
import com.example.dedilharte.network.model.ArpeggioResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class ArpeggioRepository {

    private static final String PREFS = "dedilharte_arpeggios";
    private static final String CACHE = "cache";

    private final SharedPreferences preferences;
    private final DedilharteApiService api;
    private final UserCacheScope scope;
    private static final java.util.Map<SharedPreferences, java.util.concurrent.atomic.AtomicLong> REQUESTS = new java.util.WeakHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong publicRequest;

    public ArpeggioRepository(Context context) {
        scope = new UserCacheScope(context);
        preferences = scope.preferences(context, PREFS);
        synchronized (REQUESTS) {
            publicRequest = REQUESTS.computeIfAbsent(preferences, key -> new java.util.concurrent.atomic.AtomicLong());
        }
        DedilharteApiClient.configure(context);
        api = DedilharteApiClient.service();
    }

    public void fetchPublic(ArpeggioListCallback callback) {
        if (!scope.isCurrent()) {
            if (callback != null) callback.onResult(new ArrayList<>(), false);
            return;
        }
        api.getArpeggios().enqueue(listCallback(callback, true, publicRequest.incrementAndGet()));
    }

    public void fetchAdmin(ArpeggioListCallback callback) {
        if (!scope.isAdmin()) {
            if (callback != null) callback.onResult(new ArrayList<>(), false);
            return;
        }
        api.getAdminArpeggios().enqueue(listCallback(callback, false, 0));
    }

    public void save(ArpeggioResponse existing, ArpeggioRequest request, OperationCallback callback) {
        if (!scope.isAdmin()) {
            if (callback != null) callback.onComplete(false, 403);
            return;
        }
        Call<ArpeggioResponse> call = existing == null || isBlank(existing.id)
                ? api.createArpeggio(request)
                : api.updateArpeggio(existing.id, request);
        call.enqueue(operationCallback(callback));
    }

    public void delete(String id, OperationCallback callback) {
        if (!scope.isAdmin()) {
            if (callback != null) callback.onComplete(false, 403);
            return;
        }
        if (isBlank(id) || "undefined".equals(id) || "null".equals(id)) {
            if (callback != null) {
                callback.onComplete(false, 400);
            }
            return;
        }
        api.deleteArpeggio(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!scope.isAdmin()) return;
                if (response.isSuccessful()) {
                    removeFromCache(id);
                }
                if (callback != null) {
                    callback.onComplete(response.isSuccessful(), response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (!scope.isAdmin()) return;
                if (callback != null) {
                    callback.onComplete(false, 0);
                }
            }
        });
    }

    private Callback<ArpeggioListResponse> listCallback(ArpeggioListCallback callback, boolean updateCache, long request) {
        return new Callback<ArpeggioListResponse>() {
            @Override
            public void onResponse(Call<ArpeggioListResponse> call, Response<ArpeggioListResponse> response) {
                if (!scope.isCurrent() || (updateCache && request != publicRequest.get())) return;
                List<ArpeggioResponse> received = response.isSuccessful()
                        && response.body() != null
                        && validResponses(response.body().items)
                        ? response.body().items
                        : null;
                boolean denied = response.code() == 401 || response.code() == 403;
                if (updateCache && denied) preferences.edit().putBoolean("access_denied", true).apply();
                List<ArpeggioResponse> items = received == null
                        ? (updateCache && !denied ? readCache() : new ArrayList<>()) : new ArrayList<>(received);
                if (updateCache && received != null) {
                    items.removeIf(item -> !Boolean.TRUE.equals(item.active));
                    writeCache(items);
                    preferences.edit().remove("access_denied").apply();
                }
                if (callback != null) {
                    callback.onResult(items, received != null);
                }
            }

            @Override
            public void onFailure(Call<ArpeggioListResponse> call, Throwable t) {
                if (!scope.isCurrent() || (updateCache && request != publicRequest.get())) return;
                if (callback != null) {
                    callback.onResult(updateCache ? readCache() : new ArrayList<>(), false);
                }
            }
        };
    }

    private Callback<ArpeggioResponse> operationCallback(OperationCallback callback) {
        return new Callback<ArpeggioResponse>() {
            @Override
            public void onResponse(Call<ArpeggioResponse> call, Response<ArpeggioResponse> response) {
                if (!scope.isAdmin()) return;
                boolean valid = response.isSuccessful() && response.body() != null && response.body().isValid();
                if (valid) {
                    // Refresh public content only through the public endpoint after an edit.
                    removeFromCache(response.body().id);
                }
                if (callback != null) {
                    callback.onComplete(valid, response.code());
                }
            }

            @Override
            public void onFailure(Call<ArpeggioResponse> call, Throwable t) {
                if (!scope.isAdmin()) return;
                if (callback != null) {
                    callback.onComplete(false, 0);
                }
            }
        };
    }

    private List<ArpeggioResponse> readCache() {
        List<ArpeggioResponse> items = new ArrayList<>();
        if (!scope.isCurrent() || preferences.getBoolean("access_denied", false)) return items;
        try {
            JSONArray array = new JSONArray(preferences.getString(CACHE, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                ArpeggioResponse arpeggio = new ArpeggioResponse();
                arpeggio.id = item.optString("id", "");
                arpeggio.title = item.optString("title", "");
                arpeggio.chord = item.optString("chord", "");
                arpeggio.difficulty = item.optString("difficulty", Song.EASY);
                arpeggio.active = item.optBoolean("active", false);
                arpeggio.sortOrder = item.optInt("sortOrder", i + 1);
                JSONArray notes = item.optJSONArray("notes");
                if (notes != null) {
                    for (int n = 0; n < notes.length(); n++) {
                        JSONObject note = notes.getJSONObject(n);
                        arpeggio.notes.add(new ArpeggioNote(note.optInt("string"), note.optInt("fret")));
                    }
                }
                if (arpeggio.isValid() && Boolean.TRUE.equals(arpeggio.active)) {
                    items.add(arpeggio);
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private void writeCache(List<ArpeggioResponse> items) {
        if (!scope.isCurrent() || !validResponses(items)) return;
        JSONArray array = new JSONArray();
        try {
            for (ArpeggioResponse item : items) {
                JSONObject json = new JSONObject();
                json.put("id", item.id);
                json.put("title", item.title);
                json.put("chord", item.chord);
                json.put("difficulty", item.difficulty);
                json.put("active", item.active);
                json.put("sortOrder", item.sortOrder);
                JSONArray notes = new JSONArray();
                for (ArpeggioNote note : item.notes) {
                    JSONObject noteJson = new JSONObject();
                    noteJson.put("string", note.string);
                    noteJson.put("fret", note.fret);
                    notes.put(noteJson);
                }
                json.put("notes", notes);
                array.put(json);
            }
        } catch (Exception ignored) {
            return; // A partial serialization must never replace the last valid cache.
        }
        preferences.edit().putString(CACHE, array.toString()).apply();
    }

    private void removeFromCache(String id) {
        publicRequest.incrementAndGet(); // Discard reads started before a confirmed edit/deletion.
        List<ArpeggioResponse> items = readCache();
        boolean changed = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            if (id.equals(items.get(i).id)) {
                items.remove(i);
                changed = true;
            }
        }
        if (changed) {
            writeCache(items);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean validResponses(List<ArpeggioResponse> items) {
        if (items == null) return false;
        for (ArpeggioResponse item : items) {
            if (item == null || !item.isValid()) return false;
        }
        return true;
    }

    public interface ArpeggioListCallback {
        void onResult(List<ArpeggioResponse> items, boolean fromNetwork);
    }

    public interface OperationCallback {
        void onComplete(boolean success, int code);
    }
}
