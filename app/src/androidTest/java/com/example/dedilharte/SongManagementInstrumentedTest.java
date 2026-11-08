package com.example.dedilharte;

import static org.junit.Assert.*;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.SongListResponse;
import com.example.dedilharte.network.model.SongRequest;
import com.example.dedilharte.network.model.SongResponse;
import com.google.gson.Gson;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongManagementInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void adminCrudRefreshesRepertoireAndDeletionRequiresConfirmation() throws Exception {
        Field service = DedilharteApiClient.class.getDeclaredField("service");
        service.setAccessible(true);
        Object original = service.get(null);
        SongResponse[] stored = {null};
        int[] deleted = {0};
        int[] adminReads = {0};
        Gson gson = new Gson();
        Object fake = Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(),
                new Class[]{DedilharteApiService.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (!name.equals("getAdminSongs") && !name.equals("getSongs") && !name.equals("createSong")
                            && !name.equals("updateSong") && !name.equals("deleteSong")) return method.invoke(original, args);
                    return Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (call, operation, values) -> {
                        if (!operation.getName().equals("enqueue")) return null;
                        Object body;
                        if (name.equals("createSong") || name.equals("updateSong")) {
                            SongRequest request = (SongRequest) args[name.equals("createSong") ? 0 : 1];
                            String json = gson.toJson(request);
                            assertFalse(json.contains("recommendedBpm"));
                            assertFalse(json.contains("queueOrder"));
                            assertFalse(json.contains("status"));
                            assertFalse(json.contains("content")); // Preserve content not exposed by this editor.
                            stored[0] = gson.fromJson(json, SongResponse.class);
                            stored[0].id = "server-test-id";
                            body = stored[0];
                        } else if (name.equals("deleteSong")) {
                            deleted[0]++;
                            stored[0] = null;
                            body = null;
                        } else {
                            if (name.equals("getAdminSongs")) adminReads[0]++;
                            SongListResponse list = new SongListResponse();
        list.items = new java.util.ArrayList<>();
                            if (stored[0] != null && (name.equals("getAdminSongs") || stored[0].active)) list.items.add(stored[0]);
                            body = list;
                        }
                        ((Callback) values[0]).onResponse((Call) call, Response.success(body));
                        return null;
                    });
                });
        service.set(null, fake);
        new com.example.dedilharte.auth.SessionManager(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext())
                .saveSession("test-token", "debug-user", "Test", "test@example.com", "admin");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    Field role = MainActivity.class.getDeclaredField("userRole");
                    role.setAccessible(true);
                    role.set(activity, "admin");
                    open(activity, "showAdminSongs");
                    View root = activity.findViewById(R.id.root);
                    assertNotNull(find(root, "Nenhuma musica encontrada.", false));
                    tap(root, "NOVA MÚSICA");
                    fill(root, "Título", "Canção do teste");
                    fill(root, "Artista ou autor", "Autor");
                    fill(root, "BPM recomendado", "92");
                    fill(root, "Cole a tablatura ASCII", "e|--0--|\nB|-----|\nG|-----|\nD|-----|\nA|-----|\nE|-----|");
                    tap(root, "PUBLICAR");
                    assertTrue(stored[0].active);
                    assertEquals(92, stored[0].bpm);
                    tap(root, "EDITAR");
                    fill(root, "Título", "Canção editada");
                    tap(root, "PUBLICAR");
                    assertEquals("Canção editada", stored[0].title);
                    tap(root, "ARQUIVAR");
                    assertFalse(stored[0].active);
                    open(activity, "showSongs");
                    assertNull(find(root, "Canção editada", false));
                    open(activity, "showAdminSongs");
                    tap(root, "RESTAURAR");
                    assertTrue(stored[0].active);
                    open(activity, "showSongs");
                    fill(root, "Pesquise uma prática", "inexistente");
                    assertNull(find(root, "Canção editada", false));
                    fill(root, "Pesquise uma prática", "editada");
                    tap(root, "Canção editada");
                    assertNotNull(find(root, "Tocar música", false));
                    open(activity, "showAdminSongs");
                    tap(root, "EXCLUIR");
                    assertEquals(0, deleted[0]);
                } catch (Exception e) { throw new AssertionError(e); }
            });
            scenario.onActivity(activity -> clickDialogButton(android.R.id.button2));
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(0, deleted[0]);
            scenario.onActivity(activity -> tap(activity.findViewById(R.id.root), "EXCLUIR"));
            scenario.onActivity(activity -> clickDialogButton(android.R.id.button1));
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(1, deleted[0]);
            assertTrue(adminReads[0] >= 7);
            scenario.onActivity(activity -> {
                assertNotNull(find(activity.findViewById(R.id.root), "Nenhuma musica encontrada.", false));
                try {
                    open(activity, "showSongs");
                    assertNull(find(activity.findViewById(R.id.root), "Canção editada", false));
                } catch (Exception e) { throw new AssertionError(e); }
            });
        } finally { service.set(null, original); }
    }

    private static void open(MainActivity activity, String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(activity);
    }

    @androidx.annotation.RequiresApi(29)
    private static void clickDialogButton(int id) {
        // Inspect the actual dialog even when an emulator system window owns input focus.
        for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
            View button = window.findViewById(id);
            if (button != null && button.isShown()) {
                assertTrue(button.performClick());
                return;
            }
        }
        fail("Confirmation dialog button was not shown");
    }

    private static void fill(View root, String hint, String value) {
        TextView input = find(root, hint, true);
        assertNotNull(hint, input);
        input.setText(value);
    }

    private static void tap(View root, String label) {
        TextView view = find(root, label, false);
        assertNotNull(label, view);
        assertTrue(view.performClick());
    }

    private static TextView find(View view, String label, boolean hint) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence value = hint ? text.getHint() : text.getText();
            if ((!hint || view instanceof EditText) && value != null && value.toString().contains(label)) return text;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = find(group.getChildAt(i), label, hint);
                if (result != null) return result;
            }
        }
        return null;
    }
}
