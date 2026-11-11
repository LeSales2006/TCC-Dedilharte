package com.example.dedilharte;

import static org.junit.Assert.*;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.RegisterRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import retrofit2.Call;

@RunWith(AndroidJUnit4.class)
public class StudentTermsInstrumentedTest {
    private int calls;
    private RegisterRequest sent;

    private AlertDialog dialog(RegisterActivity activity) {
        try {
            Field field = RegisterActivity.class.getDeclaredField("termsDialog");
            field.setAccessible(true);
            return (AlertDialog) field.get(activity);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void prepare(RegisterActivity activity) {
        try {
            Field field = RegisterActivity.class.getDeclaredField("api"); field.setAccessible(true);
            field.set(activity, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                new Class[]{DedilharteApiService.class}, (proxy, method, args) -> {
                    assertEquals("register", method.getName());
                    calls++; sent = (RegisterRequest) args[0];
                    return Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class},
                            (p, m, a) -> m.getName().equals("isCanceled") ? false : null);
                }));
        } catch (Exception error) { throw new AssertionError(error); }
        ((EditText) activity.findViewById(R.id.registerName)).setText("Aluno Teste");
        ((EditText) activity.findViewById(R.id.registerEmail)).setText("student@example.com");
        ((EditText) activity.findViewById(R.id.registerPassword)).setText("Password123");
        ((EditText) activity.findViewById(R.id.registerConfirmPassword)).setText("Password123");
        activity.findViewById(R.id.registerButton).performClick();
    }

    private void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }

    @Test public void scrollGateCancellationAndExplicitAcceptance() {
        try (ActivityScenario<RegisterActivity> scenario = ActivityScenario.launch(RegisterActivity.class)) {
            scenario.onActivity(activity -> {
                prepare(activity);
                assertEquals(0, calls);
                activity.findViewById(R.id.registerButton).performClick();
            });
            idle();
            scenario.onActivity(activity -> {
                AlertDialog d = dialog(activity); assertTrue(d.isShowing());
                assertFalse(d.findViewById(R.id.studentTermsAccept).isEnabled());
                assertFalse(d.findViewById(R.id.studentTermsSubmit).isEnabled());
                d.findViewById(R.id.studentTermsSubmit).performClick();
                assertEquals(0, calls);
                d.findViewById(R.id.studentTermsCancel).performClick();
                assertFalse(d.isShowing());
                assertEquals("Password123", ((EditText) activity.findViewById(R.id.registerPassword)).getText().toString());
                assertEquals("student@example.com", ((EditText) activity.findViewById(R.id.registerEmail)).getText().toString());
                activity.findViewById(R.id.registerButton).performClick();
            });
            idle();
            scenario.onActivity(activity -> {
                AlertDialog d = dialog(activity);
                assertFalse(d.findViewById(R.id.studentTermsAccept).isEnabled());
                ((ScrollView) d.findViewById(R.id.studentTermsScroll)).fullScroll(View.FOCUS_DOWN);
            });
            idle();
            scenario.onActivity(activity -> {
                AlertDialog d = dialog(activity);
                CheckBox agree = d.findViewById(R.id.studentTermsAccept);
                assertTrue(agree.isEnabled()); assertFalse(agree.isChecked());
                assertFalse(d.findViewById(R.id.studentTermsSubmit).isEnabled());
                agree.setChecked(true); assertTrue(d.findViewById(R.id.studentTermsSubmit).isEnabled());
                agree.setChecked(false); assertFalse(d.findViewById(R.id.studentTermsSubmit).isEnabled());
                agree.setChecked(true); d.findViewById(R.id.studentTermsSubmit).performClick();
                assertFalse(d.isShowing()); assertEquals(1, calls);
                assertTrue(sent.termsAccepted); assertEquals("student", sent.role);
                assertEquals(activity.getString(R.string.student_terms_version), sent.termsVersion);
                assertEquals("Password123", sent.password);
            });
        }
    }

    @Test public void existingValidationPreventsDialogAndNetwork() {
        try (ActivityScenario<RegisterActivity> scenario = ActivityScenario.launch(RegisterActivity.class)) {
            scenario.onActivity(activity -> {
                prepare(activity);
                ((EditText) activity.findViewById(R.id.registerConfirmPassword)).setText("Different123");
                activity.findViewById(R.id.registerButton).performClick();
                assertNotNull(((EditText) activity.findViewById(R.id.registerConfirmPassword)).getError());
                assertNull(dialog(activity)); assertEquals(0, calls);
            });
        }
    }
}
