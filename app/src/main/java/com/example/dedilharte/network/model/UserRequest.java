package com.example.dedilharte.network.model;

public final class UserRequest {
    public final String id;
    public final String name;
    public final long updatedAtMillis;

    public UserRequest(String id, String name, long updatedAtMillis) {
        this.id = id;
        this.name = name;
        this.updatedAtMillis = updatedAtMillis;
    }
}
