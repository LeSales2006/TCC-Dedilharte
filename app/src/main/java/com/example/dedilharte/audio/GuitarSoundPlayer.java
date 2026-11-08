package com.example.dedilharte.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.dedilharte.model.TablatureEvent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline player. Public methods run on the UI thread; metadata is read off-thread. */
public final class GuitarSoundPlayer {
    private static final String TAG = "GuitarSoundPlayer";
    private static final int CACHE_LIMIT = 24;
    private static final int LOOK_AHEAD = 3;
    private static final long SOUND_POOL_LOAD_TIMEOUT_MS = 5000;
    public enum State { LOADING, READY, ERROR }
    public interface OnStateChangeListener { void onStateChanged(State state); }

    private final AssetManager assets;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final SoundPool soundPool = new SoundPool.Builder().setAudioAttributes(attributes).setMaxStreams(6).build();
    private final Map<String, Entry> cache = new LinkedHashMap<>(24, .75f, true);
    private final Set<String> required = new HashSet<>();
    private final Entry[] voices = new Entry[6];
    private final int[] streams = new int[6];
    private GuitarSampleBank bank;
    private List<TablatureEvent> sequence = new ArrayList<>();
    private int position;
    private int velocity = 80;
    private boolean looping, released;
    private State state = State.LOADING;
    private OnStateChangeListener listener;

    public GuitarSoundPlayer(Context context) {
        assets = context.getApplicationContext().getAssets();
        soundPool.setOnLoadCompleteListener((pool, id, status) -> {
            if (released) return;
            Entry loaded = null;
            for (Entry entry : cache.values()) {
                if (entry.soundId == id) {
                    loaded = entry;
                    break;
                }
            }
            if (loaded == null) return; // An evicted/fallback sample may finish late.
            if (status == 0) {
                cancelSoundPoolTimeout(loaded);
                loaded.ready = true;
            } else {
                fallbackToMediaPlayer(loaded);
            }
            refreshState();
        });
        loader.execute(() -> {
            try (InputStreamReader manifest = new InputStreamReader(assets.open("guitar/manifest.json"), StandardCharsets.UTF_8);
                 InputStreamReader sfz = new InputStreamReader(assets.open("guitar/meta-steel-guitar.sfz"), StandardCharsets.UTF_8)) {
                GuitarSampleBank loaded = new GuitarSampleBank(manifest, sfz);
                main.post(() -> {
                    if (released) return;
                    bank = loaded;
                    prepare(sequence, position, looping);
                });
            } catch (Exception error) { main.post(() -> fail(null, error)); }
        });
    }

    public State getState() { return state; }
    public boolean isReady() { return !released && state == State.READY; }
    public String statusText() {
        if (state == State.LOADING) return "Carregando sons locais…";
        if (state == State.READY) return "Sons prontos";
        return "Não foi possível carregar os sons locais";
    }
    public void setOnStateChangeListener(OnStateChangeListener listener) {
        this.listener = listener;
        if (!released && listener != null) listener.onStateChanged(state);
    }
    public void setVelocity(int velocity) {
        this.velocity = Math.max(1, Math.min(127, velocity));
        prepare(sequence, position, looping);
    }

    /** At most three events plus the six ringing strings stay resident. */
    public void prepare(List<TablatureEvent> events, int currentPosition, boolean loop) {
        if (released) return;
        sequence = events;
        position = currentPosition;
        looping = loop;
        if (bank == null) return;
        required.clear();
        Set<String> window = new java.util.LinkedHashSet<>();
        for (int offset = 0; offset < Math.min(LOOK_AHEAD, events.size()); offset++) {
            int index = currentPosition + offset;
            if (index >= events.size() && !loop) break;
            for (TablatureEvent.Note note : events.get(index % events.size()).getNotes()) {
                String path = sample(note).path;
                window.add(path);
                if (offset == 0) required.add(path);
            }
        }
        for (String path : window) {
            if (cache.get(path) != null) continue;
            if (cache.size() >= CACHE_LIMIT) {
                for (Entry entry : new ArrayList<>(cache.values())) {
                    if (!window.contains(entry.path) && !isActive(entry)) {
                        cache.remove(entry.path);
                        dispose(entry);
                        break;
                    }
                }
            }
            if (cache.size() >= CACHE_LIMIT) break;
            Entry entry = new Entry(path);
            cache.put(path, entry);
            inspect(entry);
        }
        refreshState();
    }

    public void play(TablatureEvent event) {
        if (released || bank == null || event == null) return;
        // Never play only part of a chord, or replay a stale note after a load.
        for (TablatureEvent.Note note : event.getNotes()) {
            Entry entry = cache.get(sample(note).path);
            if (entry == null || !entry.ready) return;
        }
        float limiter = 1f / (float) Math.sqrt(Math.max(1, event.getNotes().size()));
        for (TablatureEvent.Note note : event.getNotes()) {
            int string = note.getStringIndex();
            stopString(string);
            GuitarSampleBank.Sample sample = sample(note);
            Entry entry = cache.get(sample.path);
            float gain = sample.gain * limiter;
            try {
                if (entry.media != null) {
                    entry.media.setVolume(gain, gain);
                    entry.media.seekTo(0);
                    entry.media.setPlaybackParams(new PlaybackParams().setSpeed(sample.rate).setPitch(sample.rate));
                    entry.media.start();
                } else {
                    streams[string] = soundPool.play(entry.soundId, gain, gain, 1, 0, sample.rate);
                }
                voices[string] = entry;
            } catch (RuntimeException error) { fail(entry, error); }
        }
    }
    public void stopAll() {
        if (!released) for (int string = 0; string < 6; string++) stopString(string);
    }
    public void release() {
        if (released) return;
        stopAll();
        released = true;
        listener = null;
        loader.shutdownNow();
        for (Entry entry : cache.values()) dispose(entry);
        cache.clear();
        soundPool.release();
    }
    private GuitarSampleBank.Sample sample(TablatureEvent.Note note) {
        return bank.resolve(note.getStringIndex(), note.getFret(), velocity);
    }
    private void inspect(Entry entry) {
        loader.execute(() -> {
            if (entry.disposed) return;
            MediaExtractor extractor = new MediaExtractor();
            try (AssetFileDescriptor fd = assets.openFd(entry.path)) {
                extractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                MediaFormat format = extractor.getTrackFormat(0);
                long pcmBytes = format.getLong(MediaFormat.KEY_DURATION) * format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        / 1_000_000L * format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) * 2L;
                // SoundPool truncates beyond 1 MiB. Stream long decays locally instead.
                boolean longSample = pcmBytes > 1_048_576L;
                main.post(() -> load(entry, longSample));
            } catch (Exception error) { main.post(() -> fail(entry, error)); }
            finally { extractor.release(); }
        });
    }
    private void load(Entry entry, boolean longSample) {
        if (released || cache.get(entry.path) != entry) return;
        try (AssetFileDescriptor fd = assets.openFd(entry.path)) {
            if (longSample) {
                MediaPlayer media = new MediaPlayer();
                entry.media = media;
                media.setAudioAttributes(attributes);
                media.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                media.setOnPreparedListener(player -> {
                    if (released || cache.get(entry.path) != entry) return;
                    entry.ready = true;
                    refreshState();
                });
                media.setOnErrorListener((player, what, extra) -> {
                    fail(entry, new IllegalStateException("MediaPlayer " + what + "/" + extra));
                    return true;
                });
                media.prepareAsync();
            } else {
                entry.soundId = soundPool.load(fd, 1);
                if (entry.soundId == 0) {
                    fallbackToMediaPlayer(entry);
                } else {
                    entry.loadTimeout = () -> fallbackToMediaPlayer(entry);
                    main.postDelayed(entry.loadTimeout, SOUND_POOL_LOAD_TIMEOUT_MS);
                }
            }
        } catch (Exception error) { fail(entry, error); }
    }
    private void stopString(int string) {
        if (streams[string] != 0) soundPool.stop(streams[string]);
        streams[string] = 0;
        Entry entry = voices[string];
        voices[string] = null;
        if (entry != null && entry.media != null && entry.ready && !entry.failed) {
            entry.media.pause();
            entry.media.seekTo(0);
        }
    }
    private boolean isActive(Entry entry) {
        for (Entry voice : voices) if (voice == entry) return true;
        return false;
    }
    private void dispose(Entry entry) {
        entry.disposed = true;
        cancelSoundPoolTimeout(entry);
        if (entry.media != null) entry.media.release();
        if (entry.soundId != 0) soundPool.unload(entry.soundId);
    }
    private void fail(Entry entry, Exception error) {
        if (released || (entry != null && cache.get(entry.path) != entry)) return;
        if (entry != null) cancelSoundPoolTimeout(entry);
        Log.e(TAG, "Falha no áudio local" + (entry == null ? "" : ": " + entry.path), error);
        if (entry == null) updateState(State.ERROR);
        else { entry.failed = true; entry.ready = false; refreshState(); }
    }
    private void cancelSoundPoolTimeout(Entry entry) {
        if (entry.loadTimeout != null) main.removeCallbacks(entry.loadTimeout);
        entry.loadTimeout = null;
    }
    private void fallbackToMediaPlayer(Entry entry) {
        if (released || entry.disposed || entry.ready || entry.failed
                || entry.media != null || cache.get(entry.path) != entry) return;
        cancelSoundPoolTimeout(entry);
        Log.w(TAG, "SoundPool não confirmou a amostra; usando MediaPlayer local: " + entry.path);
        int oldSoundId = entry.soundId;
        entry.soundId = 0;
        if (oldSoundId != 0) soundPool.unload(oldSoundId);
        load(entry, true);
    }
    private void refreshState() {
        if (released) return;
        State next = State.READY;
        for (String path : required) {
            Entry entry = cache.get(path);
            if (entry != null && entry.failed) { next = State.ERROR; break; }
            if (entry == null || !entry.ready) next = State.LOADING;
        }
        updateState(next);
    }
    private void updateState(State next) {
        if (state == next) return;
        state = next;
        if (listener != null) listener.onStateChanged(next);
    }
    private static final class Entry {
        final String path;
        int soundId;
        MediaPlayer media;
        Runnable loadTimeout;
        boolean ready, failed;
        volatile boolean disposed;
        Entry(String path) { this.path = path; }
    }
}
