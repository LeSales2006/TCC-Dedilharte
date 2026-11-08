package com.example.dedilharte;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.data.SongRepository;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.SongListResponse;
import com.example.dedilharte.network.model.SongResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(AndroidJUnit4.class)
public class SongRepositoryInstrumentedTest {
    @Test
    @SuppressWarnings("unchecked")
    public void cacheSurvivesOutagesWithoutDraftsOrCrossUserLeakage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        com.example.dedilharte.auth.SessionManager session = new com.example.dedilharte.auth.SessionManager(context);
        String oldToken = session.getToken(), oldId = session.getUserId(), oldName = session.getName(),
                oldEmail = session.getEmail(), oldRole = session.getRole();
        int oldGoal = session.getWeeklyGoal();
        session.saveSession("cache-test-token", "cache-test", "Cache Test", "cache@example.com", "admin");
        SharedPreferences prefs = context.getSharedPreferences("dedilharte_songs", Context.MODE_PRIVATE);
        String oldSongs = prefs.getString("songs", null);
        String oldCache = prefs.getString("published_cache", null);
        Field field = DedilharteApiClient.class.getDeclaredField("service");
        field.setAccessible(true);
        Object original = field.get(null);
        SongListResponse body = new SongListResponse();
        body.items = new java.util.ArrayList<>();
        boolean[] failed = {false};
        boolean[] serverError = {false};
        int[] errorStatus = {500};
        boolean[] delayed = {false};
        java.util.List<Callback<SongListResponse>> pending = new java.util.ArrayList<>();
        Object call = Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (proxy, method, args) -> {
            if (method.getName().equals("enqueue")) {
                Callback<SongListResponse> callback = (Callback<SongListResponse>) args[0];
                if (delayed[0]) { pending.add(callback); return null; }
                if (failed[0]) callback.onFailure((Call) proxy, new java.io.IOException("Offline"));
                else if (serverError[0]) callback.onResponse((Call) proxy, Response.error(errorStatus[0],
                        okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),
                                "{\"error\":\"Erro interno do servidor.\"}")));
                else callback.onResponse((Call) proxy, Response.success(body));
            }
            return null;
        });
        try {
            prefs.edit().putString("songs", "[{\"id\":\"local\",\"title\":\"Local song\",\"status\":\"publicada\",\"inQueue\":true}]")
                    .putString("published_cache", "[]").commit();
            field.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                    new Class[]{DedilharteApiService.class}, (proxy, method, args) -> call));
            SongRepository repository = new SongRepository(context);
            assertTrue(repository.publishedQueued().isEmpty());
            assertTrue(repository.all().isEmpty());
            repository.fetchPublishedQueued((songs, success) -> { assertTrue(success); assertTrue(songs.isEmpty()); });
            SongResponse song = new SongResponse();
            song.id = "server";
            song.title = "Server song";
            song.active = true;
            song.bpm = 92;
            song.tablature = "e|--0--|\nB|-----|\nG|-----|\nD|-----|\nA|-----|\nE|-----|";
            body.items.add(song);
            repository.fetchPublishedQueued((songs, success) -> { assertTrue(success); assertEquals("server", songs.get(0).getId()); });
            assertEquals(92, repository.publishedQueued().get(0).getRecommendedBpm());
            assertEquals(song.tablature, repository.publishedQueued().get(0).getTablature());
            song.active = false;
            repository.fetchPublishedQueued((songs, success) -> { assertTrue(success); assertTrue(songs.isEmpty()); });
            repository.fetchAdmin((songs, success) -> { assertTrue(success); assertEquals(1, songs.size()); assertFalse(songs.get(0).isActive()); });
            song.active = true;
            body.items.clear();
            repository.fetchPublishedQueued((songs, success) -> { assertTrue(success); assertTrue(songs.isEmpty()); });
            assertTrue(repository.publishedQueued().isEmpty());
            body.items.add(song);
            repository.fetchPublishedQueued((songs, success) -> assertEquals(1, songs.size()));
            failed[0] = true;
            repository.fetchPublishedQueued((songs, success) -> { assertFalse(success); assertEquals(1, songs.size()); });
            assertEquals(1, repository.publishedQueued().size());
            assertEquals(1, new SongRepository(context).publishedQueued().size());
            assertNull(repository.get("local"));
            repository.fetchAdmin((songs, success) -> { assertFalse(success); assertTrue(songs.isEmpty()); });
            repository.fetchPublishedQueuedWithErrors((songs, success, error) -> {
                assertFalse(success);
                assertTrue(error.contains("conectar"));
            });
            failed[0] = false;
            serverError[0] = true;
            repository.fetchPublishedQueuedWithErrors((songs, success, error) -> {
                assertFalse(success);
                assertEquals(1, songs.size());
                assertTrue(error.contains("servidor"));
                assertFalse(error.contains("conexão"));
            });
            serverError[0] = false;
            failed[0] = true;
            repository.save(SongRepository.fromResponse(song).withActive(false), false,
                    (saved, error) -> assertNull(saved));
            repository.delete("server", success -> assertFalse(success));
            failed[0] = false;
            assertEquals(1, repository.all().size());
            assertEquals(1, repository.publishedQueued().size());
            session.saveSession("other-token", "cache-other", "Other", "other@example.com", "student");
            assertTrue(repository.publishedQueued().isEmpty());
            assertTrue(new SongRepository(context).publishedQueued().isEmpty());
            session.saveSession("cache-test-token", "cache-test", "Cache Test", "cache@example.com", "admin");
            assertEquals(1, new SongRepository(context).publishedQueued().size());
            body.items = null;
            repository.fetchPublishedQueued((songs, success) -> { assertFalse(success); assertEquals(1, songs.size()); });
            body.items = new java.util.ArrayList<>();
            body.items.add(song);
            errorStatus[0] = 401;
            serverError[0] = true;
            repository.fetchPublishedQueued((songs, success) -> { assertFalse(success); assertTrue(songs.isEmpty()); });
            failed[0] = true;
            repository.fetchPublishedQueued((songs, success) -> { assertFalse(success); assertTrue(songs.isEmpty()); });
            failed[0] = false;
            serverError[0] = false;
            repository.fetchPublishedQueued((songs, success) -> { assertTrue(success); assertEquals(1, songs.size()); });
            body.items.clear();
            repository.fetchPublishedQueued((songs, success) -> { assertTrue(success); assertTrue(songs.isEmpty()); });
            body.items.clear();
            repository.fetchAdmin((songs, success) -> { assertTrue(success); assertTrue(songs.isEmpty()); });
            assertTrue(repository.all().isEmpty());
            delayed[0] = true;
            repository.fetchPublishedQueued((songs, success) -> fail("Older response must be ignored"));
            repository.fetchPublishedQueued((songs, success) -> assertTrue(songs.isEmpty()));
            pending.get(1).onResponse(null, Response.success(body));
            body.items.add(song);
            pending.get(0).onResponse(null, Response.success(body));
            assertTrue(repository.publishedQueued().isEmpty());
            pending.clear();
            repository.fetchPublishedQueued((songs, success) -> fail("Old session must be ignored"));
            session.saveSession("other-token", "cache-other", "Other", "other@example.com", "student");
            pending.get(0).onResponse(null, Response.success(body));
            assertTrue(new SongRepository(context).publishedQueued().isEmpty());
        } finally {
            field.set(null, original);
            prefs.edit().putString("songs", oldSongs).putString("published_cache", oldCache).commit();
            session.saveSession(oldToken, oldId, oldName, oldEmail, oldRole, oldGoal);
        }
    }
}
