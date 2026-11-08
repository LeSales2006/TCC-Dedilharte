package com.example.dedilharte;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.data.ArpeggioRepository;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.ArpeggioListResponse;
import com.example.dedilharte.network.model.ArpeggioResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(AndroidJUnit4.class)
public class ArpeggioRepositoryInstrumentedTest {
    @Test
    @SuppressWarnings("unchecked")
    public void emptyServerOverridesLegacyCacheAndNeverSeedsDefaults() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        com.example.dedilharte.auth.SessionManager session = new com.example.dedilharte.auth.SessionManager(context);
        String oldToken = session.getToken(), oldId = session.getUserId(), oldName = session.getName(),
                oldEmail = session.getEmail(), oldRole = session.getRole();
        int oldGoal = session.getWeeklyGoal();
        session.saveSession("arp-token", "arp-cache-test", "Test", "test@example.com", "admin");
        SharedPreferences prefs = context.getSharedPreferences("dedilharte_arpeggios", Context.MODE_PRIVATE);
        String oldCache = prefs.getString("cache", null);
        Field field = DedilharteApiClient.class.getDeclaredField("service");
        field.setAccessible(true);
        Object original = field.get(null);
        ArpeggioListResponse body = new ArpeggioListResponse();
        body.items = new java.util.ArrayList<>();
        boolean[] failed = {false};
        Object call = Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (proxy, method, args) -> {
            if (method.getName().equals("enqueue")) {
                Callback<ArpeggioListResponse> callback = (Callback<ArpeggioListResponse>) args[0];
                if (failed[0]) callback.onFailure((Call) proxy, new java.io.IOException("Offline"));
                else callback.onResponse((Call) proxy, Response.success(body));
            }
            return null;
        });
        try {
            prefs.edit().putString("cache", "[{\"id\":\"old\",\"title\":\"Old arpeggio\",\"active\":true,\"notes\":[{\"string\":1,\"fret\":0}]}]").commit();
            field.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                    new Class[]{DedilharteApiService.class}, (proxy, method, args) -> call));
            ArpeggioRepository repository = new ArpeggioRepository(context);
            repository.fetchPublic((items, success) -> { assertTrue(success); assertTrue(items.isEmpty()); });
            assertTrue(prefs.getString("cache", "").contains("old")); // Preserve legacy data without importing it.
            repository.fetchAdmin((items, success) -> { assertTrue(success); assertTrue(items.isEmpty()); });
            ArpeggioResponse item = new ArpeggioResponse();
            item.id = "server";
            item.title = "Server arpeggio";
            item.active = true;
            item.notes.add(new com.example.dedilharte.network.model.ArpeggioNote(1, 0));
            body.items.add(item);
            repository.fetchPublic((items, success) -> { assertTrue(success); assertEquals("server", items.get(0).id); });
            failed[0] = true;
            new ArpeggioRepository(context).fetchPublic((items, success) -> { assertFalse(success); assertEquals("server", items.get(0).id); });
            failed[0] = false;
            body.items.clear();
            repository.fetchPublic((items, success) -> { assertTrue(success); assertTrue(items.isEmpty()); });
            failed[0] = true;
            repository.fetchPublic((items, success) -> { assertFalse(success); assertTrue(items.isEmpty()); });
            prefs.edit().remove("cache").commit();
            failed[0] = false;
            new ArpeggioRepository(context).fetchPublic((items, success) -> { assertTrue(success); assertTrue(items.isEmpty()); });
        } finally {
            field.set(null, original);
            prefs.edit().putString("cache", oldCache).commit();
            session.saveSession(oldToken, oldId, oldName, oldEmail, oldRole, oldGoal);
        }
    }
}
