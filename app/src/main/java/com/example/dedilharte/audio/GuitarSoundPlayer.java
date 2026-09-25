package com.example.dedilharte.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import com.example.dedilharte.model.TablatureEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuitarSoundPlayer {

    private static final String TAG = "GuitarSoundPlayer";

    public enum State {
        LOADING,
        READY,
        ERROR
    }

    public interface OnStateChangeListener {
        void onStateChanged(State state);
    }

    private static final int[] GUITAR_MIDI_RANGE = {
            40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51,
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
            64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75,
            76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88
    };
    private static final float TARGET_SAMPLE_RMS = 0.020f;
    private static final float GENERAL_GAIN = 1.0f;
    private static final int MAX_NOTE_STREAM_COPIES = 3;

    private final SoundPool soundPool;
    private final Map<Integer, Integer> sampleByMidi = new HashMap<>();
    private final Map<Integer, Integer> midiBySample = new HashMap<>();
    private final Map<Integer, Float> rmsByMidi = new HashMap<>();
    private final List<Integer> activeStreams = new ArrayList<>();
    private int pendingLoads;
    private State state = State.LOADING;
    private OnStateChangeListener stateChangeListener;

    public GuitarSoundPlayer(Context context) {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        soundPool = new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(16)
                .build();
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            Integer midi = midiBySample.get(sampleId);
            pendingLoads--;
            if (status != 0) {
                if (midi != null) {
                    sampleByMidi.remove(midi);
                    rmsByMidi.remove(midi);
                    Log.e(TAG, "Falha ao carregar sample " + resourceNameForMidi(midi) + " para MIDI " + midi + ": status " + status);
                } else {
                    Log.e(TAG, "Falha ao carregar sample desconhecido: status " + status);
                }
            }
            if (pendingLoads <= 0) {
                updateState(sampleByMidi.isEmpty() ? State.ERROR : State.READY);
            }
        });
        loadSamples(context);
    }

    public State getState() {
        return state;
    }

    public String statusText() {
        if (state == State.LOADING) {
            return "Carregando sons";
        }
        if (state == State.READY) {
            return "Sons prontos";
        }
        return "Adicione samples de violao em res/raw para ativar o som";
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public void setOnStateChangeListener(OnStateChangeListener listener) {
        stateChangeListener = listener;
        if (listener != null) {
            listener.onStateChanged(state);
        }
    }

    public void play(TablatureEvent event) {
        if (!isReady() || event == null) {
            return;
        }
        float limiter = event.getNotes().size() > 1
                ? (float) (1d / Math.sqrt(event.getNotes().size()))
                : 1f;
        for (TablatureEvent.Note note : event.getNotes()) {
            playMidi(note.getMidiNote(), limiter);
        }
    }

    public void stopAll() {
        for (Integer streamId : activeStreams) {
            soundPool.stop(streamId);
        }
        activeStreams.clear();
    }

    public void release() {
        stopAll();
        soundPool.release();
    }

    private void playMidi(int midi, float limiter) {
        Integer exactSample = sampleByMidi.get(midi);
        if (exactSample == null) {
            Log.e(TAG, "Sample ausente para MIDI " + midi + " (" + midiToResourceName(midi) + ")");
            return;
        }
        float sampleRms = rmsByMidi.containsKey(midi) ? rmsByMidi.get(midi) : TARGET_SAMPLE_RMS;
        int copies = streamCopiesFor(sampleRms);
        float noteGain = Math.min(1f, TARGET_SAMPLE_RMS / Math.max(0.0001f, sampleRms * copies));
        float outputGain = Math.min(1f, noteGain * GENERAL_GAIN * limiter);
        for (int i = 0; i < copies; i++) {
            int streamId = soundPool.play(exactSample, outputGain, outputGain, 1, 0, 1f);
            if (streamId != 0) {
                activeStreams.add(streamId);
            }
        }
    }

    private int streamCopiesFor(float sampleRms) {
        int copies = Math.round(TARGET_SAMPLE_RMS / Math.max(0.0001f, sampleRms));
        if (copies < 1) {
            return 1;
        }
        return Math.min(MAX_NOTE_STREAM_COPIES, copies);
    }

    private void loadSamples(Context context) {
        for (int midi : GUITAR_MIDI_RANGE) {
            String resourceName = "guitar_" + midiToResourceName(midi);
            int resourceId = context.getResources().getIdentifier(
                    resourceName,
                    "raw",
                    context.getPackageName()
            );
            if (resourceId != 0) {
                int sampleId = soundPool.load(context, resourceId, 1);
                sampleByMidi.put(midi, sampleId);
                midiBySample.put(sampleId, midi);
                rmsByMidi.put(midi, rmsForMidi(midi));
                pendingLoads++;
            } else {
                Log.w(TAG, "Sample nao encontrado em res/raw: " + resourceName);
            }
        }
        if (pendingLoads == 0) {
            updateState(State.ERROR);
        }
    }

    private void updateState(State newState) {
        if (state == newState) {
            return;
        }
        state = newState;
        if (stateChangeListener != null) {
            stateChangeListener.onStateChanged(state);
        }
    }

    private String resourceNameForMidi(int midi) {
        return "guitar_" + midiToResourceName(midi);
    }

    private String midiToResourceName(int midi) {
        String[] names = {
                "c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b"
        };
        int octave = (midi / 12) - 1;
        return names[midi % 12] + octave;
    }

    private float rmsForMidi(int midi) {
        switch (midi) {
            case 40: return 0.0137f;
            case 41: return 0.0073f;
            case 42: return 0.0186f;
            case 43: return 0.0222f;
            case 44: return 0.0155f;
            case 45: return 0.0104f;
            case 46: return 0.0129f;
            case 47: return 0.0114f;
            case 48: return 0.0311f;
            case 49: return 0.0191f;
            case 50: return 0.0126f;
            case 51: return 0.0198f;
            case 52: return 0.0265f;
            case 53: return 0.0233f;
            case 54: return 0.0184f;
            case 55: return 0.0290f;
            case 56: return 0.0151f;
            case 57: return 0.0198f;
            case 58: return 0.0157f;
            case 59: return 0.0216f;
            case 60: return 0.0283f;
            case 61: return 0.0161f;
            case 62: return 0.0125f;
            case 63: return 0.0198f;
            case 64: return 0.0172f;
            case 65: return 0.0135f;
            case 66: return 0.0108f;
            case 67: return 0.0163f;
            case 68: return 0.0205f;
            case 69: return 0.0136f;
            case 70: return 0.0171f;
            case 71: return 0.0193f;
            case 72: return 0.0212f;
            case 73: return 0.0127f;
            case 74: return 0.0154f;
            case 75: return 0.0155f;
            case 76: return 0.0159f;
            case 77: return 0.0134f;
            case 78: return 0.0148f;
            case 79: return 0.0148f;
            case 80: return 0.0263f;
            case 81: return 0.0157f;
            case 82: return 0.0138f;
            case 83: return 0.0163f;
            default: return TARGET_SAMPLE_RMS;
        }
    }
}
