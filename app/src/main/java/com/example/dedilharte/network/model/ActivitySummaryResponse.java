package com.example.dedilharte.network.model;

public final class ActivitySummaryResponse {
    public int weeklyGoal;
    public int accessDaysThisWeek = -1;
    public int[] weeklyAccesses;
    public int accessDaysLastFourWeeks;
    public int availableWeeks = 1;
    public float averageAccessesPerWeek;
}
