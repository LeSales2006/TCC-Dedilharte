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
    public void emptyAndFailedResponsesNeverRestoreLocalSongs() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("dedilharte_songs", Context.MODE_PRIVATE);
        String oldSongs = prefs.getString("songs", null);
        String oldCache = prefs.getString("published_cache", null);
        Field field = DedilharteApiClient.class.getDeclaredField("service");
        field.setAccessible(true);
        Object original = field.get(null);
        SongListResponse body = new SongListResponse();
        boolean[] failed = {false};
        Object call = Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (proxy, method, args) -> {
            if (method.getName().equals("enqueue")) {
                Callback<SongListResponse> callback = (Callback<SongListResponse>) args[0];
                if (failed[0]) callback.onFailure((Call) proxy, new java.io.IOException("Offline"));
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
            repository.fetchPublishedQueued((songs, success) -> { assertTrue(success); assertTrue(songs.isEmpty()); });
            SongResponse song = new SongResponse();
            song.id = "server";
            song.title = "Server song";
            song.status = "publicada";
            song.inQueue = true;
            body.items.add(song);
            repository.fetchPublishedQueued((songs, success) -> { assertTrue(success); assertEquals("server", songs.get(0).getId()); });
            body.items.clear();
            repository.fetchPublishedQueued((songs, success) -> { assertTrue(success); assertTrue(songs.isEmpty()); });
            assertTrue(repository.publishedQueued().isEmpty());
            body.items.add(song);
            repository.fetchPublishedQueued((songs, success) -> assertEquals(1, songs.size()));
            failed[0] = true;
            repository.fetchPublishedQueued((songs, success) -> { assertFalse(success); assertTrue(songs.isEmpty()); });
            assertTrue(repository.publishedQueued().isEmpty());
            assertEquals("Local song", repository.get("local").getTitle());
            repository.fetchAdmin((songs, success) -> { assertFalse(success); assertTrue(songs.isEmpty()); });
            repository.save(repository.get("local").withStatus("arquivada"), success -> assertFalse(success));
            assertEquals("publicada", repository.get("local").getStatus());
            repository.delete("local", success -> assertFalse(success));
            assertNotNull(repository.get("local"));
            failed[0] = false;
            body.items.clear();
            repository.fetchAdmin((songs, success) -> { assertTrue(success); assertTrue(songs.isEmpty()); });
            assertTrue(repository.all().isEmpty());
        } finally {
            field.set(null, original);
            prefs.edit().putString("songs", oldSongs).putString("published_cache", oldCache).commit();
        }
    }
}
