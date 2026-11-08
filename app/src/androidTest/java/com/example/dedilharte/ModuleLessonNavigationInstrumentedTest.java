package com.example.dedilharte;

import static org.junit.Assert.*;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
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

public class ModuleLessonNavigationInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    @Test
    public void songLessonOpensPreviewStartsPracticeAndReturnsToPreview() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    LearningModuleResponse module = new LearningModuleResponse();
                    module.id = "module_trivia_beginner";
                    module.name = "Trivia Iniciante";
                    LearningModuleItemResponse previous = new LearningModuleItemResponse();
                    previous.id = "completed_item";
                    previous.title = "Aula concluída do banco";
                    module.items.add(previous);
                    LearningModuleItemResponse item = new LearningModuleItemResponse();
                    item.id = "server_item";
                    item.sortOrder = 1;
                    item.moduleId = module.id;
                    item.title = "Música Teste";
                    item.instructions = "Toque acompanhando a tablatura.";
                    item.itemType = "song";
                    item.songId = "server_song";
                    item.song = new SongResponse();
                    item.song.id = item.songId;
                    item.song.title = "Conteúdo do banco";
                    item.song.artist = "Autor";
                    item.song.active = true;
                    item.song.tablature = "E|--0--|\nB|-----|\nG|-----|\nD|-----|\nA|-----|\nE|-----|";
                    module.items.add(item);
                    Method show = MainActivity.class.getDeclaredMethod("showDynamicModule", LearningModuleResponse.class);
                    show.setAccessible(true);
                    Field service = DedilharteApiClient.class.getDeclaredField("service");
                    service.setAccessible(true);
                    Object original = service.get(null);
                    ModuleProgressListResponse progress = new ModuleProgressListResponse();
                    ModuleProgressListResponse.Item record = new ModuleProgressListResponse.Item();
                    record.itemId = previous.id;
                    record.completed = true;
                    progress.items = Arrays.asList(record);
                    Object progressCall = Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("enqueue")) {
                                    ((Callback<ModuleProgressListResponse>) args[0]).onResponse((Call) proxy, Response.success(progress));
                                }
                                return null;
                            });
                    try {
                        service.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                                new Class[]{DedilharteApiService.class}, (proxy, method, args) ->
                                        method.getName().equals("getModuleProgress") ? progressCall : method.invoke(original, args)));
                        show.invoke(activity, module);
                    } finally {
                        service.set(null, original);
                    }
                    View root = activity.findViewById(R.id.root);
                    assertFalse(text(root).contains("MARCAR COMO APRENDIDA"));
                    assertTrue(text(root).contains("Trilha Iniciante"));
                    assertTrue(text(root).contains("1ª AULA"));
                    assertTrue(text(root).contains("1 de 2 aulas concluídas"));
                    assertTrue(text(root).contains("50%"));
                    assertTrue(text(root).contains("CONTINUAR: AULA 2"));
                    assertFalse(text(root).contains("VER CONTEÚDO DA AULA"));
                    assertTrue(click(root, "Abrir aula: Música Teste"));
                    assertTrue(text(root).contains(item.instructions));
                    assertTrue(text(root).contains(item.song.tablature));
                    assertTrue(click(root, "INICIAR AULA"));
                    assertTrue(text(root).contains("Tocar música"));
                    assertTrue(text(root).contains("CONCLUIR AULA"));
                    activity.getOnBackPressedDispatcher().onBackPressed();
                    assertTrue(text(root).contains("Conteúdo da aula"));
                    assertTrue(text(root).contains(item.song.tablature));
                } catch (Exception e) { throw new AssertionError(e); }
            });
        }
    }

    private static String text(View view) {
        String result = view instanceof TextView ? ((TextView) view).getText().toString() : "";
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result += "\n" + text(group.getChildAt(i));
        }
        return result;
    }

    private static boolean click(View view, String label) {
        if (view instanceof TextView && (label.contentEquals(((TextView) view).getText())
                || label.equals(view.getContentDescription()))) {
            view.performClick();
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (click(group.getChildAt(i), label)) return true;
        }
        return false;
    }
}
