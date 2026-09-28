package com.example.dedilharte.network.model;

public final class LearningModuleItemResponse {
    public String id;
    public String moduleId;
    public String itemType;
    public String songId;
    public String title;
    public String instructions;
    public int sortOrder;
    public boolean active = true;
    public MultipleChoiceResponse multipleChoice;
}
