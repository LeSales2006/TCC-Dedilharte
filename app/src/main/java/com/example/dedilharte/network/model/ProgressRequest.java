package com.example.dedilharte.network.model;

public final class ProgressRequest {
    public final String lesson_id;
    public final boolean completed;
    public final int score;
    public final long updatedAtMillis;

    public ProgressRequest(String lessonId, boolean completed, int score, long updatedAtMillis) {
        this.lesson_id = lessonId;
        this.completed = completed;
        this.score = score;
        this.updatedAtMillis = updatedAtMillis;
    }
}
