package com.example.dedilharte.network.model;

public final class RegisterRequest {
    public final String name;
    public final String email;
    public final String password;
    public String initialLevel = "beginner";
    public int weeklyGoal = 3, songGoal = 10;
    public boolean notificationsEnabled;

    public RegisterRequest(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }
}
