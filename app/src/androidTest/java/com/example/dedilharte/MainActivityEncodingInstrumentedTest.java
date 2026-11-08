package com.example.dedilharte;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.dedilharte.data.LessonRepository;
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
    public void courseScreenDoesNotShowMojibake() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.root);
                assertTrue(collectText(root).contains("Continuar de onde parou"));
                assertTrue(click(root, "VER AULAS"));
                assertTrue(collectText(root).contains("qual é o seu nível atual?"));
                assertTrue(click(root, "INICIANTE"));
                String visibleText = collectText(activity.findViewById(R.id.root));

                assertTrue(visibleText.contains("Trilha Iniciante"));
                assertTrue(visibleText.contains("Conhecendo as cordas"));
                assertTrue(visibleText.contains("Desafio iniciante"));
                assertTrue(visibleText.contains("CONTINUAR: AULA 1"));
                assertFalse(visibleText.contains("\u00c3"));
                assertFalse(visibleText.contains("\u00c2"));
                assertFalse(visibleText.contains("\u00e2"));
                assertFalse(visibleText.contains("\ufffd"));
            });
        }
    }

    @Test
    public void lessonFlowRestoresTrackPracticeAndSavedProgress() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.root);
                assertTrue(click(root, "VER AULAS"));
                assertTrue(click(root, "INICIANTE"));
                assertTrue(click(root, "CONTINUAR: AULA 1"));
                assertTrue(collectText(root).contains("COMO FUNCIONA"));
                assertTrue(collectText(root).contains("Passo a passo"));
                assertTrue(collectText(root).contains("Padrão do exercício"));
                assertTrue(click(root, "MARCAR COMO CONCLUÍDA"));
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertProgress(root, LessonRepository.BEGINNER);
                assertTrue(collectText(root).contains("CONTINUAR: AULA 2"));
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertTrue(collectText(root).contains("qual é o seu nível atual?"));
                assertTrue(click(root, "INTERMEDIÁRIO"));
                assertTrue(collectText(root).contains("Trilha Intermediário"));
                assertTrue(click(root, "CONTINUAR: AULA 1"));
                assertTrue(click(root, "ABRIR MODO TREINO"));
                assertTrue(collectText(root).contains("Modo treino"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.root);
                assertTrue(collectText(root).contains("Modo treino"));
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertTrue(collectText(root).contains("Passo a passo"));
                assertTrue(click(root, "ABRIR MODO TREINO"));
                assertTrue(click(root, "MARCAR COMO CONCLUÍDA"));
                assertTrue(collectText(root).contains("AULA CONCLUÍDA ✓"));
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertTrue(collectText(root).contains("Trilha Intermediário"));
                assertProgress(root, LessonRepository.INTERMEDIATE);
                assertTrue(collectText(root).contains("CONTINUAR: AULA 2"));
                ProgressStore progress = new ProgressStore(targetContext, "debug-user");
                assertTrue(progress.isCompleted("ini_1"));
                assertTrue(progress.isCompleted("int_1"));
                assertFalse(progress.isCompleted("int_2"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.root);
                assertTrue(collectText(root).contains("Trilha Intermediário"));
                assertProgress(root, LessonRepository.INTERMEDIATE);
            });
        }
    }

    private void assertProgress(View root, String level) {
        int total = LessonRepository.getLessons(level).size();
        String visible = collectText(root);
        assertTrue(visible.contains("1 de " + total + " aulas concluídas"));
        assertTrue(visible.contains(Math.round(100f / total) + "%"));
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
