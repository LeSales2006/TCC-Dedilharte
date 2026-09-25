package com.example.dedilharte;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuthenticationFlowInstrumentedTest {

    private Context targetContext;

    @Before
    public void setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clearProfile();
    }

    @After
    public void tearDown() {
        clearProfile();
    }

    @Test
    public void loginStudentRegistrationAndAdminRegistrationFlow() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.root);

                assertNotNull(findText(root, "Login"));
                findText(root, "CADASTRAR ALUNO").performClick();

                assertNotNull(findText(root, "Cadastro Aluno"));
                findInput(root, "Digite seu nome").setText("Teste Aluno");
                findText(root, "CRIAR CONTA").performClick();

                assertNotNull(findText(root, "INICIANTE"));
                assertNotNull(findTextContaining(root, "INTERMEDI"));

                activity.onBackPressed();
                assertNotNull(findText(root, "Login"));
                findText(root, "CADASTRAR ADMIN").performClick();

                assertNotNull(findText(root, "Cadastro Admin"));
                findInput(root, "Digite seu nome").setText("Teste Admin");
                findText(root, "CRIAR ADMIN").performClick();

                assertNotNull(findTextContaining(root, "Administra"));
                assertNotNull(findTextContaining(root, "NOVA M"));
            });
        }
    }

    private void clearProfile() {
        targetContext.getSharedPreferences("dedilharte_profile", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    private TextView findText(View view, String value) {
        TextView result = findTextContaining(view, value);
        if (result != null && value.contentEquals(result.getText())) {
            return result;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                result = findText(group.getChildAt(i), value);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private TextView findTextContaining(View view, String value) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            if (text != null && text.toString().contains(value)) {
                return textView;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = findTextContaining(group.getChildAt(i), value);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private EditText findInput(View view, String hint) {
        if (view instanceof EditText) {
            EditText editText = (EditText) view;
            CharSequence currentHint = editText.getHint();
            if (currentHint != null && hint.contentEquals(currentHint)) {
                return editText;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText result = findInput(group.getChildAt(i), hint);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
