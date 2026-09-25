package com.example.dedilharte.model;

import java.util.Arrays;

/**
 * Conteúdo imutável de uma aula do Dedilharte.
 */
public final class Lesson {

    private final String id;
    private final String level;
    private final int order;
    private final String title;
    private final String subtitle;
    private final String objective;
    private final String theory;
    private final String[] steps;
    private final int[] pattern;
    private final int bpm;
    private final String chord;

    public Lesson(
            String id,
            String level,
            int order,
            String title,
            String subtitle,
            String objective,
            String theory,
            String[] steps,
            int[] pattern,
            int bpm,
            String chord
    ) {
        this.id = id;
        this.level = level;
        this.order = order;
        this.title = title;
        this.subtitle = subtitle;
        this.objective = objective;
        this.theory = theory;
        this.steps = Arrays.copyOf(steps, steps.length);
        this.pattern = Arrays.copyOf(pattern, pattern.length);
        this.bpm = bpm;
        this.chord = chord;
    }

    public String getId() {
        return id;
    }

    public String getLevel() {
        return level;
    }

    public int getOrder() {
        return order;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getObjective() {
        return objective;
    }

    public String getTheory() {
        return theory;
    }

    public String[] getSteps() {
        return Arrays.copyOf(steps, steps.length);
    }

    public int[] getPattern() {
        return Arrays.copyOf(pattern, pattern.length);
    }

    public int getBpm() {
        return bpm;
    }

    public String getChord() {
        return chord;
    }
}
