package com.example.dedilharte;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.AuthResponse;
import com.example.dedilharte.network.model.AuthenticatedUser;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRecoveryInstrumentedTest {
    @Test public void serverFailureKeepsSessionButUnauthorizedClearsIt() throws Exception {
        try (Fixture f = new Fixture()) {
            for (int status : new int[]{503, 401, 403}) {
                f.session.saveSession("expired-test-token", "auth-test", "Test", "test@example.com", "student");
                f.status = status;
                try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
                    scenario.onActivity(activity -> {
                        assertTrue(activity.findViewById(R.id.loginButton).isEnabled());
                        assertEquals(View.GONE, activity.findViewById(R.id.loginProgress).getVisibility());
                        assertEquals(status == 503, f.session.isLoggedIn());
                    });
                }
            }
        }
    }

    @Test public void successfulHttpWithoutTokenNeverOpensMainOrSavesInvalidSession() throws Exception {
        try (Fixture f = new Fixture()) {
            f.session.clearSession();
            f.status = 200;
            f.body = new AuthResponse();
            f.body.user = new AuthenticatedUser();
            f.body.user.id = "auth-test";
            try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
                scenario.onActivity(activity -> {
                    ((EditText) activity.findViewById(R.id.loginEmail)).setText("test@example.com");
                    ((EditText) activity.findViewById(R.id.loginPassword)).setText("Teste123");
                    activity.findViewById(R.id.loginButton).performClick();
                    assertFalse(f.session.isLoggedIn());
                    assertTrue(activity.findViewById(R.id.loginButton).isEnabled());
                });
            }
        }
    }

    @Test public void offlineAccessRequiresMatchingUserAndUnexpiredPreviouslySavedToken() throws Exception {
        try (Fixture f = new Fixture()) {
            for (long expires : new long[]{1, System.currentTimeMillis() / 1000 + 300}) {
                String payload = "{\"sub\":\"auth-test\",\"exp\":" + expires + "}";
                String encoded = android.util.Base64.encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
                f.session.saveSession("header." + encoded + ".signature", "auth-test", "Test", "test@example.com", "student");
                assertEquals(expires > 1, f.session.canUseOfflineSession());
                f.session.saveSession("header." + encoded + ".signature", "other", "Test", "test@example.com", "student");
                assertFalse(f.session.canUseOfflineSession());
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class Fixture implements AutoCloseable {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final SessionManager session = new SessionManager(context);
        final String token = session.getToken(), id = session.getUserId(), name = session.getName(),
                email = session.getEmail(), role = session.getRole();
        final int goal = session.getWeeklyGoal();
        final Field service;
        final Object original;
        int status = 503;
        AuthResponse body;
        Fixture() throws Exception {
            service = DedilharteApiClient.class.getDeclaredField("service");
            service.setAccessible(true);
            original = service.get(null);
            service.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(), new Class[]{DedilharteApiService.class},
                    (proxy, method, args) -> Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class},
                            (call, operation, values) -> {
                                if (operation.getName().equals("isCanceled")) return false;
                                if (operation.getName().equals("enqueue")) {
                                    Response response = status == 200 ? Response.success(body) : Response.error(status,
                                            okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{}"));
                                    ((Callback) values[0]).onResponse((Call) call, response);
                                }
                                return null;
                            })));
        }
        public void close() throws Exception {
            service.set(null, original);
            session.saveSession(token, id, name, email, role, goal);
        }
    }
}
