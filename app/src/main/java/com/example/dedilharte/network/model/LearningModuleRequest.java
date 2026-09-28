package com.example.dedilharte.network.model;

public final class LearningModuleRequest {
    public final String name;
    public final String moduleType;
    public final String description;
    public final int sortOrder;
    public final boolean active;

    public LearningModuleRequest(String name, String moduleType, String description, int sortOrder, boolean active) {
        this.name = name;
        this.moduleType = moduleType;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
