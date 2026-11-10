package com.example.dedilharte;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.RootMatchers.withDecorView;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.data.ThemeStore;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.view.PasswordDialogs;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

/** Uses the existing offline API/session fixture; does not submit any form. */
public class NightThemeInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    @Test public void modeSwitchUpdatesScreensAndPersists() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> new ThemeStore(context).setDarkModeEnabled(false));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(Color.WHITE, activity.getColor(R.color.theme_page));
                show(activity, "showSettings");
            });
            onView(withId(R.id.settingsDarkMode)).perform(click());
            scenario.onActivity(activity -> {
                assertTrue(ThemeStore.isNight(activity));
                assertTrue(new ThemeStore(activity).isDarkModeEnabled());
                assertEquals(Color.rgb(27, 73, 70), activity.getColor(R.color.theme_page));
                assertEquals(Color.rgb(16, 45, 43), activity.getColor(R.color.theme_header));
                for (String screen : new String[]{"showSettings", "showSongs", "showArpeggios", "showAbout", "showEditProfile"}) {
                    show(activity, screen);
                    ViewGroup root = activity.findViewById(R.id.root);
                    assertEquals(screen, activity.getColor(R.color.theme_page),
                            ((ColorDrawable) root.getChildAt(0).getBackground()).getColor());
                }
                TextView name = activity.findViewById(R.id.editName);
                assertEquals(activity.getColor(R.color.theme_ink), name.getCurrentTextColor());
            });
            screenshot(context, "night-profile");
            scenario.onActivity(activity -> PasswordDialogs.change(activity, DedilharteApiClient.service(), () -> {}));
            onView(withContentDescription("Senha atual")).inRoot(isDialog()).check((view, error) -> {
                if (error != null) throw error;
                assertEquals(Color.WHITE, ((TextView) view).getCurrentTextColor());
                assertEquals(Color.rgb(27, 73, 70), ((ColorDrawable) ((android.view.View) view.getParent()).getBackground()).getColor());
            });
            screenshot(context, "night-dialog");
            onView(withText("Cancelar")).inRoot(isDialog()).perform(click());
            scenario.onActivity(activity -> show(activity, "openDrawer"));
            onView(withId(R.id.drawerUserName)).inRoot(withDecorView(hasDescendant(withId(R.id.drawerUserName)))).check((view, error) -> {
                if (error != null) throw error;
                assertEquals(Color.WHITE, ((TextView) view).getCurrentTextColor());
            });
            screenshot(context, "night-menu");
            pressBack();
        }
        // Reset AppCompat's in-memory choice; the next Activity must read the saved setting.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> ThemeStore.apply(false));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(ThemeStore.isNight(activity));
                show(activity, "showSettings");
            });
            screenshot(context, "night-settings");
            onView(withId(R.id.settingsDarkMode)).perform(click());
            scenario.onActivity(activity -> {
                assertEquals(Color.WHITE, activity.getColor(R.color.theme_page));
                assertEquals(Color.rgb(27, 79, 76), activity.getColor(R.color.theme_header));
                assertEquals(Color.rgb(232, 246, 246), activity.getColor(R.color.theme_card));
            });
            screenshot(context, "light-settings");
            onView(withId(R.id.settingsDarkMode)).perform(click());
        }
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> assertTrue(ThemeStore.isNight(activity)));
            screenshot(context, "night-login");
        }
        try (ActivityScenario<RegisterActivity> scenario = ActivityScenario.launch(RegisterActivity.class)) {
            scenario.onActivity(activity -> assertTrue(ThemeStore.isNight(activity)));
            screenshot(context, "night-register");
        }
    }

    private static void show(MainActivity activity, String name) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(activity);
        } catch (Exception error) { throw new AssertionError(name, error); }
    }

    private static void screenshot(Context context, String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Thread.sleep(350); // Allow the compositor to present the newly themed window.
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }
}
