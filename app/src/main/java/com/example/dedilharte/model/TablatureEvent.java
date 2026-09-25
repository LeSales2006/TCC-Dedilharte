package com.example.dedilharte.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TablatureEvent {

    private final int column;
    private final long instantMs;
    private final long durationMs;
    private final List<Note> notes;

    public TablatureEvent(int column, long instantMs, long durationMs, List<Note> notes) {
        this.column = column;
        this.instantMs = instantMs;
        this.durationMs = durationMs;
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
    }

    public int getColumn() {
        return column;
    }

    public long getInstantMs() {
        return instantMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public List<Note> getNotes() {
        return notes;
    }

    public boolean isChord() {
        return notes.size() > 1;
    }

    public static final class Note {
        private final String stringName;
        private final int stringIndex;
        private final int fret;
        private final String musicalNote;
        private final int midiNote;
        private final int fingerNumber;

        public Note(String stringName, int stringIndex, int fret, String musicalNote, int midiNote) {
            this(stringName, stringIndex, fret, musicalNote, midiNote, 0);
        }

        public Note(
                String stringName,
                int stringIndex,
                int fret,
                String musicalNote,
                int midiNote,
                int fingerNumber
        ) {
            this.stringName = stringName;
            this.stringIndex = stringIndex;
            this.fret = fret;
            this.musicalNote = musicalNote;
            this.midiNote = midiNote;
            this.fingerNumber = fingerNumber;
        }

        public String getStringName() {
            return stringName;
        }

        public int getStringIndex() {
            return stringIndex;
        }

        public int getFret() {
            return fret;
        }

        public String getMusicalNote() {
            return musicalNote;
        }

        public int getMidiNote() {
            return midiNote;
        }

        public int getFingerNumber() {
            return fingerNumber;
        }
    }
}
