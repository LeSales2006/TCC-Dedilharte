package com.example.dedilharte.data;

public final class StudentMetrics {

    public static final float BEGINNER_TRIVIA_POINTS = 25f;
    public static final float INTERMEDIATE_TRIVIA_POINTS = 30f;
    public static final float SONG_POINTS = 30f;
    public static final float ACTIVITY_POINTS = 15f;
    public static final float INTERMEDIATE_SCORE_THRESHOLD = 60f;
    public static final float BEGINNER_TRIVIA_GATE = .70f;

    private StudentMetrics() {
    }

    public static WeeklyStatus weeklyStatus(int weeklyGoal, int accessDaysThisWeek) {
        int goal = clampGoal(weeklyGoal);
        float adherence = Math.min(1f, accessDaysThisWeek / (float) goal);
        if (adherence >= 1f) {
            return WeeklyStatus.EXEMPLAR;
        }
        if (adherence >= .60f) {
            return WeeklyStatus.EFFORT;
        }
        return WeeklyStatus.BACK_ROW;
    }

    public static float levelScore(
            float beginnerTriviaRatio,
            float intermediateTriviaRatio,
            int weightedLearned,
            int weightedTotal,
            float activityRatio
    ) {
        float songRatio = weightedTotal <= 0 ? 0f : clampRatio(weightedLearned / (float) weightedTotal);
        return clampRatio(beginnerTriviaRatio) * BEGINNER_TRIVIA_POINTS
                + clampRatio(intermediateTriviaRatio) * INTERMEDIATE_TRIVIA_POINTS
                + songRatio * SONG_POINTS
                + clampRatio(activityRatio) * ACTIVITY_POINTS;
    }

    public static String calculatedLevel(float levelScore, float beginnerTriviaRatio) {
        if (levelScore >= INTERMEDIATE_SCORE_THRESHOLD
                && clampRatio(beginnerTriviaRatio) >= BEGINNER_TRIVIA_GATE) {
            return "Intermediário";
        }
        return "Iniciante";
    }

    public static float activityRatio(int weeklyGoal, int[] weeklyAccesses) {
        if (weeklyAccesses == null || weeklyAccesses.length == 0) {
            return 0f;
        }
        int goal = clampGoal(weeklyGoal);
        float total = 0f;
        for (int weeklyAccess : weeklyAccesses) {
            total += Math.min(1f, Math.max(0, weeklyAccess) / (float) goal);
        }
        return total / weeklyAccesses.length;
    }

    public static int clampGoal(int weeklyGoal) {
        return Math.max(1, Math.min(7, weeklyGoal));
    }

    public static float clampRatio(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    public enum WeeklyStatus {
        EXEMPLAR("Exemplar", "Você cumpriu sua meta semanal!"),
        EFFORT("Aluno esforçado", "Você está no caminho."),
        BACK_ROW("Aluno do fundão", "A guitarra está sentindo sua falta.");

        private final String label;
        private final String message;

        WeeklyStatus(String label, String message) {
            this.label = label;
            this.message = message;
        }

        public String label() {
            return label;
        }

        public String message() {
            return message;
        }
    }
}
