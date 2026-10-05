package com.example.dedilharte.network.model;

public final class SongRequest {
    public final String id;
    public final String title;
    public final String artist;
    public final String difficulty;
    public final int recommendedBpm;
    public final String tablature;
    public final int queueOrder;
    public final boolean inQueue;
    public final String status;

    public SongRequest(
            String id,
            String title,
            String artist,
            String difficulty,
            int recommendedBpm,
            String tablature,
            int queueOrder,
            boolean inQueue,
            String status
    ) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.difficulty = difficulty;
        this.recommendedBpm = recommendedBpm;
        this.tablature = tablature;
        this.queueOrder = queueOrder;
        this.inQueue = inQueue;
        this.status = status;
    }
}
