package com.example.dedilharte.network.model;

public final class SongRequest {
    public final String title;
    public final String artist;
    public final String difficulty;
    public final int bpm;
    public final String tablature;
    public final int sortOrder;
    public final boolean active;

    public SongRequest(
            String title,
            String artist,
            String difficulty,
            int bpm,
            String tablature,
            int sortOrder,
            boolean active
    ) {
        this.title = title;
        this.artist = artist;
        this.difficulty = difficulty;
        this.bpm = bpm;
        this.tablature = tablature;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
