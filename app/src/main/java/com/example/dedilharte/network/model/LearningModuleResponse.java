package com.example.dedilharte.network.model;

import java.util.ArrayList;
import java.util.List;

public final class LearningModuleResponse {
    public String id;
    public String name;
    public String moduleType;
    public String description;
    public int sortOrder;
    public boolean active = true;
    public List<LearningModuleItemResponse> items = new ArrayList<>();
}
