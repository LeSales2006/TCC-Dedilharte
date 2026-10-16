package com.example.dedilharte;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityEncodingInstrumentedTest {

    private Context targetContext;

    @Before
    public void setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        saveStudentSession();
    }

    @After
    public void tearDown() {
        targetContext.getSharedPreferences("dedilharte_session", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        targetContext.getSharedPreferences("dedilharte_profile", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void courseScreenDoesNotShowMojibake() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                String visibleText = collectText(activity.findViewById(R.id.root));

                assertTrue(visibleText.contains("Trivia"));
                assertFalse(visibleText.contains("Conhecendo as cordas"));
                assertFalse(visibleText.contains("CONTINUAR: AULA"));
                assertFalse(visibleText.contains("\u00c3"));
                assertFalse(visibleText.contains("\u00c2"));
                assertFalse(visibleText.contains("\u00e2"));
                assertFalse(visibleText.contains("\ufffd"));
            });
        }
    }

    private void saveStudentSession() {
        targetContext.getSharedPreferences("dedilharte_session", Context.MODE_PRIVATE)
                .edit()
                .putString("jwt_token", "debug-token")
                .putString("user_id", "debug-user")
                .putString("name", "Leticia Teste")
                .putString("email", "leticia@example.com")
                .putString("role", "student")
                .putInt("weekly_goal", 3)
                .commit();
    }

    private String collectText(View view) {
        StringBuilder builder = new StringBuilder();
        appendText(view, builder);
        return builder.toString();
    }

    private void appendText(View view, StringBuilder builder) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) {
                builder.append(text).append('\n');
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                appendText(group.getChildAt(i), builder);
            }
        }
    }
}
