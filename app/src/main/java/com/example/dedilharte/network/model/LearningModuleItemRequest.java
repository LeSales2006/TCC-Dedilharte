package com.example.dedilharte.network.model;

import java.util.List;

public final class LearningModuleItemRequest {
    public final String itemType;
    public final String songId;
    public final String title;
    public final String instructions;
    public final int sortOrder;
    public final boolean active;
    public final String question;
    public final List<String> options;
    public final Integer correctIndex;
    public final String explanation;

    public LearningModuleItemRequest(
            String itemType,
            String songId,
            String title,
            String instructions,
            int sortOrder,
            boolean active,
            String question,
            List<String> options,
            Integer correctIndex,
            String explanation
    ) {
        this.itemType = itemType;
        this.songId = songId;
        this.title = title;
        this.instructions = instructions;
        this.sortOrder = sortOrder;
        this.active = active;
        this.question = question;
        this.options = options;
        this.correctIndex = correctIndex;
        this.explanation = explanation;
    }
}
