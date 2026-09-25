package com.example.dedilharte.data;

import com.example.dedilharte.model.TablatureEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class TablatureParser {

    public static final int DEFAULT_COLUMNS_PER_BEAT = 4;
    public static final int DEFAULT_MAX_FRET = 24;
    private static final String[] STRING_NAMES = {"E", "B", "G", "D", "A", "E"};
    private static final int[] OPEN_STRING_MIDI = {64, 59, 55, 50, 45, 40};
    private static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    private TablatureParser() {
    }

    public static List<TablatureEvent> parse(String tablatureText) {
        return parse(tablatureText, 60, DEFAULT_COLUMNS_PER_BEAT);
    }

    public static List<TablatureEvent> parse(String tablatureText, int bpm, int columnsPerBeat) {
        if (bpm <= 0) {
            throw new IllegalArgumentException("Informe um BPM recomendado maior que zero.");
        }
        if (tablatureText == null || tablatureText.length() == 0) {
            throw new IllegalArgumentException("A tablatura esta vazia.");
        }

        String[] rawLines = tablatureText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> tabLines = new ArrayList<>();
        for (String rawLine : rawLines) {
            if (rawLine.length() > 0) {
                tabLines.add(extractTabContent(rawLine));
            }
        }

        if (tabLines.size() != 6) {
            throw new IllegalArgumentException("A tablatura precisa ter exatamente seis linhas de cordas.");
        }

        int expectedLength = tabLines.get(0).length();
        if (expectedLength == 0) {
            throw new IllegalArgumentException("A tablatura nao possui conteudo entre os delimitadores.");
        }

        Map<Integer, List<TablatureEvent.Note>> grouped = new TreeMap<>();
        for (int stringIndex = 0; stringIndex < 6; stringIndex++) {
            String line = tabLines.get(stringIndex);
            if (line.length() != expectedLength) {
                throw new IllegalArgumentException("As linhas da tablatura estao desalinhadas.");
            }
            readLine(line, stringIndex, grouped);
        }

        if (grouped.isEmpty()) {
            throw new IllegalArgumentException("A musica nao possui notas na tablatura.");
        }

        return buildEvents(grouped, bpm, columnsPerBeat);
    }

    public static String getNoteFromStringAndFret(int stringIndex, int fret) {
        return noteNameForMidi(midiFromStringAndFret(stringIndex, fret));
    }

    public static int midiFromStringAndFret(int stringIndex, int fret) {
        if (stringIndex < 0 || stringIndex >= OPEN_STRING_MIDI.length) {
            throw new IllegalArgumentException("Indice de corda invalido.");
        }
        if (fret < 0) {
            throw new IllegalArgumentException("A casa nao pode ser negativa.");
        }
        if (fret > DEFAULT_MAX_FRET) {
            throw new IllegalArgumentException("Casa acima do limite suportado: " + DEFAULT_MAX_FRET + ".");
        }
        return OPEN_STRING_MIDI[stringIndex] + fret;
    }

    public static long eventTimeMs(TablatureEvent event, int firstColumn, int bpm, int columnsPerBeat) {
        int safeBpm = Math.max(1, bpm);
        int safeColumnsPerBeat = Math.max(1, columnsPerBeat);
        double beatDurationMs = 60000d / safeBpm;
        return Math.round((event.getColumn() - firstColumn) * (beatDurationMs / safeColumnsPerBeat));
    }

    private static String extractTabContent(String rawLine) {
        int firstPipe = rawLine.indexOf('|');
        int lastPipe = rawLine.lastIndexOf('|');
        if (firstPipe < 0 || lastPipe <= firstPipe) {
            throw new IllegalArgumentException("Linha sem delimitadores '|': " + rawLine);
        }
        return rawLine.substring(firstPipe + 1, lastPipe);
    }

    private static void readLine(
            String line,
            int stringIndex,
            Map<Integer, List<TablatureEvent.Note>> grouped
    ) {
        int column = 0;
        while (column < line.length()) {
            char current = line.charAt(column);
            if (Character.isDigit(current)) {
                int start = column;
                StringBuilder digits = new StringBuilder();
                while (column < line.length() && Character.isDigit(line.charAt(column))) {
                    digits.append(line.charAt(column));
                    column++;
                }
                int fret = Integer.parseInt(digits.toString());
                int midi = midiFromStringAndFret(stringIndex, fret);
                List<TablatureEvent.Note> notes = grouped.get(start);
                if (notes == null) {
                    notes = new ArrayList<>();
                    grouped.put(start, notes);
                }
                notes.add(new TablatureEvent.Note(
                        STRING_NAMES[stringIndex],
                        stringIndex,
                        fret,
                        noteNameForMidi(midi),
                        midi
                ));
            } else if (current == '-' || current == ' ') {
                column++;
            } else {
                throw new IllegalArgumentException("Caractere invalido na tablatura: '" + current + "'.");
            }
        }
    }

    private static List<TablatureEvent> buildEvents(
            Map<Integer, List<TablatureEvent.Note>> grouped,
            int bpm,
            int columnsPerBeat
    ) {
        List<Integer> columns = new ArrayList<>(grouped.keySet());
        int firstColumn = columns.get(0);
        List<TablatureEvent> events = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            int column = columns.get(i);
            long instant = timeFromColumn(column, firstColumn, bpm, columnsPerBeat);
            long duration;
            if (i + 1 < columns.size()) {
                duration = timeFromColumn(columns.get(i + 1), firstColumn, bpm, columnsPerBeat) - instant;
            } else {
                duration = Math.round((60000d / bpm) / 2d);
            }
            long soundDuration = Math.max(90L, Math.min(duration, Math.round((60000d / bpm) * .75d)));
            events.add(new TablatureEvent(column, instant, soundDuration, grouped.get(column)));
        }
        return events;
    }

    private static long timeFromColumn(int column, int firstColumn, int bpm, int columnsPerBeat) {
        double beatDurationMs = 60000d / Math.max(1, bpm);
        double columnDurationMs = beatDurationMs / Math.max(1, columnsPerBeat);
        return Math.round((column - firstColumn) * columnDurationMs);
    }

    private static String noteNameForMidi(int midi) {
        int octave = (midi / 12) - 1;
        return NOTE_NAMES[midi % 12] + octave;
    }
}
