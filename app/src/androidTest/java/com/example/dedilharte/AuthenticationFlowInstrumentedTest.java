package com.example.dedilharte;
import static org.junit.Assert.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.auth.SessionManager;
import org.junit.Test;

/** Covers authenticated entry; local admin registration no longer exists. */
public class AuthenticationFlowInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    @Test public void loginHasRegistrationAndRecoveryAndNoLocalAdminRegistration() {
        new SessionManager(InstrumentationRegistry.getInstrumentation().getTargetContext()).clearSession();
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.loginCreateAccount));
                assertNotNull(activity.findViewById(R.id.loginForgotPassword));
                assertFalse(text(activity.findViewById(android.R.id.content)).contains("CADASTRAR ADMIN"));
            });
        }
    }
    @Test public void studentAndAdminEnterCorrectScreensAndAdminPreviewOpensProgress() {
        SessionManager session = new SessionManager(InstrumentationRegistry.getInstrumentation().getTargetContext());
        for (String role : new String[]{"student", "admin"}) {
            session.updateUser("Teste", "test@example.com", role);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    View root = activity.findViewById(R.id.root);
                    assertTrue(text(root).contains("admin".equals(role) ? "Painel administrativo" : "Seu progresso"));
                    if ("admin".equals(role)) {
                        assertTrue(click(root, "Visualizar app como aluno"));
                        assertTrue(text(root).contains("Seu progresso"));
                    }
                });
            }
        }
    }
    private static boolean click(View view, String value) {
        if (view instanceof TextView && value.equalsIgnoreCase(((TextView)view).getText().toString())) return view.performClick();
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) if (click(((ViewGroup)view).getChildAt(i), value)) return true;
        return false;
    }
    private static String text(View view) {
        String value = view instanceof TextView ? ((TextView)view).getText().toString() : "";
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) value += "\n" + text(((ViewGroup)view).getChildAt(i));
        return value;
    }
}
