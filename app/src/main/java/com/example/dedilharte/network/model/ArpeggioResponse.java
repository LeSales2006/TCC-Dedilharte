package com.example.dedilharte.network.model;

import java.util.ArrayList;
import java.util.List;

public final class ArpeggioResponse {
    public String id;
    public String title;
    public String chord;
    public List<ArpeggioNote> notes = new ArrayList<>();
    public String difficulty = "easy";
    public Boolean active;
    public int sortOrder;
    public String created_at;
    public String updated_at;

    public boolean isValid() {
        if (id == null || id.trim().isEmpty() || title == null || title.trim().isEmpty()
                || active == null || notes == null || notes.isEmpty()) return false;
        for (ArpeggioNote note : notes) {
            if (note == null || note.string < 1 || note.string > 6 || note.fret < 0) return false;
        }
        return true;
    }
}
