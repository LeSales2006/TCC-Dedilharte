package com.example.dedilharte.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.*;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** UI-thread repository. Each group keeps its last confirmed response independently. */
public final class DashboardRepository {
    public interface Listener { void onResult(DashboardMetrics data, boolean loading, String notice); }
    private final UserCacheScope scope;
    private final SharedPreferences cache;
    private final SessionManager session;
    private final DedilharteApiService api;
    private final List<Call<?>> calls = new ArrayList<>();
    private final Gson gson = new Gson();
    private final boolean diagnostics;
    private int generation;

    public DashboardRepository(Context context) {
        diagnostics = (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        scope = new UserCacheScope(context);
        cache = scope.preferences(context, "dashboard");
        session = new SessionManager(context);
        api = DedilharteApiClient.service();
    }

    public DashboardMetrics cached() {
        if (!scope.isCurrent() || !session.canUseOfflineSession() || cache.getBoolean("blocked", false)) return null;
        try {
            DashboardMetrics data = gson.fromJson(cache.getString("snapshot", ""), DashboardMetrics.class);
            return data != null && data.valid() && data.savedAt > 0 ? data : null;
        }
        catch (RuntimeException invalid) { return null; }
    }

    public void cancel() {
        generation++;
        for (Call<?> call : calls) call.cancel();
        calls.clear();
    }

    public void invalidate() {
        cancel();
        // A confirmed write requires a refresh, but a failed refresh must not erase history.
    }

    /** Called only after the server confirms a profile write. */
    public void updateWeeklyGoal(int goal) {
        cancel();
        DashboardMetrics state = cached();
        if (state != null) {
            state.weeklyGoal = Math.max(1, Math.min(7, goal));
            cache.edit().putString("snapshot", gson.toJson(state)).apply();
        }
    }

    public void refresh(Listener listener) {
        cancel();
        if (!scope.isCurrent()) return;
        listener.onResult(cached(), true, "Atualizando indicadores…");
        new Fetch(generation, listener).start();
    }

    private final class Fetch {
        final int request;
        final Listener listener;
        int remaining = 5;
        final List<String> errors = new ArrayList<>();
        boolean triviaDone, songsDone, activityDone;
        DashboardMetrics state;
        LearningModuleListResponse modules;
        ModuleProgressListResponse progress;
        SongListResponse songs;
        SongProgressListResponse learned;
        ActivitySummaryResponse activity;
        Fetch(int request, Listener listener) {
            this.request = request; this.listener = listener;
            state = cached();
            if (state == null) {
                state = new DashboardMetrics(); state.partial = true;
                state.weeklyGoal = 3; state.weeklyAccesses = new int[]{0};
            } else {
                state.triviaAvailable = state.hasTrivia(); state.songsAvailable = state.hasSongs();
                state.activityAvailable = state.hasActivity(); state.partial = true;
            }
        }
        void start() {
            read("/api/modules", api.getModules(), value -> modules = value);
            read("/api/module-progress", api.getModuleProgress(), value -> progress = value);
            read("/api/songs", api.getSongs(), value -> songs = value);
            read("/api/songs/progress", api.getSongProgress(), value -> learned = value);
            read("/api/activity/summary", api.getActivitySummary(), value -> activity = value);
        }
        boolean current() { return request == generation && scope.isCurrent(); }
        <T> void read(String path, Call<T> call, Consumer<T> accept) {
            if (!current()) return;
            calls.add(call);
            call.enqueue(new Callback<T>() {
                @Override public void onResponse(Call<T> call, Response<T> response) {
                    if (!current()) return;
                    diagnostic(path, "HTTP " + response.code());
                    if (response.code() == 401 || response.code() == 403) {
                        cache.edit().putBoolean("blocked", true).apply();
                        cancel();
                        listener.onResult(null, false, "Sessão sem acesso. Entre novamente para atualizar seu progresso.");
                        return;
                    }
                    if (response.isSuccessful() && response.body() != null) accept.accept(response.body());
                    else errors.add(label(path) + " (HTTP " + response.code() + ")");
                    finish();
                }
                @Override public void onFailure(Call<T> call, Throwable error) {
                    if (!current()) return;
                    diagnostic(path, error.getClass().getSimpleName());
                    errors.add(label(path) + " (falha de conexão)"); finish();
                }
            });
        }
        void finish() {
            remaining--;
            if (modules != null && progress != null && !triviaDone) {
                triviaDone = true;
                try {
                    DashboardMetrics values = calculate(modules, progress, emptySongs(), emptyLearned(), emptyActivity());
                    state.beginner = values.beginner; state.intermediate = values.intermediate;
                    state.triviaAvailable = true; save();
                } catch (IllegalArgumentException invalid) { invalid("Trivias", invalid); }
            }
            if (songs != null && learned != null && !songsDone) {
                songsDone = true;
                try {
                    DashboardMetrics values = calculate(emptyModules(), emptyProgress(), songs, learned, emptyActivity());
                    state.songs = values.songs; state.easy = values.easy;
                    state.medium = values.medium; state.hard = values.hard;
                    state.songsAvailable = true; save();
                } catch (IllegalArgumentException invalid) { invalid("Músicas", invalid); }
            }
            if (activity != null && !activityDone) {
                activityDone = true;
                try {
                    DashboardMetrics values = calculate(emptyModules(), emptyProgress(), emptySongs(), emptyLearned(), activity);
                    state.weeklyGoal = values.weeklyGoal; state.accessDays = values.accessDays;
                    state.weeklyAccesses = values.weeklyAccesses; state.activityAvailable = true; save();
                } catch (IllegalArgumentException invalid) { invalid("/api/activity/summary", invalid); }
            }
            if (remaining == 0) calls.clear();
            String notice = errors.isEmpty() ? "" : "Não foi possível atualizar: " + android.text.TextUtils.join(", ", errors)
                    + (state.savedAt > 0 ? ". Dados disponíveis preservados." : ". Ainda sem dados sincronizados.")
                    + " Verifique a conexão ou tente novamente mais tarde.";
            listener.onResult(cached(), remaining > 0, notice);
        }
        DashboardMetrics calculate(LearningModuleListResponse modules, ModuleProgressListResponse progress,
                SongListResponse songs, SongProgressListResponse learned, ActivitySummaryResponse activity) {
            return DashboardMetrics.calculate(session.getUserId(), modules, progress, songs, learned, activity);
        }
        void save() {
            state.savedAt = System.currentTimeMillis();
            cache.edit().putString("snapshot", gson.toJson(state)).putBoolean("blocked", false).apply();
        }
        void invalid(String group, IllegalArgumentException error) {
            diagnostic(group, "JSON inválido: " + error.getMessage());
            errors.add(label(group) + " (resposta inválida)");
        }
    }
    // Empty groups are used only to validate/count another independent group, never as API success.
    private static LearningModuleListResponse emptyModules() { LearningModuleListResponse r = new LearningModuleListResponse(); r.items = new ArrayList<>(); return r; }
    private static ModuleProgressListResponse emptyProgress() { ModuleProgressListResponse r = new ModuleProgressListResponse(); r.items = new ArrayList<>(); return r; }
    private static SongListResponse emptySongs() { SongListResponse r = new SongListResponse(); r.items = new ArrayList<>(); return r; }
    private static SongProgressListResponse emptyLearned() { SongProgressListResponse r = new SongProgressListResponse(); r.items = new ArrayList<>(); return r; }
    private static ActivitySummaryResponse emptyActivity() { ActivitySummaryResponse r = new ActivitySummaryResponse(); r.weeklyGoal = 3; r.accessDaysThisWeek = 0; return r; }
    private void diagnostic(String path, String result) {
        // Temporary debug diagnostics: no token, user identifier, headers or response body.
        if (diagnostics) Log.d("DashboardRepository", path + " -> " + result);
    }
    private static String label(String path) {
        switch (path) {
            case "/api/modules": return "catálogo das Trivias";
            case "/api/module-progress": return "progresso das Trivias";
            case "/api/songs": return "catálogo de músicas";
            case "/api/songs/progress": return "músicas aprendidas";
            case "/api/activity/summary": return "atividade semanal";
            default: return path;
        }
    }
}
