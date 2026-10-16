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

    public ArpeggioRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        api = DedilharteApiClient.service();
    }

    public void fetchPublic(ArpeggioListCallback callback) {
        writeCache(new ArrayList<>());
        api.getArpeggios().enqueue(listCallback(callback, true));
    }

    public void fetchAdmin(ArpeggioListCallback callback) {
        api.getAdminArpeggios().enqueue(listCallback(callback, false));
    }

    public void save(ArpeggioResponse existing, ArpeggioRequest request, OperationCallback callback) {
        Call<ArpeggioResponse> call = existing == null || isBlank(existing.id)
                ? api.createArpeggio(request)
                : api.updateArpeggio(existing.id, request);
        call.enqueue(operationCallback(callback));
    }

    public void delete(String id, OperationCallback callback) {
        if (isBlank(id) || "undefined".equals(id) || "null".equals(id)) {
            if (callback != null) {
                callback.onComplete(false, 400);
            }
            return;
        }
        api.deleteArpeggio(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    removeFromCache(id);
                }
                if (callback != null) {
                    callback.onComplete(response.isSuccessful(), response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (callback != null) {
                    callback.onComplete(false, 0);
                }
            }
        });
    }

    private Callback<ArpeggioListResponse> listCallback(ArpeggioListCallback callback, boolean updateCache) {
        return new Callback<ArpeggioListResponse>() {
            @Override
            public void onResponse(Call<ArpeggioListResponse> call, Response<ArpeggioListResponse> response) {
                List<ArpeggioResponse> received = response.isSuccessful()
                        && response.body() != null
                        && response.body().items != null
                        ? response.body().items
                        : null;
                List<ArpeggioResponse> items = received == null ? new ArrayList<>() : received;
                if (updateCache) {
                    writeCache(items);
                }
                if (callback != null) {
                    callback.onResult(items, received != null);
                }
            }

            @Override
            public void onFailure(Call<ArpeggioListResponse> call, Throwable t) {
                if (callback != null) {
                    callback.onResult(new ArrayList<>(), false);
                }
            }
        };
    }

    private Callback<ArpeggioResponse> operationCallback(OperationCallback callback) {
        return new Callback<ArpeggioResponse>() {
            @Override
            public void onResponse(Call<ArpeggioResponse> call, Response<ArpeggioResponse> response) {
                if (callback != null) {
                    callback.onComplete(response.isSuccessful(), response.code());
                }
            }

            @Override
            public void onFailure(Call<ArpeggioResponse> call, Throwable t) {
                if (callback != null) {
                    callback.onComplete(false, 0);
                }
            }
        };
    }

    private List<ArpeggioResponse> readCache() {
        List<ArpeggioResponse> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(CACHE, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                ArpeggioResponse arpeggio = new ArpeggioResponse();
                arpeggio.id = item.optString("id", "");
                arpeggio.title = item.optString("title", "");
                arpeggio.chord = item.optString("chord", "");
                arpeggio.difficulty = item.optString("difficulty", Song.EASY);
                arpeggio.active = item.optBoolean("active", true);
                arpeggio.sortOrder = item.optInt("sortOrder", i + 1);
                JSONArray notes = item.optJSONArray("notes");
                if (notes != null) {
                    for (int n = 0; n < notes.length(); n++) {
                        JSONObject note = notes.getJSONObject(n);
                        arpeggio.notes.add(new ArpeggioNote(note.optInt("string"), note.optInt("fret")));
                    }
                }
                if (!isBlank(arpeggio.title) && !arpeggio.notes.isEmpty()) {
                    items.add(arpeggio);
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private void writeCache(List<ArpeggioResponse> items) {
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
        }
        preferences.edit().putString(CACHE, array.toString()).apply();
    }

    private void removeFromCache(String id) {
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

    public interface ArpeggioListCallback {
        void onResult(List<ArpeggioResponse> items, boolean fromNetwork);
    }

    public interface OperationCallback {
        void onComplete(boolean success, int code);
    }
}
