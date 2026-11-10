package com.example.dedilharte.audio;

import static org.junit.Assert.*;
import android.content.Context;
import android.media.MediaPlayer;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.dedilharte.data.TablatureParser;
import com.example.dedilharte.model.TablatureEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GuitarSoundPlayerInstrumentedTest {
    private GuitarSoundPlayer player;

    @Before public void create() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ui(() -> player = new GuitarSoundPlayer(context));
    }
    @After public void release() { ui(() -> player.release()); }

    @Test public void chordsUseBothLocalBackendsAndStopWithoutLatePlayback() throws Exception {
        List<TablatureEvent> events = new ArrayList<>();
        events.add(chord(0));
        ready(() -> { player.setVelocity(127); player.prepare(events, 0, false); });
        ui(() -> {
            assertTrue(cache().size() <= 6);
            boolean longSample = false, shortSample = false;
            for (Object entry : cache().values()) {
                longSample |= field(entry, "media") != null;
                shortSample |= ((Integer) field(entry, "soundId")) != 0;
            }
            assertTrue(longSample);
            assertTrue(shortSample);
            player.play(events.get(0));
            for (Object voice : (Object[]) field(player, "voices")) assertNotNull(voice);
            for (Object entry : cache().values()) {
                MediaPlayer media = (MediaPlayer) field(entry, "media");
                if (media != null) assertTrue(media.isPlaying());
            }
            Object[] voices = (Object[]) field(player, "voices");
            int[] streams = (int[]) field(player, "streams");
            for (int string = 0; string < 6; string++) {
                if (field(voices[string], "media") == null) assertTrue(streams[string] > 0);
            }
            player.stopAll();
            for (Object voice : (Object[]) field(player, "voices")) assertNull(voice);
            for (Object entry : cache().values()) {
                MediaPlayer media = (MediaPlayer) field(entry, "media");
                if (media != null) assertFalse(media.isPlaying());
            }
        });
        ready(() -> { player.setVelocity(1); player.prepare(events, 0, false); player.stopAll(); });
        ui(() -> {
            for (Object voice : (Object[]) field(player, "voices")) assertNull(voice);
            player.release();
            player.release();
            assertTrue(cache().isEmpty());
            assertFalse(player.isReady());
        });
    }

    @Test public void longExerciseKeepsCacheBoundedAcrossAllFrets() throws Exception {
        List<TablatureEvent> events = new ArrayList<>();
        for (int fret = 0; fret <= 24; fret++) events.add(chord(fret));
        for (int position = 0; position < events.size(); position++) {
            final int index = position;
            android.util.Log.i("GuitarSoundPlayerTest", "Preparing fret " + index);
            ready(() -> player.prepare(events, index, true));
            ui(() -> {
                assertTrue(cache().size() <= 24);
                player.play(events.get(index));
                for (Object voice : (Object[]) field(player, "voices")) assertNotNull(voice);
            });
        }
    }

    @Test public void sustainedChordAdvancesLocalPlayback() throws Exception {
        List<TablatureEvent> events = java.util.Collections.singletonList(chord(0));
        ready(() -> { player.setVelocity(127); player.prepare(events, 0, false); });
        ui(() -> {
            player.play(events.get(0));
            android.util.Log.i("GuitarSoundPlayerTest", "Sustained chord started");
        });
        // MediaPlayer starts asynchronously. Wait for actual progress instead of
        // assuming decoder startup and playback finish within a fixed two seconds.
        boolean[] advanced = {false};
        long deadline = android.os.SystemClock.elapsedRealtime() + 8000;
        while (!advanced[0] && android.os.SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(100);
            ui(() -> {
                advanced[0] = true;
                for (Object voice : (Object[]) field(player, "voices")) {
                    if (voice == null) { advanced[0] = false; continue; }
                    MediaPlayer media = (MediaPlayer) field(voice, "media");
                    if (media != null && media.getCurrentPosition() < 1000) advanced[0] = false;
                }
            });
        }
        ui(() -> {
            for (Object voice : (Object[]) field(player, "voices")) {
                MediaPlayer media = (MediaPlayer) field(voice, "media");
                if (media != null) android.util.Log.i("GuitarSoundPlayerTest", field(voice, "path")
                        + " position=" + media.getCurrentPosition() + " playing=" + media.isPlaying());
            }
        });
        ui(() -> {
            for (Object voice : (Object[]) field(player, "voices")) {
                assertNotNull(voice);
                MediaPlayer media = (MediaPlayer) field(voice, "media");
                if (media != null) {
                    assertTrue(field(voice, "path") + ": playback position=" + media.getCurrentPosition(),
                            media.getCurrentPosition() >= 1000);
                    assertTrue(media.isPlaying());
                }
            }
        });
        // Leave the decay audible long enough to inspect the host/emulator output meter.
        Thread.sleep(4000);
    }

    @Test public void missingSoundPoolCallbackFallsBackWithoutPlayingAfterCancellation() throws Exception {
        TablatureEvent.Note note = chord(0).getNotes().get(0); // High E: a short SoundPool recording.
        List<TablatureEvent> events = java.util.Collections.singletonList(new TablatureEvent(
                0, 0, 500, java.util.Collections.singletonList(note)));
        ready(() -> {
            ((android.media.SoundPool) field(player, "soundPool")).setOnLoadCompleteListener(null);
            player.setVelocity(127);
            player.prepare(events, 0, false);
            player.stopAll();
        });
        ui(() -> {
            assertEquals(1, cache().size());
            Object entry = cache().values().iterator().next();
            MediaPlayer media = (MediaPlayer) field(entry, "media");
            assertNotNull("A missing callback must switch to local streaming", media);
            assertFalse(media.isPlaying());
            for (Object voice : (Object[]) field(player, "voices")) assertNull(voice);
            player.play(events.get(0));
            assertTrue(media.isPlaying());
            player.stopAll();
            assertFalse(media.isPlaying());
        });
    }

    private void ready(Runnable prepare) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        ui(() -> {
            player.setOnStateChangeListener(null);
            prepare.run();
            player.setOnStateChangeListener(state -> {
                if (state != GuitarSoundPlayer.State.LOADING) done.countDown();
            });
        });
        boolean loaded = done.await(30, TimeUnit.SECONDS);
        if (!loaded) ui(() -> {
            android.util.Log.e("GuitarSoundPlayerTest", "Load timeout at position=" + field(player, "position")
                    + " required=" + field(player, "required") + " cache=" + cache().size());
            for (Object entry : cache().values()) {
                android.util.Log.e("GuitarSoundPlayerTest", field(entry, "path") + " ready=" + field(entry, "ready")
                        + " failed=" + field(entry, "failed") + " soundId=" + field(entry, "soundId")
                        + " media=" + (field(entry, "media") != null));
            }
        });
        assertTrue("Timeout loading local OGG", loaded);
        ui(() -> assertEquals(player.statusText(), GuitarSoundPlayer.State.READY, player.getState()));
    }
    private Map<?, ?> cache() { return (Map<?, ?>) field(player, "cache"); }
    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static void ui(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }
    private static TablatureEvent chord(int fret) {
        List<TablatureEvent.Note> notes = new ArrayList<>();
        for (int string = 0; string < 6; string++) {
            notes.add(new TablatureEvent.Note("", string, fret,
                    TablatureParser.getNoteFromStringAndFret(string, fret),
                    TablatureParser.midiFromStringAndFret(string, fret)));
        }
        return new TablatureEvent(fret * 4, fret * 1000L, 500, notes);
    }
}
