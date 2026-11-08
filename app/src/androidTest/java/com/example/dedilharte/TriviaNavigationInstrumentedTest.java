package com.example.dedilharte;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.data.ProgressStore;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TriviaNavigationInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    private int moduleRequests;
    private boolean offline;
    private final LearningModuleListResponse modules = new LearningModuleListResponse();

    @Test public void shortcutsTrackSwitchAndBackUseTheSameServerModules() throws Exception {
        installApi();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertProgress(activity);
                assertEquals(0, moduleRequests);
                tap(activity, "TRIVIA INICIANTE");
                assertEquals(1, moduleRequests);
                assertTrue(text(root(activity)).contains("Exercício remoto iniciante"));
                assertTrue(text(root(activity)).contains("1 de 1 aulas concluídas"));
                tap(activity, "INTERMEDIÁRIO");
                assertEquals(2, moduleRequests);
                assertTrue(text(root(activity)).contains("Exercício remoto intermediário"));
                assertFalse(text(root(activity)).contains("Exercício remoto iniciante"));
                tap(activity, "Abrir aula: Exercício remoto intermediário");
                assertTrue(text(root(activity)).contains("Conteúdo da aula"));
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertEquals(3, moduleRequests);
                assertTrue(text(root(activity)).contains("Exercício remoto intermediário"));
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertProgress(activity);
                tap(activity, "TRIVIA INTERMEDIÁRIO");
                assertEquals(4, moduleRequests);
                assertTrue(text(root(activity)).contains("Exercício remoto intermediário"));
                assertNoLocalLessons(activity);
            });
        }
    }

    @Test public void offlineMissingAndLegacyRoutesNeverOpenLocalLessons() throws Exception {
        installApi();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                offline = true;
                tap(activity, "TRIVIA INICIANTE");
                assertTrue(text(root(activity)).contains("Verifique sua conexão"));
                assertNoLocalLessons(activity);
                tap(activity, "TENTAR NOVAMENTE");
                assertEquals(2, moduleRequests);
                offline = false;
                modules.items = Collections.emptyList();
                tap(activity, "TENTAR NOVAMENTE");
                assertTrue(text(root(activity)).contains("Nenhum conteúdo cadastrado"));
                assertNoLocalLessons(activity);
                invoke(activity, "showCourse", "Intermediário");
                assertEquals(4, moduleRequests);
                assertTrue(text(root(activity)).contains("Trivia Intermediário"));
                assertTrue(text(root(activity)).contains("Nenhum conteúdo cadastrado"));
                invoke(activity, "showLevelChoice");
                assertProgress(activity);
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                ProgressStore progress = new ProgressStore(context, "debug-user");
                progress.rememberLesson(null, "ini_1");
                invoke(activity, "showHome");
                assertTrue(text(root(activity)).contains("Escolha uma Trivia"));
                assertFalse(text(root(activity)).contains("CONTINUAR LIÇÃO"));
                assertEquals("ini_1", progress.lastLessonId());
                assertNoLocalLessons(activity);
            });
        }
    }

    @Test public void drawerHasOnlyTheTwoRemoteTriviaEntries() throws Exception {
        installApi();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> tap(activity, "Abrir menu"));
            onView(withId(R.id.drawerHome)).inRoot(isDialog()).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.drawerCourse)).inRoot(isDialog()).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.drawerDynamicModules)).inRoot(isDialog()).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.drawerTriviaBeginner)).inRoot(isDialog()).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                assertTrue(text(root(activity)).contains("Exercício remoto iniciante"));
                tap(activity, "Abrir menu");
            });
            onView(withId(R.id.drawerTriviaIntermediate)).inRoot(isDialog()).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                assertTrue(text(root(activity)).contains("Exercício remoto intermediário"));
                assertEquals(2, moduleRequests);
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertProgress(activity);
            });
        }
    }

    @Test public void adminStartsAtPanelAndStudentPreviewStartsAtProgress() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new SessionManager(context).saveSession("debug-token", "debug-user", "Admin Teste",
                "admin@example.com", "admin");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(text(root(activity)).contains("Painel administrativo"));
                tap(activity, "Visualizar app como aluno");
                assertProgress(activity);
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertTrue(text(root(activity)).contains("Painel administrativo"));
            });
        }
    }

    @Test public void studentBackFromOtherScreensReturnsToProgressAndRootBackExits() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                for (String screen : Arrays.asList("showSettings", "showSongs", "showArpeggios")) {
                    invoke(activity, screen);
                    tap(activity, "Voltar");
                    assertProgress(activity);
                    invoke(activity, screen);
                    activity.getOnBackPressedDispatcher().onBackPressed();
                    assertProgress(activity);
                }
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertTrue(activity.isFinishing());
            });
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void installApi() throws Exception {
        modules.items = Arrays.asList(module("module_trivia_beginner", "iniciante"),
                module("module_trivia_intermediate", "intermediário"));
        ModuleProgressListResponse progress = new ModuleProgressListResponse();
        ModuleProgressListResponse.Item record = new ModuleProgressListResponse.Item();
        record.itemId = "module_trivia_beginner_item";
        record.completed = true;
        progress.items = Collections.singletonList(record);
        Field service = DedilharteApiClient.class.getDeclaredField("service");
        service.setAccessible(true);
        Object previous = service.get(null);
        service.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                new Class[]{DedilharteApiService.class}, (proxy, method, args) -> {
                    boolean catalog = method.getName().equals("getModules");
                    if (!catalog && !method.getName().equals("getModuleProgress")) return method.invoke(previous, args);
                    return Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class},
                            (call, operation, values) -> {
                                if (operation.getName().equals("enqueue")) {
                                    if (catalog) moduleRequests++;
                                    Callback callback = (Callback) values[0];
                                    if (offline) callback.onFailure((Call) call, new java.io.IOException("offline"));
                                    else callback.onResponse((Call) call, Response.success(catalog ? modules : progress));
                                }
                                return null;
                            });
                }));
    }

    private static LearningModuleResponse module(String id, String level) {
        LearningModuleResponse module = new LearningModuleResponse();
        module.id = id;
        module.name = "Trivia " + level;
        LearningModuleItemResponse item = new LearningModuleItemResponse();
        item.id = id + "_item";
        item.moduleId = id;
        item.title = "Exercício remoto " + level;
        item.itemType = "multiple_choice";
        item.multipleChoice = new MultipleChoiceResponse();
        item.multipleChoice.question = "Pergunta do servidor";
        item.multipleChoice.options = Arrays.asList("A", "B");
        module.items.add(item);
        return module;
    }

    private static void assertProgress(MainActivity activity) {
        String visible = text(root(activity));
        assertTrue(visible.contains("Seu progresso"));
        assertTrue(visible.contains("TRIVIA INICIANTE"));
        assertTrue(visible.contains("TRIVIA INTERMEDIÁRIO"));
        assertNoLocalLessons(activity);
    }

    private static void assertNoLocalLessons(MainActivity activity) {
        String visible = text(root(activity));
        assertFalse(visible.contains("Conhecendo as cordas"));
        assertFalse(visible.contains("Revisão do modo treino"));
        assertFalse(visible.contains("qual é o seu nível atual?"));
        assertFalse(visible.contains("VER AULAS"));
    }

    private static View root(MainActivity activity) { return activity.findViewById(R.id.root); }
    private static void invoke(MainActivity activity, String name, String... args) {
        try {
            Method method = args.length == 0 ? MainActivity.class.getDeclaredMethod(name)
                    : MainActivity.class.getDeclaredMethod(name, String.class);
            method.setAccessible(true);
            method.invoke(activity, (Object[]) args);
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static String text(View view) {
        String result = view instanceof TextView ? ((TextView) view).getText().toString() : "";
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result += "\n" + text(group.getChildAt(i));
        }
        return result;
    }
    private static void tap(MainActivity activity, String label) {
        assertTrue("Missing button: " + label, tap(root(activity), label));
    }
    private static boolean tap(View view, String label) {
        if ((view instanceof TextView && label.contentEquals(((TextView) view).getText()))
                || label.equals(view.getContentDescription())) return view.performClick();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (tap(group.getChildAt(i), label)) return true;
        }
        return false;
    }
}
