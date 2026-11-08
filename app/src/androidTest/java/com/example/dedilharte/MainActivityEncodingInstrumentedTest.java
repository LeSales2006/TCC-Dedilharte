package com.example.dedilharte;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import com.example.dedilharte.data.ProgressStore;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import retrofit2.Call;

@RunWith(AndroidJUnit4.class)
public class MainActivityEncodingInstrumentedTest {

    private Context targetContext;
    private Field serviceField;
    private Object originalService;

    @Before
    public void setUp() throws Exception {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        saveStudentSession();
        new ProgressStore(targetContext, "debug-user").deleteUserData("debug-user");
        serviceField = DedilharteApiClient.class.getDeclaredField("service");
        serviceField.setAccessible(true);
        originalService = serviceField.get(null);
        // Keep navigation tests offline; no test writes reach the real API.
        Object pendingCall = Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class},
                (proxy, method, args) -> null);
        serviceField.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                new Class[]{DedilharteApiService.class}, (proxy, method, args) -> pendingCall));
    }

    @After
    public void tearDown() throws Exception {
        serviceField.set(null, originalService);
        new ProgressStore(targetContext, "debug-user").deleteUserData("debug-user");
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
    public void progressScreenHasOnlyRemoteTriviaShortcutsAndNoMojibake() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.root);
                String visible = collectText(root);
                assertTrue(visible.contains("Seu progresso"));
                assertTrue(visible.contains("TRIVIA INICIANTE"));
                assertTrue(visible.contains("TRIVIA INTERMEDIÁRIO"));
                assertFalse(visible.contains("VER AULAS"));
                assertFalse(visible.contains("RESETAR PROGRESSO"));
                assertFalse(visible.contains("\u00c3"));
                assertFalse(visible.contains("\u00c2"));
                assertFalse(visible.contains("\u00e2"));
                assertFalse(visible.contains("\ufffd"));
                assertTrue(click(root, "TRIVIA INICIANTE"));
                assertTrue(collectText(root).contains("Carregando conteúdo"));
                assertFalse(collectText(root).contains("Conhecendo as cordas"));
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertTrue(collectText(root).contains("TRIVIA INTERMEDIÁRIO"));
            });
        }
    }

    @Test
    public void recreationDoesNotRestoreLocalLessonsOrEraseSavedProgress() {
        ProgressStore progress = new ProgressStore(targetContext, "debug-user");
        progress.setCompleted("ini_1", true);
        progress.setCompleted("int_1", true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Simulate the screen saved by the previous version of the app.
                try {
                    java.lang.reflect.Method show = MainActivity.class.getDeclaredMethod(
                            "showLessonDetail", com.example.dedilharte.model.Lesson.class);
                    show.setAccessible(true);
                    show.invoke(activity, com.example.dedilharte.data.LessonRepository.getById("ini_1"));
                } catch (Exception error) { throw new AssertionError(error); }
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                String visible = collectText(activity.findViewById(R.id.root));
                assertTrue(visible.contains("Seu progresso"));
                assertTrue(visible.contains("TRIVIA INICIANTE"));
                assertFalse(visible.contains("Conhecendo as cordas"));
                assertTrue(progress.isCompleted("ini_1"));
                assertTrue(progress.isCompleted("int_1"));
                assertTrue("ini_1".equals(progress.lastLessonId()));
            });
        }
    }
    private boolean click(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) {
            return view.performClick();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (click(group.getChildAt(i), label)) return true;
            }
        }
        return false;
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
