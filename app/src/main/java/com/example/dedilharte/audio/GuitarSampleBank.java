package com.example.dedilharte.audio;

import com.example.dedilharte.data.TablatureParser;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** String-specific SFZ channels 2..7; channel 1 is a generic MIDI mapping. */
final class GuitarSampleBank {
    private final List<Region> regions = new ArrayList<>();

    GuitarSampleBank(Reader manifestReader, Reader sfzReader) throws IOException {
        Manifest manifest = new Gson().fromJson(manifestReader, Manifest.class);
        Set<String> files = new HashSet<>();
        for (FileEntry entry : manifest.samples) files.add(entry.file.replace(".wav", ".ogg"));
        int channel = 0;
        Map<String, String> group = new HashMap<>();
        BufferedReader lines = new BufferedReader(sfzReader);
        String line;
        while ((line = lines.readLine()) != null) {
            line = line.split("//", 2)[0].trim();
            Map<String, String> values = values(line);
            if (line.startsWith("<master>")) channel = number(values, "lochan", 0);
            else if (line.startsWith("<group>")) group = values;
            else if (line.startsWith("<region>") && channel >= 2 && channel <= 7) {
                String file = values.get("sample");
                if (!files.contains(file)) throw new IOException("Sample fora do manifesto: " + file);
                int key = number(values, "key", -1);
                Region region = new Region();
                region.stringIndex = 7 - channel;
                region.low = number(values, "lokey", key);
                region.high = number(values, "hikey", key);
                region.root = number(values, "pitch_keycenter", key);
                region.lowVelocity = number(group, "lovel", 1);
                region.highVelocity = number(group, "hivel", 127);
                region.lowGain = Float.parseFloat(group.getOrDefault("amp_velcurve_" + region.lowVelocity, "0.708"));
                region.highGain = Float.parseFloat(group.getOrDefault("amp_velcurve_" + region.highVelocity, "1"));
                region.path = "guitar/" + manifest.ogg_dir + "/" + file;
                regions.add(region);
            }
        }
        if (regions.isEmpty()) throw new IOException("Mapa SFZ vazio");
    }

    Sample resolve(int stringIndex, int fret, int velocity) {
        int midi = TablatureParser.midiFromStringAndFret(stringIndex, fret);
        int strength = Math.max(1, Math.min(127, velocity));
        Region closest = null;
        int distance = Integer.MAX_VALUE;
        for (Region region : regions) {
            if (region.stringIndex != stringIndex || strength < region.lowVelocity || strength > region.highVelocity) continue;
            int delta = midi < region.low ? region.low - midi : Math.max(0, midi - region.high);
            if (delta < distance) { closest = region; distance = delta; }
        }
        if (closest == null) throw new IllegalArgumentException("Corda sem sample: " + stringIndex);
        // Frets 21..24 extend the same string's final recording locally.
        float rate = (float) Math.pow(2d, (midi - closest.root) / 12d);
        float fraction = (strength - closest.lowVelocity) / (float) Math.max(1, closest.highVelocity - closest.lowVelocity);
        return new Sample(closest.path, rate, closest.lowGain + fraction * (closest.highGain - closest.lowGain));
    }

    private static Map<String, String> values(String line) {
        Map<String, String> result = new HashMap<>();
        for (String token : line.split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals > 0) result.put(token.substring(0, equals), token.substring(equals + 1));
        }
        return result;
    }

    private static int number(Map<String, String> values, String key, int fallback) {
        return values.containsKey(key) ? Integer.parseInt(values.get(key)) : fallback;
    }

    static final class Sample {
        final String path;
        final float rate, gain;
        Sample(String path, float rate, float gain) { this.path = path; this.rate = rate; this.gain = gain; }
    }

    private static final class Region {
        int stringIndex, low, high, root, lowVelocity, highVelocity;
        float lowGain, highGain;
        String path;
    }
    private static final class Manifest { String ogg_dir; List<FileEntry> samples; }
    private static final class FileEntry { String file; }
}
