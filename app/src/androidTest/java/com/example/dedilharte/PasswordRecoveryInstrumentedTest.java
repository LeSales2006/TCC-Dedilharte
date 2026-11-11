package com.example.dedilharte;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.containsString;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.data.ThemeStore;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.RecoveryRequest;
import com.example.dedilharte.network.model.RecoveryResponse;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PasswordRecoveryInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    private Context context;
    private int status = 200, sends, resets;
    private boolean offline, empty;
    private RecoveryRequest last;
    private static final String GRANT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    @Before public void recoveryApi() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        setNight(false);
        Field service = DedilharteApiClient.class.getDeclaredField("service"); service.setAccessible(true);
        Object original = service.get(null);
        service.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(), new Class[]{DedilharteApiService.class}, (proxy, method, args) -> {
            String name = method.getName();
            if (!name.equals("requestRecoveryCode") && !name.equals("verifyRecoveryCode") && !name.equals("resetPassword") && !name.equals("login")) return method.invoke(original, args);
            if (!name.equals("login")) last = (RecoveryRequest) args[0];
            if (name.equals("requestRecoveryCode")) sends++;
            if (name.equals("resetPassword")) resets++;
            RecoveryResponse body = new RecoveryResponse(); body.requestId = "0bb95f93-7ac2-4f39-a4a4-8bf14f4e000" + sends;
            body.authorization = GRANT; body.resendAfter = 60; body.message = "Senha atualizada se houver conta elegível.";
            return Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (call, action, values) -> {
                if (action.getName().equals("enqueue")) {
                    Callback<Object> callback = (Callback<Object>) values[0];
                    if (offline) callback.onFailure((Call<Object>) call, new java.io.IOException("offline fixture"));
                    else if (status != 200 || name.equals("login")) callback.onResponse((Call<Object>) call,
                            Response.error(name.equals("login") ? 401 : status, okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{\"error\":\"Não foi possível concluir. Solicite outro código.\"}")));
                    else callback.onResponse((Call<Object>) call, Response.success(empty ? null : body));
                }
                return action.getName().equals("isCanceled") ? false : null;
            });
        }));
    }
    @After public void resetTheme() { setNight(false); }
    private void setNight(boolean night) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> new ThemeStore(context).setDarkModeEnabled(night));
    }
    private Intent recovery() {
        new SessionManager(context).clearSession();
        return new Intent(context, PasswordRecoveryActivity.class).putExtra("email", "test@example.com");
    }

    // API 36 can omit the legacy InputMethodManager callback used by Espresso's close action.
    private static androidx.test.espresso.ViewAction hideKeyboard() {
        return new androidx.test.espresso.ViewAction() {
            public org.hamcrest.Matcher<android.view.View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "hide IME through window insets"; }
            public void perform(androidx.test.espresso.UiController ui, android.view.View view) {
                androidx.core.view.WindowInsetsControllerCompat controller = androidx.core.view.ViewCompat.getWindowInsetsController(view);
                assertNotNull(controller); controller.hide(androidx.core.view.WindowInsetsCompat.Type.ime());
                ui.loopMainThreadForAtLeast(400);
                androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(view);
                assertNotNull(insets); assertFalse(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));
            }
        };
    }

    @Test public void loginAlwaysOffersRecoveryAndHighlightsAfterRejectedLogin() {
        new SessionManager(context).clearSession();
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.loginForgotPassword)).check(matches(withText("Esqueceu a senha?")));
            onView(withId(R.id.loginEmail)).perform(replaceText("test@example.com"));
            onView(withId(R.id.loginPassword)).perform(replaceText("Wrong123"), hideKeyboard());
            onView(withId(R.id.loginButton)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                TextView recovery = activity.findViewById(R.id.loginForgotPassword);
                assertEquals(0xff54d6dd, recovery.getCurrentTextColor()); assertTrue(recovery.getTypeface().isBold());
            });
            onView(withId(R.id.loginForgotPassword)).perform(scrollTo(), click());
            onView(withId(R.id.recoveryEmail)).check(matches(withText("test@example.com")));
            onView(withId(R.id.recoveryBack)).perform(scrollTo(), click());
        }
    }

    @Test public void clipboardCodeRotationGrantAndNewPasswordWorkInBothThemes() {
        for (boolean night : new boolean[]{false, true}) {
            setNight(night);
            try (ActivityScenario<PasswordRecoveryActivity> scenario = ActivityScenario.launch(recovery())) {
                scenario.onActivity(activity -> {
                    assertEquals(night, ThemeStore.isNight(activity));
                    assertEquals(0xff54d6dd, activity.findViewById(R.id.recoverySubmit).getBackgroundTintList().getDefaultColor());
                });
                onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click());
                onView(withId(R.id.recoveryResend)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
                scenario.onActivity(activity -> ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("code", "012345")));
                onView(withId(R.id.recoveryCode)).perform(click());
                onView(withId(R.id.recoveryCode)).check(matches(withText("012345")));
                onView(withId(R.id.recoveryCode)).perform(hideKeyboard());
                onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click());
                assertEquals("012345", last.code);
                scenario.recreate();
                onView(withId(R.id.recoveryStep)).check(matches(withText(containsString("3 de 3"))));
                onView(withId(R.id.recoveryPassword)).perform(replaceText("NewPassword123"));
                onView(withId(R.id.recoveryConfirm)).perform(replaceText("Mismatch123"), hideKeyboard());
                int before = resets;
                onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click()); assertEquals(before, resets);
                onView(withId(R.id.recoveryConfirm)).perform(scrollTo(), replaceText("NewPassword123"), hideKeyboard());
                status = 503;
                onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click());
                onView(withId(R.id.recoveryStep)).check(matches(withText(containsString("3 de 3"))));
                status = 200;
                // Second pass exercises the original bug: a 2xx reset with no response body.
                empty = night;
                onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click());
                assertEquals(before + 2, resets); assertNull(last.code); assertEquals(GRANT, last.authorization);
                assertEquals(last.newPassword, last.confirmPassword);
                onView(withId(R.id.loginEmail)).check(matches(withText("test@example.com")));
                onView(withId(R.id.loginRecoveryMessage))
                        .check(matches(isDisplayed()))
                        .check(matches(withText(containsString("Senha"))));
                assertFalse(new SessionManager(context).isLoggedIn());
            }
        }
    }

    @Test public void publicErrorsNetworkAndIncompleteSuccessNeverAdvanceOrClaimExpiredSession() {
        for (int code : new int[]{401, 403, 404, 500, 503, 200}) {
            status = code; empty = code == 200;
            try (ActivityScenario<PasswordRecoveryActivity> scenario = ActivityScenario.launch(recovery())) {
                onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click());
                onView(withId(R.id.recoveryStep)).check(matches(withText(containsString("1 de 3"))));
                onView(withId(R.id.recoverySubmit)).check(matches(isEnabled()));
                onView(withText(containsString("Sua sessão expirou"))).check(doesNotExist());
            }
        }
        status = 200; empty = false; offline = true;
        try (ActivityScenario<PasswordRecoveryActivity> scenario = ActivityScenario.launch(recovery())) {
            onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click());
            onView(withId(R.id.recoveryStatus)).check(matches(withText(containsString("Sem conexão"))));
            onView(withId(R.id.recoveryStep)).check(matches(withText(containsString("1 de 3"))));
        }
    }

    @Test public void incorrectCodeStaysOnVerificationAndRateLimitDisablesActionsUntilRetry() {
        try (ActivityScenario<PasswordRecoveryActivity> scenario = ActivityScenario.launch(recovery())) {
            onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click());
            onView(withId(R.id.recoveryCode)).perform(replaceText("111111"), hideKeyboard());
            status = 400; onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click());
            onView(withId(R.id.recoveryStep)).check(matches(withText(containsString("2 de 3"))));
            status = 429; onView(withId(R.id.recoverySubmit)).perform(scrollTo(), click());
            onView(withId(R.id.recoverySubmit)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            onView(withId(R.id.recoveryResend)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            // Advance the retained cooldown for the test without waiting a minute.
            scenario.onActivity(activity -> {
                PasswordRecoveryActivity.Flow state = new ViewModelProvider(activity).get(PasswordRecoveryActivity.Flow.class);
                state.retryUntil = 0; state.resendAt = 0;
            });
            scenario.recreate(); status = 200;
            int before = sends; onView(withId(R.id.recoveryResend)).perform(scrollTo(), click()); assertEquals(before + 1, sends);
            onView(withId(R.id.recoveryStep)).check(matches(withText(containsString("2 de 3"))));
        }
    }
}
