package com.example.dedilharte.network.model;

public final class ManagedUserRequest {
    public final String name;
    public final String email;
    public final String password;
    public final String role;

    public ManagedUserRequest(String name, String email, String password, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }
}
