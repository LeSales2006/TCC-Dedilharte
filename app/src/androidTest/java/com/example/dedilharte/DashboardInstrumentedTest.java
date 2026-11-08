package com.example.dedilharte;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.data.*;
import com.example.dedilharte.model.Song;
import com.example.dedilharte.network.*;
import com.example.dedilharte.network.model.*;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;

public class DashboardInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    private Context context;
    private int completed = 1;
    private boolean offline, forbidden, malformed, hold, learned;
    private String failedMethod = "";
    private final List<Runnable> pending = new ArrayList<>();
    private final LearningModuleListResponse modules = new LearningModuleListResponse();
    private final SongListResponse songs = new SongListResponse();

    @Before public void dashboardApi() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        session("debug-user"); clearCache("debug-user"); clearCache("dashboard-other");
        modules.items = Arrays.asList(module(DashboardMetrics.BEGINNER), module(DashboardMetrics.INTERMEDIATE));
        songs.items = new ArrayList<>();
        for (String difficulty : Arrays.asList("easy", "medium", "hard")) {
            SongResponse song = new SongResponse(); song.id = difficulty; song.title = difficulty;
            song.difficulty = difficulty; song.active = true; songs.items.add(song);
        }
        Field service = DedilharteApiClient.class.getDeclaredField("service"); service.setAccessible(true);
        Object original = service.get(null);
        service.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(), new Class[]{DedilharteApiService.class},
                (proxy, method, args) -> {
                    Object body;
                    switch (method.getName()) {
                        case "getModules": body = modules; break;
                        case "getSongs": body = songs; break;
                        case "getModuleProgress": body = progress(); break;
                        case "getSongProgress": {
                            SongProgressListResponse list = new SongProgressListResponse(); list.items = Collections.singletonList(songProgress()); body = list; break;
                        }
                        case "getActivitySummary": {
                            ActivitySummaryResponse activity = new ActivitySummaryResponse();
                            if (!malformed) { activity.weeklyGoal = 3; activity.accessDaysThisWeek = 2; activity.weeklyAccesses = new int[]{0, 1, 3, 2}; }
                            body = activity; break;
                        }
                        case "upsertModuleProgress": if (!offline) completed = 2; body = null; break;
                        case "upsertSongProgress": if (!offline) learned = ((SongProgressRequest) args[0]).learned; body = songProgress(); break;
                        default: return method.invoke(original, args); // Pending mock, never the real API.
                    }
                    return call(body, method.getName().equals(failedMethod));
                }));
    }

    @Test public void dashboardRefreshesAfterLessonAndKeepsNavigation() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                String visible = text(activity.findViewById(R.id.root));
                assertTrue(visible.contains("Meta semanal: 2 de 3 dias")); assertTrue(visible.contains("67%"));
                assertTrue(visible.contains("1 de 5 aulas concluídas")); assertTrue(visible.contains("20%"));
                tap(activity, "TRIVIA INICIANTE");
                tap(activity, "Abrir aula: Aula remota 2"); tap(activity, "INICIAR AULA"); tap(activity, "Resposta A");
                assertTrue(text(activity.findViewById(R.id.root)).contains("RESPOSTA ENVIADA"));
                activity.getOnBackPressedDispatcher().onBackPressed();
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertTrue(text(activity.findViewById(R.id.root)).contains("2 de 5 aulas concluídas"));
                assertTrue(text(activity.findViewById(R.id.root)).contains("40%"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertTrue(text(activity.findViewById(R.id.root)).contains("2 de 5 aulas concluídas")));
            Thread.sleep(700); // Let the 600 ms progress animations finish before the visual artifact.
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try (FileOutputStream out = new FileOutputStream(new File(context.getExternalFilesDir(null), "dashboard-preview.png"))) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            scenario.onActivity(activity -> {
                offline = true; tap(activity, "ATUALIZAR");
                String visible = text(activity.findViewById(R.id.root));
                assertTrue(visible.contains("Dados salvos em")); assertTrue(visible.contains("2 de 5 aulas concluídas"));
                assertTrue(visible.contains("Verifique a conexão"));
            });
        }
    }

    @Test public void snapshotPersistsOfflineAndRejectsUnauthorizedAndOtherUsers() {
        ui(() -> {
            DashboardRepository repo = new DashboardRepository(context);
            DashboardMetrics[] result = {null};
            repo.refresh((data, loading, notice) -> { if (!loading) result[0] = data; });
            assertEquals(20, result[0].beginner.percent());
            repo = new DashboardRepository(context); assertEquals(20, repo.cached().beginner.percent());
            offline = true;
            repo.refresh((data, loading, notice) -> { if (!loading) { assertNotNull(data); assertFalse(notice.isEmpty()); } });
            offline = false; malformed = true;
            repo.refresh((data, loading, notice) -> { if (!loading) assertEquals(20, data.beginner.percent()); });
            malformed = false; forbidden = true;
            repo.refresh((data, loading, notice) -> { if (!loading) assertNull(data); });
            assertNull(repo.cached());
            forbidden = false; offline = true;
            repo.refresh((data, loading, notice) -> { if (!loading) assertNull(data); });
            offline = false; repo.refresh((data, loading, notice) -> {}); assertNotNull(repo.cached());
            session("dashboard-other"); assertNull(repo.cached()); assertNull(new DashboardRepository(context).cached());
        });
    }

    @Test public void lateResponsesCannotOverwriteNewSnapshotOrCrossSessions() {
        ui(() -> {
            DashboardRepository repo = new DashboardRepository(context);
            hold = true; repo.refresh((data, loading, notice) -> {});
            assertEquals(5, pending.size());
            hold = false; completed = 2; repo.refresh((data, loading, notice) -> {});
            for (Runnable response : new ArrayList<>(pending)) response.run(); pending.clear();
            assertEquals(40, repo.cached().beginner.percent());
            hold = true; repo.refresh((data, loading, notice) -> {});
            session("dashboard-other");
            for (Runnable response : pending) response.run(); pending.clear();
            assertNull(repo.cached()); assertNull(new DashboardRepository(context).cached());
        });
    }

    @Test public void emptyAndFailedFirstLoadDoNotInventPercentages() {
        ui(() -> {
            DashboardRepository repo = new DashboardRepository(context); offline = true;
            repo.refresh((data, loading, notice) -> { if (!loading) { assertNull(data); assertFalse(notice.isEmpty()); } });
            offline = false; modules.items = Collections.emptyList(); songs.items = Collections.emptyList();
            repo.refresh((data, loading, notice) -> { if (!loading) { assertEquals(0, data.songs.total); assertEquals(0, data.beginner.total); } });
        });
    }

    @Test public void songProgressIsConfirmedOnlyAfterServerSave() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    Song song = SongRepository.fromResponse(songs.items.get(0));
                    Method method = MainActivity.class.getDeclaredMethod("songLearnedButton", Song.class); method.setAccessible(true);
                    TextView button = (TextView) method.invoke(activity, song);
                    offline = true; button.performClick();
                    assertFalse(new SongProgressStore(context, "debug-user").isLearned(song.getId()));
                    assertEquals("APRENDI ESTA MÚSICA", button.getText().toString());
                    offline = false; button.performClick();
                    assertTrue(new SongProgressStore(context, "debug-user").isLearned(song.getId()));
                    assertEquals("MÚSICA APRENDIDA ✓", button.getText().toString());
                    assertTrue(text(activity.findViewById(R.id.root)).contains("1 de 3"));
                } catch (Exception error) { throw new AssertionError(error); }
            });
        }
    }

    @Test public void aSingleFailedEndpointDoesNotDiscardOtherGroupsAndRecovers() {
        ui(() -> {
            for (String endpoint : Arrays.asList("getModules", "getModuleProgress", "getSongs", "getSongProgress", "getActivitySummary")) {
                clearCache("debug-user"); failedMethod = endpoint; learned = true;
                DashboardRepository repo = new DashboardRepository(context);
                DashboardMetrics[] result = {null}; String[] notice = {""};
                repo.refresh((data, loading, message) -> { if (!loading) { result[0] = data; notice[0] = message; } });
                assertNotNull(endpoint, result[0]); assertTrue(notice[0].contains("HTTP 500"));
                if (!endpoint.equals("getActivitySummary")) assertEquals(2, result[0].accessDays);
                if (!endpoint.equals("getModules") && !endpoint.equals("getModuleProgress")) assertEquals(20, result[0].beginner.percent());
                if (!endpoint.equals("getSongs") && !endpoint.equals("getSongProgress")) assertEquals(1, result[0].songs.completed);
                assertFalse(result[0].complete());
                failedMethod = "";
                repo.refresh((data, loading, message) -> { if (!loading) { assertTrue(data.complete()); assertEquals("", message); } });
                repo.invalidate(); offline = true;
                repo.refresh((data, loading, message) -> { if (!loading) { assertTrue(data.complete()); assertEquals(1, data.songs.completed); } });
                offline = false;
            }
        });
    }

    @Test public void failedGroupKeepsPreviousValueWhileOthersAdvance() {
        ui(() -> {
            DashboardRepository repo = new DashboardRepository(context);
            repo.refresh((data, loading, message) -> {});
            failedMethod = "getModuleProgress"; completed = 2; learned = true;
            repo.refresh((data, loading, message) -> {
                if (!loading) { assertEquals(1, data.beginner.completed); assertEquals(1, data.songs.completed); }
            });
            failedMethod = "";
            repo.refresh((data, loading, message) -> { if (!loading) assertEquals(2, data.beginner.completed); });
        });
    }

    @Test public void initialErrorAndEmptyHistoryShowZerosAndLocalLevel() {
        offline = true;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                String visible = text(activity.findViewById(R.id.root));
                assertFalse(visible.contains("Aguardando")); assertFalse(visible.contains("—"));
                assertTrue(visible.contains("Nível atual: Iniciante"));
                assertTrue(visible.contains("0%")); assertTrue(visible.contains("0 aulas concluídas"));
                offline = false; completed = 0;
                tap(activity, "ATUALIZAR");
                assertTrue(text(activity.findViewById(R.id.root)).contains("0 de 5 aulas concluídas"));
            });
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) private Object call(Object body, boolean serverError) {
        return Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (proxy, method, args) -> {
            if (method.getName().equals("enqueue")) {
                Callback callback = (Callback) args[0];
                Runnable response = () -> {
                    if (offline) callback.onFailure((Call) proxy, new java.io.IOException("offline"));
                    else if (forbidden) callback.onResponse((Call) proxy, Response.error(403, ResponseBody.create(null, "denied")));
                    else if (serverError) callback.onResponse((Call) proxy, Response.error(500, ResponseBody.create(null, "server failure")));
                    else callback.onResponse((Call) proxy, Response.success(body));
                };
                if (hold) pending.add(response); else response.run();
            }
            return null;
        });
    }
    private ModuleProgressListResponse progress() {
        ModuleProgressListResponse list = new ModuleProgressListResponse(); list.items = new ArrayList<>();
        for (int i = 0; i < completed; i++) { ModuleProgressListResponse.Item item = new ModuleProgressListResponse.Item(); item.itemId = DashboardMetrics.BEGINNER + i; item.userId = "debug-user"; item.completed = true; list.items.add(item); }
        return list;
    }
    private SongProgressResponse songProgress() { SongProgressResponse record = new SongProgressResponse(); record.user_id = "debug-user"; record.song_id = "easy"; record.learned = learned; return record; }
    private LearningModuleResponse module(String id) {
        LearningModuleResponse module = new LearningModuleResponse(); module.id = id; module.name = id;
        for (int i = 0; i < 5; i++) {
            LearningModuleItemResponse item = new LearningModuleItemResponse(); item.id = id + i; item.moduleId = id; item.title = "Aula remota " + (i + 1);
            item.itemType = "multiple_choice"; item.multipleChoice = new MultipleChoiceResponse(); item.multipleChoice.question = "Pergunta do servidor";
            item.multipleChoice.options = Arrays.asList("Resposta A", "Resposta B"); module.items.add(item);
        }
        return module;
    }
    private void session(String id) {
        String payload = Base64.encodeToString(("{\"sub\":\"" + id + "\",\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + "}").getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        new SessionManager(context).saveSession("test." + payload + ".test", id, "Estudante Teste", "test@example.com", "student");
    }
    private void clearCache(String id) {
        String encoded = Base64.encodeToString(id.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        context.getSharedPreferences("dashboard_v2_" + encoded, Context.MODE_PRIVATE).edit().clear().commit();
    }
    private static void ui(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private static String text(View view) {
        String result = view instanceof TextView ? ((TextView) view).getText().toString() : "";
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) result += "\n" + text(group.getChildAt(i)); }
        return result;
    }
    private static void tap(MainActivity activity, String label) { assertTrue("Missing: " + label, tap(activity.findViewById(R.id.root), label)); }
    private static boolean tap(View view, String label) {
        if ((view instanceof TextView && label.contentEquals(((TextView) view).getText())) || label.equals(view.getContentDescription())) return view.performClick();
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) if (tap(group.getChildAt(i), label)) return true; }
        return false;
    }
}
