package com.example.dedilharte.network.model;

import java.util.List;

public final class ArpeggioRequest {
    public final String id;
    public final String title;
    public final String chord;
    public final List<ArpeggioNote> notes;
    public final String difficulty;
    public final boolean active;
    public final int sortOrder;

    public ArpeggioRequest(
            String id,
            String title,
            String chord,
            List<ArpeggioNote> notes,
            String difficulty,
            boolean active,
            int sortOrder
    ) {
        this.id = id;
        this.title = title;
        this.chord = chord;
        this.notes = notes;
        this.difficulty = difficulty;
        this.active = active;
        this.sortOrder = sortOrder;
    }
}
