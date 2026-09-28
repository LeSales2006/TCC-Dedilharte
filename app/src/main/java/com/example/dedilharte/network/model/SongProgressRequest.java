package com.example.dedilharte.network.model;

public final class SongProgressRequest {
    public final String song_id;
    public final boolean learned;

    public SongProgressRequest(String songId, boolean learned) {
        this.song_id = songId;
        this.learned = learned;
    }
}
