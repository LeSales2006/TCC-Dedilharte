package com.example.dedilharte.network.model;

public final class SongResponse {
    public String id;
    public String title;
    public String artist;
    public String difficulty = "easy";
    @com.google.gson.annotations.SerializedName(value = "bpm", alternate = {"recommendedBpm", "recommended_bpm"})
    public int bpm = 60;
    public String tablature = "";
    @com.google.gson.annotations.SerializedName(value = "sortOrder", alternate = {"sort_order", "queueOrder", "queue_order"})
    public int sortOrder;
    public Boolean active;
    public String status;
    @com.google.gson.annotations.SerializedName(value = "inQueue", alternate = {"in_queue"})
    public Boolean inQueue;
    public com.google.gson.JsonElement content;

    public boolean isPublished() {
        return active != null ? active : "publicada".equals(status) && !Boolean.FALSE.equals(inQueue);
    }

    public boolean isValid() {
        return id != null && !id.trim().isEmpty() && title != null && !title.trim().isEmpty()
                && bpm > 0 && (active != null || status != null);
    }
}
