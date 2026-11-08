package com.example.dedilharte;

import static org.junit.Assert.*;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import com.example.dedilharte.audio.GuitarSoundPlayer;
import com.example.dedilharte.data.LessonRepository;
import com.example.dedilharte.model.Lesson;
import com.example.dedilharte.model.Song;
import com.example.dedilharte.view.GuitarPracticeView;
import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public class GuitarPracticeAudioInstrumentedTest extends MainActivityEncodingInstrumentedTest {
    @Test public void lessonsArpeggiosAndSongsPlayPauseAndMuteOffline() {
        for (int mode = 0; mode < 3; mode++) {
            final int selected = mode;
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    try {
                        if (selected == 0) {
                            invoke(activity, "showLessonDetail", Lesson.class, LessonRepository.getById("ini_1"));
                            click(activity.findViewById(R.id.root), "ABRIR MODO TREINO");
                        } else if (selected == 1) {
                            Class<?> type = Class.forName("com.example.dedilharte.MainActivity$Arpeggio");
                            Constructor<?> constructor = type.getDeclaredConstructor(String.class, int[][].class);
                            constructor.setAccessible(true);
                            invoke(activity, "showArpeggioPractice", type,
                                    constructor.newInstance("Teste", new int[][]{{6, 0}, {3, 0}, {2, 0}, {1, 0}}));
                        } else {
                            Song song = new Song("offline-test", "Teste", "", Song.EASY, 60,
                                    "E|0---0|\nB|0---0|\nG|0---0|\nD|0---0|\nA|0---0|\nE|0---0|",
                                    0, true, Song.PUBLISHED, 0, 0);
                            invoke(activity, "showSongPractice", Song.class, song);
                        }
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                AtomicBoolean ready = new AtomicBoolean();
                long deadline = SystemClock.uptimeMillis() + 30000;
                while (!ready.get() && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity(activity -> ready.set(((GuitarSoundPlayer) field(activity, "guitarSoundPlayer")).isReady()));
                    if (!ready.get()) SystemClock.sleep(50);
                }
                assertTrue("Local sounds must load", ready.get());
                scenario.onActivity(activity -> {
                    View root = activity.findViewById(R.id.root);
                    GuitarSoundPlayer player = (GuitarSoundPlayer) field(activity, "guitarSoundPlayer");
                    GuitarPracticeView practice = (GuitarPracticeView) field(activity, "practiceView");
                    practice.setBpm(88);
                    assertTrue(click(root, selected == 0 ? "INICIAR" : selected == 1 ? "PLAY" : "▶"));
                    assertTrue(practice.isRunning());
                    assertTrue(hasVoice(player));
                    assertTrue(click(root, "SOM: LIGADO"));
                    assertFalse(hasVoice(player));
                    assertTrue(practice.isRunning());
                    assertTrue(click(root, selected == 2 ? "⏸" : "PAUSAR"));
                    assertFalse(practice.isRunning());
                    assertTrue(click(root, "SOM: DESLIGADO"));
                    assertTrue(click(root, selected == 0 ? "CONTINUAR" : selected == 1 ? "PLAY" : "▶"));
                    assertTrue(hasVoice(player));
                    assertEquals(88, practice.getBpm());
                });
            }
        }
    }

    private static boolean hasVoice(GuitarSoundPlayer player) {
        for (Object voice : (Object[]) field(player, "voices")) if (voice != null) return true;
        return false;
    }
    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static void invoke(MainActivity activity, String name, Class<?> type, Object value) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(activity, value);
    }
    private static boolean click(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view.performClick();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (click(group.getChildAt(i), label)) return true;
        }
        return false;
    }
}
