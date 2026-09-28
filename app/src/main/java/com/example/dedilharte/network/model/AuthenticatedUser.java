package com.example.dedilharte.network.model;

public final class AuthenticatedUser {
    public String id;
    public String name;
    public String email;
    public String role;
    public int weeklyGoal = 3;
    public boolean mustChangePassword;
    public boolean hasPhoto;
    public String profilePhotoUpdatedAt;
}
