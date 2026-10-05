package com.example.dedilharte.network.model;

public final class SongResponse {
    public String id;
    public String title;
    public String artist;
    public String difficulty = "easy";
    public int recommendedBpm = 60;
    public String tablature = "";
    public int queueOrder;
    public boolean inQueue = true;
    public String status = "rascunho";
    public long createdAt;
    public long updatedAt;
}
