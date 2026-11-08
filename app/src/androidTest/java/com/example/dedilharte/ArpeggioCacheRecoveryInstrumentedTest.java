package com.example.dedilharte;

import static org.junit.Assert.*;
import android.content.Context;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.data.ArpeggioRepository;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.ArpeggioListResponse;
import com.example.dedilharte.network.model.ArpeggioNote;
import com.example.dedilharte.network.model.ArpeggioResponse;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(AndroidJUnit4.class)
public class ArpeggioCacheRecoveryInstrumentedTest {
    private Context context;
    private SessionManager session;
    private String oldToken, oldId, oldName, oldEmail, oldRole, testId;
    private int oldGoal;
    private Field serviceField;
    private Object originalService;
    private ArpeggioRepository repository;
    private ArpeggioListResponse body;
    private boolean offline, delayed;
    private int status = 200;
    private final List<Callback<ArpeggioListResponse>> pending = new ArrayList<>();

    @Before @SuppressWarnings({"unchecked", "rawtypes"})
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        session = new SessionManager(context);
        oldToken = session.getToken(); oldId = session.getUserId(); oldName = session.getName();
        oldEmail = session.getEmail(); oldRole = session.getRole(); oldGoal = session.getWeeklyGoal();
        testId = "arp-recovery-" + UUID.randomUUID();
        session.saveSession("test-token", testId, "Test", "test@example.com", "admin");
        serviceField = DedilharteApiClient.class.getDeclaredField("service");
        serviceField.setAccessible(true);
        originalService = serviceField.get(null);
        body = list(item());
        serviceField.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                new Class[]{DedilharteApiService.class}, (service, method, args) ->
                Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (call, callMethod, callArgs) -> {
                    if (!callMethod.getName().equals("enqueue")) return null;
                    Callback callback = (Callback) callArgs[0];
                    if (method.getName().equals("deleteArpeggio")) callback.onResponse((Call) call, Response.success(204, null));
                    else if (delayed) pending.add(callback);
                    else if (offline) callback.onFailure((Call) call, new java.io.IOException("Offline"));
                    else if (status != 200) callback.onResponse((Call) call, Response.error(status,
                            okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{}")));
                    else callback.onResponse((Call) call, Response.success(body));
                    return null;
                })));
        repository = new ArpeggioRepository(context);
        repository.fetchPublic((items, network) -> { assertTrue(network); assertEquals(1, items.size()); });
    }

    @After public void tearDown() throws Exception {
        serviceField.set(null, originalService);
        String owner = Base64.encodeToString(testId.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        context.getSharedPreferences("dedilharte_arpeggios_v2_" + owner, Context.MODE_PRIVATE).edit().clear().commit();
        session.saveSession(oldToken, oldId, oldName, oldEmail, oldRole, oldGoal);
    }

    private ArpeggioResponse item() {
        ArpeggioResponse item = new ArpeggioResponse();
        item.id = "published"; item.title = "Published"; item.active = true;
        item.notes.add(new ArpeggioNote(1, 0));
        return item;
    }

    private ArpeggioListResponse list(ArpeggioResponse... items) {
        ArpeggioListResponse result = new ArpeggioListResponse();
        result.items = new ArrayList<>(Arrays.asList(items));
        return result;
    }

    private void assertOfflineCount(int count) {
        offline = true;
        new ArpeggioRepository(context).fetchPublic((items, network) -> {
            assertFalse(network); assertEquals(count, items.size());
        });
        offline = false;
    }

    @Test public void malformedResponsesPreserveCacheAndExplicitRemovalClearsIt() {
        ArpeggioResponse noNotes = item(); noNotes.notes = null;
        ArpeggioResponse noPublication = item(); noPublication.active = null;
        ArpeggioResponse badString = item(); badString.notes.get(0).string = 7;
        for (ArpeggioResponse invalid : new ArpeggioResponse[]{null, noNotes, noPublication, badString}) {
            body = list(item(), invalid);
            repository.fetchPublic((items, network) -> { assertFalse(network); assertEquals(1, items.size()); });
            assertOfflineCount(1);
        }
        body.items = null;
        repository.fetchPublic((items, network) -> { assertFalse(network); assertEquals(1, items.size()); });
        status = 503;
        repository.fetchPublic((items, network) -> { assertFalse(network); assertEquals(1, items.size()); });
        status = 200;
        body = list(item()); body.items.get(0).active = false;
        repository.fetchPublic((items, network) -> { assertTrue(network); assertTrue(items.isEmpty()); });
        assertOfflineCount(0);
        body = list(item()); repository.fetchPublic(null);
        body = list(); repository.fetchPublic((items, network) -> { assertTrue(network); assertTrue(items.isEmpty()); });
        assertOfflineCount(0);
    }

    @Test public void delayedReadsAcrossInstancesCannotUndoNewListOrDeletion() {
        delayed = true;
        repository.fetchPublic((items, network) -> fail("Older response must be ignored"));
        new ArpeggioRepository(context).fetchPublic((items, network) -> assertTrue(items.isEmpty()));
        pending.get(1).onResponse(null, Response.success(list()));
        pending.get(0).onResponse(null, Response.success(list(item())));
        delayed = false;
        assertOfflineCount(0);
        repository.fetchPublic(null);
        pending.clear(); delayed = true;
        repository.fetchPublic((items, network) -> fail("Read before deletion must be ignored"));
        new ArpeggioRepository(context).delete("published", (success, code) -> assertTrue(success));
        pending.get(0).onResponse(null, Response.success(list(item())));
        delayed = false;
        assertOfflineCount(0);
    }

    @Test public void publicDenialBlocksOfflineUntilValidPublicRevalidation() {
        status = 403;
        repository.fetchAdmin((items, network) -> { assertFalse(network); assertTrue(items.isEmpty()); });
        assertOfflineCount(1); // Administrative permission does not govern public cache access.
        for (int denied : new int[]{401, 403}) {
            status = denied;
            repository.fetchPublic((items, network) -> { assertFalse(network); assertTrue(items.isEmpty()); });
            assertOfflineCount(0);
            status = 200;
            repository.fetchAdmin(null);
            assertOfflineCount(0);
            repository.fetchPublic((items, network) -> { assertTrue(network); assertEquals(1, items.size()); });
            assertOfflineCount(1);
        }
    }

    @Test public void oldSessionCannotDeliverResponsesOrReadAnotherUsersCache() {
        delayed = true;
        repository.fetchPublic((items, network) -> fail("Old session response must be ignored"));
        session.saveSession("other-token", testId + "-other", "Other", "other@example.com", "student");
        pending.get(0).onResponse(null, Response.success(body));
        delayed = false;
        assertOfflineCount(0);
        repository.fetchPublic((items, network) -> { assertFalse(network); assertTrue(items.isEmpty()); });
        new ArpeggioRepository(context).fetchAdmin((items, network) -> { assertFalse(network); assertTrue(items.isEmpty()); });
    }
}
