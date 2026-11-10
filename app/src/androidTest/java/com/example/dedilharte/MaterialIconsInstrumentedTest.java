package com.example.dedilharte;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.ContextThemeWrapper;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.data.ThemeStore;
import com.example.dedilharte.view.MaterialIcons;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import org.junit.Test;

/** Uses the parent's fake API and isolated test account; never writes to production. */
public class MaterialIconsInstrumentedTest extends DashboardInstrumentedTest {
    @Test public void localVectorsKeepSizeTintAndContrastInBothThemes() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
            Configuration configuration = new Configuration(target.getResources().getConfiguration());
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
            Context themed = new ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_Dedilharte);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                android.widget.TextView button = new android.widget.TextView(themed);
                button.setGravity(android.view.Gravity.CENTER);
                MaterialIcons.iconOnly(button, R.drawable.ic_ms_menu, MaterialIcons.DARK, "Abrir menu");
                int side = Math.round(56 * themed.getResources().getDisplayMetrics().density);
                int spec = android.view.View.MeasureSpec.makeMeasureSpec(side, android.view.View.MeasureSpec.EXACTLY);
                button.measure(spec, spec); button.layout(0, 0, side, side);
                Bitmap bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
                button.draw(new Canvas(bitmap));
                int top = side, bottom = -1;
                for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
                    if (bitmap.getPixel(x, y) == Color.WHITE) { top = Math.min(top, y); bottom = Math.max(bottom, y); }
                }
                assertTrue("Icon-only controls remain centered", bottom >= 0 && Math.abs((top + bottom) / 2f - side / 2f) < 3);
                assertEquals("Abrir menu", button.getContentDescription());
                bitmap.recycle();
            });
            for (Field field : R.drawable.class.getFields()) {
                if (!field.getName().startsWith("ic_ms_")) continue;
                for (int surface : new int[]{Color.WHITE, MaterialIcons.DARK, Color.rgb(84, 214, 221), Color.rgb(50, 150, 145)}) {
                    Drawable drawable = MaterialIcons.onSurface(themed, field.getInt(null), surface);
                    int size = Math.round(24 * themed.getResources().getDisplayMetrics().density);
                    assertEquals(size, drawable.getBounds().width()); assertEquals(size, drawable.getBounds().height());
                    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    drawable.draw(new Canvas(bitmap));
                    int expected = surface == Color.WHITE ? MaterialIcons.DARK : Color.WHITE;
                    int pixels = 0;
                    for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) if (bitmap.getPixel(x, y) == expected) pixels++;
                    assertTrue(field.getName() + " must render visible monochrome strokes", pixels > 0);
                    int actualBackground = drawable instanceof LayerDrawable ? MaterialIcons.DARK : surface;
                    assertTrue(field.getName(), ColorUtils.calculateContrast(expected, actualBackground) >= 3);
                    bitmap.recycle();
                }
            }
        }
    }

    @Test public void dashboardAndMenuKeepAccessibleIconsInLightAndDark() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            for (boolean dark : new boolean[]{false, true}) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> new ThemeStore(context).setDarkModeEnabled(dark));
                try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                    scenario.onActivity(activity -> {
                        assertEquals(dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO,
                                activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
                        android.widget.TextView item = (android.widget.TextView) activity.getLayoutInflater()
                                .inflate(R.layout.drawer_profile, null).findViewById(R.id.drawerProgress);
                        assertNotNull(item.getCompoundDrawablesRelative()[0]);
                        assertEquals("Seu progresso", item.getText().toString());
                    });
                    Thread.sleep(700);
                    Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                    try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null),
                            dark ? "icons-dark.png" : "icons-light.png"))) {
                        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
                    }
                    screenshot.recycle();
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> new ThemeStore(context).setDarkModeEnabled(false));
        }
    }
}
