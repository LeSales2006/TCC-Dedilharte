package com.example.dedilharte.model;

public final class Song {

    public static final String DRAFT = "rascunho";
    public static final String PUBLISHED = "publicada";
    public static final String ARCHIVED = "arquivada";
    public static final String EASY = "easy";
    public static final String MEDIUM = "medium";
    public static final String HARD = "hard";

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
        this.difficulty = normalizeDifficulty(difficulty);
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
        return difficultyLabel(difficulty);
    }

    public String normalizedDifficulty() {
        return normalizeDifficulty(difficulty);
    }

    public String difficultyLabel() {
        return difficultyLabel(difficulty);
    }

    public int difficultyWeight() {
        return difficultyWeight(difficulty);
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

    public static String normalizeDifficulty(String value) {
        if (value == null) {
            return EASY;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals(MEDIUM)
                || normalized.equals("media")
                || normalized.equals("média")
                || normalized.equals("intermediario")
                || normalized.equals("intermediário")) {
            return MEDIUM;
        }
        if (normalized.equals(HARD)
                || normalized.equals("dificil")
                || normalized.equals("difícil")
                || normalized.equals("avancado")
                || normalized.equals("avançado")) {
            return HARD;
        }
        return EASY;
    }

    public static String difficultyLabel(String value) {
        switch (normalizeDifficulty(value)) {
            case MEDIUM:
                return "Média";
            case HARD:
                return "Difícil";
            case EASY:
            default:
                return "Fácil";
        }
    }

    public static int difficultyWeight(String value) {
        switch (normalizeDifficulty(value)) {
            case MEDIUM:
                return 2;
            case HARD:
                return 3;
            case EASY:
            default:
                return 1;
        }
    }
}
