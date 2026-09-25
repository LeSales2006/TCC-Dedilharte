package com.example.dedilharte.sync;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.dedilharte.data.ProgressStore;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.ProgressListResponse;
import com.example.dedilharte.network.model.ProgressRequest;
import com.example.dedilharte.network.model.ProgressResponse;
import com.example.dedilharte.network.model.UserRequest;
import com.example.dedilharte.network.model.UserResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DedilharteSyncManager {

    private static final String TAG = "DedilharteSync";

    private final DedilharteApiService api;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DedilharteSyncManager() {
        api = DedilharteApiClient.service();
    }

    public void syncUser(String userId, String name, long updatedAtMillis) {
        if (isBlank(userId) || isBlank(name)) {
            return;
        }
        api.upsertUser(new UserRequest(userId, name, validTimestamp(updatedAtMillis)))
                .enqueue(new LoggingCallback<>("syncUser"));
    }

    public void fetchRemoteUser(String userId, UserSyncCallback callback) {
        if (isBlank(userId)) {
            return;
        }
        api.getUser(userId).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.code() == 404) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    logHttp("fetchRemoteUser", response.code());
                    return;
                }
                if (callback != null) {
                    mainHandler.post(() -> callback.onRemoteUser(response.body()));
                }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Log.e(TAG, "fetchRemoteUser falhou: " + t.getMessage());
            }
        });
    }

    public void deleteUser(String userId) {
        if (isBlank(userId)) {
            return;
        }
        api.deleteUser(userId).enqueue(new LoggingCallback<>("deleteUser"));
    }

    public void syncProgress(String userId, ProgressStore.ProgressRecord record) {
        if (isBlank(userId) || record == null || isBlank(record.getLessonId())) {
            return;
        }
        ProgressRequest request = new ProgressRequest(
                record.getLessonId(),
                record.isCompleted(),
                0,
                validTimestamp(record.getUpdatedAtMillis())
        );
        api.upsertProgress(userId, request).enqueue(new LoggingCallback<>("syncProgress"));
    }

    public void syncProgressRecords(String userId, List<ProgressStore.ProgressRecord> records, boolean includeUnchanged) {
        if (records == null) {
            return;
        }
        for (ProgressStore.ProgressRecord record : records) {
            if (includeUnchanged || record.isCompleted() || record.getUpdatedAtMillis() > 0) {
                syncProgress(userId, record);
            }
        }
    }

    public void fetchRemoteProgress(
            String userId,
            ProgressStore progressStore,
            Runnable onLocalChanged
    ) {
        if (isBlank(userId) || progressStore == null) {
            return;
        }
        api.getProgress(userId).enqueue(new Callback<ProgressListResponse>() {
            @Override
            public void onResponse(Call<ProgressListResponse> call, Response<ProgressListResponse> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().items == null) {
                    logHttp("fetchRemoteProgress", response.code());
                    return;
                }
                boolean changed = false;
                for (ProgressResponse remote : response.body().items) {
                    if (remote == null || isBlank(remote.lesson_id)) {
                        continue;
                    }
                    long localUpdatedAt = progressStore.updatedAt(remote.lesson_id);
                    if (remote.updatedAtMillis > localUpdatedAt) {
                        progressStore.setCompleted(
                                remote.lesson_id,
                                remote.completed,
                                remote.updatedAtMillis
                        );
                        changed = true;
                    }
                }
                if (changed && onLocalChanged != null) {
                    mainHandler.post(onLocalChanged);
                }
            }

            @Override
            public void onFailure(Call<ProgressListResponse> call, Throwable t) {
                Log.e(TAG, "fetchRemoteProgress falhou: " + t.getMessage());
            }
        });
    }

    private long validTimestamp(long updatedAtMillis) {
        return updatedAtMillis > 0 ? updatedAtMillis : System.currentTimeMillis();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void logHttp(String operation, int code) {
        Log.e(TAG, operation + " respondeu HTTP " + code);
    }

    private final class LoggingCallback<T> implements Callback<T> {
        private final String operation;

        private LoggingCallback(String operation) {
            this.operation = operation;
        }

        @Override
        public void onResponse(Call<T> call, Response<T> response) {
            if (!response.isSuccessful()) {
                logHttp(operation, response.code());
            }
        }

        @Override
        public void onFailure(Call<T> call, Throwable t) {
            Log.e(TAG, operation + " falhou: " + t.getMessage());
        }
    }

    public interface UserSyncCallback {
        void onRemoteUser(UserResponse user);
    }
}
