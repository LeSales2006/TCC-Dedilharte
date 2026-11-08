package com.example.dedilharte;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.data.ProgressStore;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    public void homeResumesExactLessonAcrossTriviasAndRestartsWithoutLocalContentFallback() throws Exception {
        Field service = DedilharteApiClient.class.getDeclaredField("service");
        service.setAccessible(true);
        Object original = service.get(null);
        LearningModuleResponse beginner = module("module_trivia_beginner", "Trivia Iniciante", "beginner-question");
        LearningModuleResponse intermediate = module("module_trivia_intermediate", "Trivia Intermediário", "intermediate-first");
        LearningModuleItemResponse last = item("intermediate-last", "Minha última lição");
        intermediate.items.add(last);
        LearningModuleListResponse modules = new LearningModuleListResponse();
        modules.items = Arrays.asList(beginner, intermediate);
        SongListResponse songs = new SongListResponse();
        songs.items = new java.util.ArrayList<>();
        SongResponse song = new SongResponse();
        song.id = "server-song";
        song.title = "Sugestão do servidor";
        song.active = true;
        song.tablature = "e|--0--|\nB|-----|\nG|-----|\nD|-----|\nA|-----|\nE|-----|";
        songs.items.add(song);
        boolean[] offline = {false};
        service.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                new Class[]{DedilharteApiService.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getModules") && !method.getName().equals("getSongs")) return method.invoke(original, args);
                    Object body = method.getName().equals("getModules") ? modules : songs;
                    return Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (call, operation, values) -> {
                        if (operation.getName().equals("enqueue")) {
                            Callback callback = (Callback) values[0];
                            if (offline[0]) callback.onFailure((Call) call, new java.io.IOException("offline"));
                            else callback.onResponse((Call) call, Response.success(body));
                        }
                        return null;
                    });
                }));
        try {
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    View root = activity.findViewById(R.id.root);
                    assertTrue(text(root).contains("Seu progresso"));
                    home(activity);
                    assertTrue(text(root).contains("Continuar de onde parou"));
                    assertTrue(text(root).contains(song.title));
                    assertFalse(text(root).contains("CONTINUAR LIÇÃO"));
                    tap(root, "TRIVIA INICIANTE");
                    tap(root, "Abrir aula: beginner-question");
                    home(activity);
                    assertTrue(text(root).contains("beginner-question"));
                    tap(root, "TRIVIA INTERMEDIÁRIO");
                    tap(root, "Abrir aula: Minha última lição");
                    home(activity);
                    assertTrue(text(root).contains(last.title));
                    assertFalse(text(root).contains("intermediate-first"));
                    tap(root, "TRIVIA INICIANTE"); // Browsing another trivia must not replace the bookmark.
                    activity.getOnBackPressedDispatcher().onBackPressed();
                    assertTrue(text(root).contains("Seu progresso"));
                    home(activity);
                    assertTrue(text(root).contains(last.title));
                    tap(root, "Praticar música: " + song.title);
                    assertTrue(text(root).contains("Tocar música"));
                    activity.getOnBackPressedDispatcher().onBackPressed();
                    assertTrue(text(root).contains("Continuar de onde parou"));
                    assertTrue(text(root).contains(last.title));
                    tap(root, "CONTINUAR LIÇÃO");
                    assertTrue(text(root).contains("Conteúdo da aula"));
                    assertTrue(text(root).contains(last.title));
                });
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    home(activity);
                    View root = activity.findViewById(R.id.root);
                    assertTrue(text(root).contains(last.title));
                    assertTrue(text(root).contains("Trivia Intermediário"));
                    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                    ProgressStore own = new ProgressStore(context, "debug-user");
                    ProgressStore other = new ProgressStore(context, "home-other-user");
                    other.deleteUserData("home-other-user");
                    assertEquals("intermediate-last", own.lastLessonId());
                    assertEquals("module_trivia_intermediate", own.lastModuleId());
                    assertEquals("", other.lastLessonId());
                    other.rememberLesson("other-module", "other-lesson");
                    assertEquals("intermediate-last", own.lastLessonId());
                    other.deleteUserData("home-other-user");
                    intermediate.items.remove(last);
                    tap(root, "ATUALIZAR");
                    assertTrue(text(root).contains("Lição indisponível"));
                    assertFalse(text(root).contains("CONTINUAR LIÇÃO"));
                    intermediate.items.add(last);
                    intermediate.active = false;
                    tap(root, "ATUALIZAR");
                    assertTrue(text(root).contains("Lição indisponível"));
                    intermediate.active = true;
                    songs.items.clear();
                    tap(root, "ATUALIZAR");
                    assertTrue(text(root).contains("Nenhuma nova música"));
                    assertFalse(text(root).contains(song.title));
                    offline[0] = true;
                    tap(root, "ATUALIZAR");
                    assertTrue(text(root).contains("Não foi possível carregar a lição"));
                    assertFalse(text(root).contains(last.title));
                    assertFalse(text(root).contains(song.title));
                });
            }
        } finally { service.set(null, original); }
    }

    private static LearningModuleResponse module(String id, String name, String lesson) {
        LearningModuleResponse module = new LearningModuleResponse();
        module.id = id;
        module.name = name;
        module.items.add(item(lesson, lesson));
        return module;
    }

    private static LearningModuleItemResponse item(String id, String title) {
        LearningModuleItemResponse item = new LearningModuleItemResponse();
        item.id = id;
        item.title = title;
        item.itemType = "multiple_choice";
        item.multipleChoice = new MultipleChoiceResponse();
        item.multipleChoice.question = "Pergunta do servidor";
        item.multipleChoice.options = Arrays.asList("A", "B");
        return item;
    }

    private static void home(MainActivity activity) {
        try {
            Method method = MainActivity.class.getDeclaredMethod("showHome");
            method.setAccessible(true);
            method.invoke(activity);
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

    private static void tap(View view, String label) {
        assertTrue("Missing button: " + label, click(view, label));
    }

    private static boolean click(View view, String label) {
        if (view instanceof TextView && (label.contentEquals(((TextView) view).getText()) || label.equals(view.getContentDescription()))) {
            return view.performClick();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (click(group.getChildAt(i), label)) return true;
        }
        return false;
    }
}
