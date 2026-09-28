package com.example.dedilharte.network.model;

public final class ModuleProgressRequest {
    public final boolean completed;
    public final Integer selectedIndex;

    public ModuleProgressRequest(boolean completed, Integer selectedIndex) {
        this.completed = completed;
        this.selectedIndex = selectedIndex;
    }
}
