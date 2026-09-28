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

    public List<ArpeggioResponse> cachedOrFallback() {
        List<ArpeggioResponse> cached = readCache();
        return cached.isEmpty() ? fallback() : cached;
    }

    public void fetchPublic(ArpeggioListCallback callback) {
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
        api.deleteArpeggio(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
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
                List<ArpeggioResponse> items = response.isSuccessful()
                        && response.body() != null
                        && response.body().items != null
                        ? response.body().items
                        : cachedOrFallback();
                if (updateCache && response.isSuccessful()) {
                    writeCache(items);
                }
                if (callback != null) {
                    callback.onResult(items, response.isSuccessful());
                }
            }

            @Override
            public void onFailure(Call<ArpeggioListResponse> call, Throwable t) {
                if (callback != null) {
                    callback.onResult(cachedOrFallback(), false);
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

    private List<ArpeggioResponse> fallback() {
        List<ArpeggioResponse> items = new ArrayList<>();
        items.add(arpeggio("arp_c_major", "Dó maior (C)", "C", 1, new int[][]{{5, 3}, {3, 0}, {2, 1}, {1, 0}}));
        items.add(arpeggio("arp_d_major", "Ré maior (D)", "D", 2, new int[][]{{4, 0}, {3, 2}, {2, 3}, {1, 2}}));
        items.add(arpeggio("arp_e_major", "Mi maior (E)", "E", 3, new int[][]{{6, 0}, {3, 1}, {2, 0}, {1, 0}}));
        items.add(arpeggio("arp_f_major", "Fá maior (F)", "F", 4, new int[][]{{6, 1}, {3, 2}, {2, 1}, {1, 1}}));
        items.add(arpeggio("arp_g_major", "Sol maior (G)", "G", 5, new int[][]{{6, 3}, {3, 0}, {2, 0}, {1, 3}}));
        items.add(arpeggio("arp_a_major", "Lá maior (A)", "A", 6, new int[][]{{5, 0}, {3, 2}, {2, 2}, {1, 0}}));
        items.add(arpeggio("arp_b_major", "Si maior (B)", "B", 7, new int[][]{{5, 2}, {3, 4}, {2, 4}, {1, 2}}));
        return items;
    }

    private ArpeggioResponse arpeggio(String id, String title, String chord, int sortOrder, int[][] notes) {
        ArpeggioResponse arpeggio = new ArpeggioResponse();
        arpeggio.id = id;
        arpeggio.title = title;
        arpeggio.chord = chord;
        arpeggio.difficulty = Song.EASY;
        arpeggio.active = true;
        arpeggio.sortOrder = sortOrder;
        for (int[] note : notes) {
            arpeggio.notes.add(new ArpeggioNote(note[0], note[1]));
        }
        return arpeggio;
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
