package com.example.dedilharte.model;

public final class Song {

    public static final String DRAFT = "rascunho";
    public static final String PUBLISHED = "publicada";
    public static final String ARCHIVED = "arquivada";

    private final String id;
    private final String title;
    private final String artist;
    private final String difficulty;
    private final int recommendedBpm;
    private final String tablature;
    private final int queueOrder;
    private final boolean inQueue;
    private final String status;
    private final long createdAt;
    private final long updatedAt;

    public Song(
            String id,
            String title,
            String artist,
            String difficulty,
            int recommendedBpm,
            String tablature,
            int queueOrder,
            boolean inQueue,
            String status,
            long createdAt,
            long updatedAt
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public int getRecommendedBpm() {
        return recommendedBpm;
    }

    public String getTablature() {
        return tablature;
    }

    public int getQueueOrder() {
        return queueOrder;
    }

    public boolean isInQueue() {
        return inQueue;
    }

    public String getStatus() {
        return status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public Song withStatus(String newStatus) {
        return copy(queueOrder, inQueue, newStatus);
    }

    public Song withQueue(int newOrder, boolean newInQueue) {
        return copy(newOrder, newInQueue, status);
    }

    public Song copy(int newOrder, boolean newInQueue, String newStatus) {
        return new Song(
                id,
                title,
                artist,
                difficulty,
                recommendedBpm,
                tablature,
                newOrder,
                newInQueue,
                newStatus,
                createdAt,
                System.currentTimeMillis()
        );
    }
}
