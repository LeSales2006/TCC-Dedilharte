package com.example.dedilharte.network.model;

public final class UserRequest {
    public final String id;
    public final String name;
    public final int weeklyGoal;
    public final long updatedAtMillis;

    public UserRequest(String id, String name, int weeklyGoal, long updatedAtMillis) {
        this.id = id;
        this.name = name;
        this.weeklyGoal = weeklyGoal;
        this.updatedAtMillis = updatedAtMillis;
    }

    public UserRequest(String id, String name, long updatedAtMillis) {
        this(id, name, 3, updatedAtMillis);
    }
}
