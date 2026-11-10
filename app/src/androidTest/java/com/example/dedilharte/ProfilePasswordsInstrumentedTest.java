package com.example.dedilharte;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.network.*;
import com.example.dedilharte.network.model.*;
import com.example.dedilharte.view.PreferencesFields;
import com.example.dedilharte.view.ProgressDashboardView;
import java.lang.reflect.*;
import org.junit.Before;
import org.junit.Test;
import retrofit2.*;

/** Runs in an isolated application id; all HTTP calls are intercepted. */
public class ProfilePasswordsInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    private Context context;
    private SessionManager session;
    private RegisterRequest registration;
    private UserRequest profile;
    private PasswordRequest password;
    private boolean failProfile;
    private int passwordStatus = 200;
    private int backgroundProfileWrites;
    @Before public void preferencesApi() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext(); session = new SessionManager(context);
        Field field = DedilharteApiClient.class.getDeclaredField("service"); field.setAccessible(true); Object pending = field.get(null);
        field.set(null, Proxy.newProxyInstance(DedilharteApiService.class.getClassLoader(), new Class[]{DedilharteApiService.class}, (proxy, method, args) -> {
            Object response;
            switch (method.getName()) {
                case "register": {
                    registration = (RegisterRequest) args[0]; AuthResponse auth = new AuthResponse(); auth.token = session.getToken();
                    auth.user = new AuthenticatedUser(); auth.user.id = "debug-user"; auth.user.name = registration.name; auth.user.email = registration.email; auth.user.role = "student";
                    auth.user.weeklyGoal = registration.weeklyGoal; auth.user.songGoal = registration.songGoal;
                    auth.user.initialLevel = registration.initialLevel; auth.user.notificationsEnabled = registration.notificationsEnabled; response = auth; break;
                }
                case "updateProfile": {
                    profile = (UserRequest) args[0]; UserResponse user = new UserResponse(); user.id = session.getUserId(); user.name = profile.name; user.role = "student";
                    user.weeklyGoal = profile.weeklyGoal; user.songGoal = profile.songGoal; user.initialLevel = profile.initialLevel;
                    user.notificationsEnabled = profile.notificationsEnabled; user.updatedAtMillis = System.currentTimeMillis(); response = user; break;
                }
                case "changePassword": password = (PasswordRequest) args[0]; response = null; break;
                default: if (method.getName().equals("upsertUser")) backgroundProfileWrites++; return method.invoke(pending, args);
            }
            Object body = response; boolean failed = failProfile && method.getName().equals("updateProfile");
            return Proxy.newProxyInstance(Call.class.getClassLoader(), new Class[]{Call.class}, (call, action, params) -> {
                if (action.getName().equals("enqueue")) { Callback<Object> callback = (Callback<Object>) params[0];
                    if (failed) callback.onFailure((Call<Object>) call, new java.io.IOException("offline fixture"));
                    else if (method.getName().equals("changePassword") && passwordStatus != 200)
                        callback.onResponse((Call<Object>) call, Response.error(passwordStatus, okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{\"error\":\"Token de autenticacao ausente.\"}")));
                    else callback.onResponse((Call<Object>) call, Response.success(body)); }
                if (action.getName().equals("isCanceled")) return false; return null;
            });
        }));
    }
    @Test public void registrationHasTwoStepsAndPersistsPreferences() {
        try (ActivityScenario<RegisterActivity> scenario = ActivityScenario.launch(RegisterActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.registerName)).setText("Aluno Teste");
                ((EditText) activity.findViewById(R.id.registerEmail)).setText("test@example.com");
                ((EditText) activity.findViewById(R.id.registerPassword)).setText("Password123");
                ((EditText) activity.findViewById(R.id.registerConfirmPassword)).setText("Password123");
                activity.findViewById(R.id.registerButton).performClick();
                assertNull(registration); assertEquals(View.VISIBLE, activity.findViewById(R.id.registerPreferences).getVisibility());
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertEquals(View.VISIBLE, activity.findViewById(R.id.registerName).getVisibility());
                activity.findViewById(R.id.registerButton).performClick();
                Button button = activity.findViewById(R.id.registerButton); assertEquals("CRIAR CONTA", button.getText().toString());
                assertEquals(0xff54d6dd, button.getBackgroundTintList().getDefaultColor()); assertEquals(0xff1b4f4c, button.getCurrentTextColor());
                ((Spinner) activity.findViewById(R.id.preferenceLevel)).setSelection(1);
                ((Spinner) activity.findViewById(R.id.preferenceWeeklyGoal)).setSelection(6);
                ((EditText) activity.findViewById(R.id.preferenceSongGoal)).setText("12");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.registerPreferences).getVisibility());
                assertEquals("12", ((EditText)activity.findViewById(R.id.preferenceSongGoal)).getText().toString());
                activity.findViewById(R.id.registerButton).performClick();
                assertNotNull(registration); assertEquals("intermediate", registration.initialLevel); assertEquals(7, registration.weeklyGoal);
                assertEquals(12, session.getSongGoal()); assertEquals("intermediate", session.getInitialLevel());
            });
        }
    }
    @Test public void profileKeepsLastStateOnErrorAndRefreshesAfterConfirmation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> invoke(activity, "showEditProfile"));
            onView(withId(R.id.editName)).perform(scrollTo(), replaceText("Nome Editado"), closeSoftKeyboard());
            onView(withId(R.id.preferenceSongGoal)).perform(scrollTo(), replaceText("4"), closeSoftKeyboard());
            failProfile = true; onView(withId(R.id.editSave)).perform(scrollTo(), click());
            assertNotEquals("Nome Editado", session.getName()); assertEquals(10, session.getSongGoal());
            failProfile = false; onView(withId(R.id.editSave)).perform(scrollTo(), click());
            assertEquals("Nome Editado", session.getName()); assertEquals(4, session.getSongGoal());
            scenario.onActivity(activity -> {
                String text = text(activity.findViewById(R.id.root)); assertTrue(text.contains("0 de 4 músicas"));
                assertTrue(text.contains("Nível calculado:")); assertTrue(text.contains("Nível inicial declarado:"));
            });
            scenario.recreate(); scenario.onActivity(activity -> assertTrue(text(activity.findViewById(R.id.root)).contains("0 de 4 músicas")));
        }
    }
    @Test public void profileIsScreenAndKeepsDraftAcrossRecreationWithoutChangingRole() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                invoke(activity, "showEditProfile");
                assertNotNull(activity.findViewById(R.id.editName));
                ((EditText) activity.findViewById(R.id.editName)).setText("Administrador");
                ((EditText) activity.findViewById(R.id.preferenceSongGoal)).setText("22");
                ((Spinner) activity.findViewById(R.id.preferenceLevel)).setSelection(1);
                activity.findViewById(R.id.editGoalPlus).performClick();
                assertNull(((TextView) activity.findViewById(R.id.editSave)).getCompoundDrawablesRelative()[0]);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("Administrador", ((EditText)activity.findViewById(R.id.editName)).getText().toString());
                assertEquals("22", ((EditText)activity.findViewById(R.id.preferenceSongGoal)).getText().toString());
                assertEquals(1, ((Spinner)activity.findViewById(R.id.preferenceLevel)).getSelectedItemPosition());
                assertEquals("4 dias por semana", ((TextView)activity.findViewById(R.id.editGoalValue)).getText().toString());
                activity.findViewById(R.id.editSave).performClick();
                assertEquals("student", session.getRole());
                assertEquals("Administrador", session.getName());
                assertEquals("intermediate", session.getInitialLevel());
            });
        }
    }
    @Test public void settingsHasOneTitleAndLogoutClearsSession() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                invoke(activity, "showSettings");
                String content = text(activity.findViewById(R.id.root));
                assertEquals(content.indexOf("Configurações"), content.lastIndexOf("Configurações"));
                assertNotNull(activity.findViewById(R.id.settingsDarkMode));
            });
            onView(withText("Sair da conta")).perform(scrollTo(), click());
            onView(withText("Sair")).inRoot(isDialog()).perform(click());
            assertFalse(session.isLoggedIn());
        }
    }
    @Test public void profileKeyboardAndSettingsThemesRespectSystemBars() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> new com.example.dedilharte.data.ThemeStore(context).setDarkModeEnabled(false));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> invoke(activity, "showSettings"));
            capture("settings-light.png");
            onView(withId(R.id.settingsDarkMode)).perform(click());
            onView(withId(R.id.settingsDarkMode)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isChecked()));
            capture("settings-dark.png");
            onView(withText("Editar perfil")).perform(click());
            capture("edit-profile.png");
            onView(withId(R.id.editName)).perform(scrollTo(), click());
            scenario.onActivity(activity -> androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                    .show(androidx.core.view.WindowInsetsCompat.Type.ime()));
            final boolean[] shown = {false};
            for (int i = 0; i < 40 && !shown[0]; i++) {
                Thread.sleep(200);
                scenario.onActivity(activity -> {
                    androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.findViewById(R.id.editName));
                    shown[0] = insets != null && insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());
                });
            }
            scenario.onActivity(activity -> {
                View input = activity.findViewById(R.id.editName);
                androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(input);
                assertNotNull(insets); assertTrue(shown[0]);
                android.graphics.Rect rect = new android.graphics.Rect(); assertTrue(input.getGlobalVisibleRect(rect));
                assertTrue(rect.top >= insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top);
                assertTrue(rect.bottom <= activity.getWindow().getDecorView().getHeight() - insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom);
                assertTrue("Status bar visible", insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()));
                assertTrue("Navigation bar visible", insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars()));
            });
            capture("edit-profile-keyboard.png");
            onView(withId(R.id.editName)).perform(closeSoftKeyboard());
            onView(withId(R.id.editPassword)).perform(scrollTo(), click());
            onView(withHint("Senha atual")).inRoot(isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            onView(withText("Cancelar")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.editSave)).perform(scrollTo());
            capture("edit-profile-actions.png");
            onView(withId(R.id.editSave)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isCompletelyDisplayed()));
            scenario.onActivity(activity -> activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            final boolean[] landscape = {false};
            for (int i = 0; i < 40 && !landscape[0]; i++) {
                Thread.sleep(200);
                scenario.onActivity(activity -> landscape[0] = activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
            }
            assertTrue("Landscape applied", landscape[0]);
            onView(withId(R.id.editSave)).perform(scrollTo()).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            capture("edit-profile-landscape.png");
            failProfile = true;
            onView(withId(R.id.editSave)).perform(click());
            assertNotNull(profile);
            scenario.onActivity(activity -> activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        }
    }
    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // UiAutomation can otherwise capture the compositor's frame before the scroll/rotation.
        Thread.sleep(350);
        android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null), name))) {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        } finally { image.recycle(); }
    }
    @Test public void profileUsesServerRevisionDespiteDeviceClockAndNeverUploadsOnOpening() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(0, backgroundProfileWrites);
                UserResponse remote = new UserResponse(); remote.id = session.getUserId(); remote.name = "Outro dispositivo";
                remote.initialLevel = "intermediate"; remote.songGoal = 8; remote.weeklyGoal = 5; remote.notificationsEnabled = true;
                remote.updatedAtMillis = System.currentTimeMillis() - 3600000;
                try {
                    Method apply = MainActivity.class.getDeclaredMethod("applyRemoteUserIfNewer", UserResponse.class); apply.setAccessible(true); apply.invoke(activity, remote);
                    assertEquals(8, session.getSongGoal()); assertEquals("Outro dispositivo", session.getName());
                    remote.updatedAtMillis--; remote.songGoal = 2; apply.invoke(activity, remote); assertEquals(8, session.getSongGoal());
                    remote.id = "another-user"; remote.updatedAtMillis += 10000; apply.invoke(activity, remote); assertEquals(8, session.getSongGoal());
                } catch (Exception e) { throw new AssertionError(e); }
            });
        }
    }
    @Test public void changePasswordDialogCallsAuthenticatedEndpoint() {
        session.clearSession();
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> com.example.dedilharte.view.PasswordDialogs.change(activity, DedilharteApiClient.service(), () -> session.clearSession()));
            onView(withHint("Senha atual")).inRoot(isDialog()).perform(replaceText("Current123"), closeSoftKeyboard());
            onView(withHint("Nova senha")).inRoot(isDialog()).perform(replaceText("NextPassword123"), closeSoftKeyboard());
            onView(withHint("Confirmar nova senha")).inRoot(isDialog()).perform(replaceText("NextPassword123"), closeSoftKeyboard());
            onView(withText("Salvar senha")).inRoot(isDialog()).perform(click());
            assertEquals("Current123", password.currentPassword); assertEquals("NextPassword123", password.newPassword); assertFalse(session.isLoggedIn());
        }
    }
    @Test public void insetsRespectBarsCutoutAndKeyboardWithoutAccumulatingPadding() {
        session.clearSession();
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                View root = ((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
                androidx.core.view.WindowInsetsCompat insets = new androidx.core.view.WindowInsetsCompat.Builder()
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), androidx.core.graphics.Insets.of(0, 30, 0, 24))
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout(), androidx.core.graphics.Insets.of(12, 0, 0, 0))
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, 300)).build();
                androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets); androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets);
                assertEquals(30, root.getPaddingTop()); assertEquals(300, root.getPaddingBottom()); assertEquals(12, root.getPaddingLeft());
            });
        }
    }
    @Test public void registrationKeyboardLeavesFocusedFieldAboveIme() throws Exception {
        try (ActivityScenario<RegisterActivity> scenario = ActivityScenario.launch(RegisterActivity.class)) {
            onView(withId(R.id.registerName)).perform(scrollTo(), click());
            scenario.onActivity(activity -> androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                    .show(androidx.core.view.WindowInsetsCompat.Type.ime()));
            final boolean[] keyboardVisible = {false};
            for (int i = 0; i < 40 && !keyboardVisible[0]; i++) {
                Thread.sleep(200);
                scenario.onActivity(activity -> {
                    androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.findViewById(R.id.registerName));
                    keyboardVisible[0] = insets != null && insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());
                });
            }
            scenario.onActivity(activity -> {
                EditText input = activity.findViewById(R.id.registerName);
                androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(input);
                assertNotNull(insets); assertTrue("Keyboard must actually be visible", insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));
                android.graphics.Rect visible = new android.graphics.Rect(); assertTrue(input.getGlobalVisibleRect(visible));
                int keyboard = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
                assertTrue(visible.bottom <= activity.getWindow().getDecorView().getHeight() - keyboard);
                assertTrue(visible.top >= insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top);
            });
            android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null), "register-keyboard.png"))) { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); }
            image.recycle(); onView(withId(R.id.registerName)).perform(closeSoftKeyboard());
        }
    }
    @Test public void notificationPermissionDenialPreservesDeclaredPreference() throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        assertTrue("Only the isolated test package may change permissions", context.getPackageName().endsWith(".navigationtest"));
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        android.accessibilityservice.AccessibilityServiceInfo info = automation.getServiceInfo();
        info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        automation.setServiceInfo(info);
        session.clearSession(); session.updatePreferences("beginner", 10, true);
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                scenario.onActivity(activity -> com.example.dedilharte.auth.NotificationPermissions.requestIfNeeded(activity));
                android.view.accessibility.AccessibilityNodeInfo deny = null;
                for (int i = 0; i < 60 && deny == null; i++) {
                    Thread.sleep(250);
                    android.view.accessibility.AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
                    if (root != null) {
                        java.util.List<android.view.accessibility.AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button");
                        if (matches.isEmpty()) matches = root.findAccessibilityNodeInfosByText("Don't allow");
                        if (matches.isEmpty()) matches = root.findAccessibilityNodeInfosByText("Não permitir");
                        if (!matches.isEmpty()) deny = matches.get(0);
                    }
                }
                assertNotNull("Notification permission prompt", deny); assertTrue(deny.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
                Thread.sleep(300); assertNotEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS));
            }
            assertTrue(session.notificationsEnabled());
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(), android.Manifest.permission.POST_NOTIFICATIONS);
            scenario.onActivity(activity -> com.example.dedilharte.auth.NotificationPermissions.requestIfNeeded(activity));
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS));
        }
    }
    @Test public void personalSongPercentageIsCappedWithoutChangingTriviaOrDeclaredLevel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ProgressDashboardView view = new ProgressDashboardView(context, "Aluno", "Iniciante", 3, 4, "intermediate", () -> {}, () -> {}, () -> {});
            com.example.dedilharte.data.DashboardMetrics data = new com.example.dedilharte.data.DashboardMetrics();
            data.partial = true; data.songsAvailable = true; data.songs.total = 10; data.songs.completed = 2;
            view.render(data, false, ""); assertTrue(text(view).contains("2 de 4 músicas")); assertTrue(text(view).contains("50%"));
            assertTrue(text(view).contains("Nível calculado: Iniciante")); assertTrue(text(view).contains("Nível inicial declarado: Intermediário"));
            data.songs.completed = 6; view.render(data, false, "");
            assertTrue(text(view).contains("6 de 4 músicas")); assertTrue(text(view).contains("100%"));
        });
    }
    private static void invoke(MainActivity activity, String name) { try { Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity); } catch (Exception e) { throw new AssertionError(e); } }
    private static String text(View view) { String value = view instanceof TextView ? ((TextView)view).getText().toString() + "\n" : ""; if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) value += text(((ViewGroup)view).getChildAt(i)); return value; }
}
