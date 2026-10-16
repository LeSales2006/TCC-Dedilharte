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

public class TriviaContentInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    @Test
    public void reloadUsesServerItemsAndNeverRestoresLocalLessons() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    Field service = DedilharteApiClient.class.getDeclaredField("service");
                    service.setAccessible(true);
                    Object original = service.get(null);
                    LearningModuleResponse beginner = new LearningModuleResponse();
                    beginner.id = "module_trivia_beginner";
                    beginner.name = "Trivia Iniciante";
                    LearningModuleResponse intermediate = new LearningModuleResponse();
                    intermediate.id = "module_trivia_intermediate";
                    intermediate.name = "Trivia Intermediário";
                    LearningModuleListResponse body = new LearningModuleListResponse();
                    body.items = Arrays.asList(beginner, intermediate);
                    boolean[] fail = {false};
                    int[] requests = {0};
                    Object fake = Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (proxy, method, args) -> {
                        if (method.getName().equals("enqueue")) {
                            requests[0]++;
                            Callback<LearningModuleListResponse> callback = (Callback<LearningModuleListResponse>) args[0];
                            if (fail[0]) callback.onFailure((Call) proxy, new java.io.IOException("Offline"));
                            else callback.onResponse((Call) proxy, Response.success(body));
                        }
                        return null;
                    });
                    service.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                            new Class[]{DedilharteApiService.class}, (proxy, method, args) ->
                                    method.getName().equals("getModules") ? fake : method.invoke(original, args)));
                    try {
                        Method open = MainActivity.class.getDeclaredMethod("showCourse", String.class);
                        open.setAccessible(true);
                        View root = activity.findViewById(R.id.root);
                        for (LearningModuleResponse module : body.items) {
                            open.invoke(activity, module == beginner ? "Iniciante" : "Intermediário");
                            assertTrue(text(root).contains("Nenhum item ativo"));
                            assertFalse(text(root).contains("Conhecendo as cordas"));
                            LearningModuleItemResponse item = new LearningModuleItemResponse();
                            item.id = "question";
                            item.moduleId = module.id;
                            item.title = "Pergunta do servidor";
                            item.itemType = "multiple_choice";
                            module.items.add(item);
                            click(root, "ATUALIZAR");
                            assertTrue(text(root).contains(item.title));
                            item.title = "Pergunta editada";
                            click(root, "ATUALIZAR");
                            assertTrue(text(root).contains(item.title));
                            assertFalse(text(root).contains("Pergunta do servidor"));
                            module.items.clear();
                            click(root, "ATUALIZAR");
                            assertTrue(text(root).contains("Nenhum item ativo"));
                            assertFalse(text(root).contains(item.title));
                        }
                        fail[0] = true;
                        click(root, "ATUALIZAR");
                        assertTrue(text(root).contains("Não foi possível carregar"));
                        assertFalse(text(root).contains("Nenhum item ativo"));
                        assertFalse(text(root).contains("Conhecendo as cordas"));
                        assertEquals(9, requests[0]);
                    } finally { service.set(null, original); }
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
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) {
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
