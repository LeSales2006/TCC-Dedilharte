package com.example.dedilharte.data;

import com.example.dedilharte.model.Song;
import com.example.dedilharte.network.model.*;
import java.util.HashSet;
import java.util.Set;

/** Counts only published content and the authenticated user's persisted progress. */
public final class DashboardMetrics {
    public static final String BEGINNER = "module_trivia_beginner";
    public static final String INTERMEDIATE = "module_trivia_intermediate";
    public Count beginner = new Count(), intermediate = new Count();
    public Count songs = new Count(), easy = new Count(), medium = new Count(), hard = new Count();
    public int weeklyGoal, accessDays;
    public int[] weeklyAccesses;
    public long savedAt;
    // Old snapshots contain all groups. New snapshots may contain only confirmed groups.
    public boolean partial, triviaAvailable, songsAvailable, activityAvailable;
    public boolean hasTrivia() { return !partial || triviaAvailable; }
    public boolean hasSongs() { return !partial || songsAvailable; }
    public boolean hasActivity() { return !partial || activityAvailable; }
    public boolean complete() { return hasTrivia() && hasSongs() && hasActivity(); }

    public static final class Count {
        public int completed, total;
        public int percent() { return percentage(completed, total); }
        public float ratio() { return total == 0 ? 0f : completed / (float) total; }
    }

    public static int percentage(int completed, int total) {
        return total <= 0 ? 0 : Math.round(Math.max(0, Math.min(completed, total)) * 100f / total);
    }

    public String level() {
        int learnedWeight = easy.completed + medium.completed * 2 + hard.completed * 3;
        int totalWeight = easy.total + medium.total * 2 + hard.total * 3;
        float score = StudentMetrics.levelScore(beginner.ratio(), intermediate.ratio(), learnedWeight,
                totalWeight, StudentMetrics.activityRatio(weeklyGoal, weeklyAccesses));
        return StudentMetrics.calculatedLevel(score, beginner.ratio());
    }

    public boolean valid() {
        if (weeklyGoal < 1 || weeklyGoal > 7 || accessDays < 0 || accessDays > 7
                || weeklyAccesses == null || weeklyAccesses.length < 1 || weeklyAccesses.length > 4) return false;
        for (int days : weeklyAccesses) if (days < 0 || days > 7) return false;
        for (Count count : new Count[]{beginner, intermediate, songs, easy, medium, hard}) {
            if (count == null || count.completed < 0 || count.total < count.completed) return false;
        }
        return true;
    }

    public static DashboardMetrics calculate(String userId, LearningModuleListResponse modules,
            ModuleProgressListResponse moduleProgress, SongListResponse catalog,
            SongProgressListResponse songProgress, ActivitySummaryResponse activity) {
        if (modules == null || modules.items == null || moduleProgress == null || moduleProgress.items == null
                || catalog == null || catalog.items == null || songProgress == null || songProgress.items == null
                || activity == null || activity.weeklyGoal < 1 || activity.weeklyGoal > 7
                || activity.accessDaysThisWeek < 0 || activity.accessDaysThisWeek > 7) {
            throw new IllegalArgumentException("Incomplete dashboard response");
        }
        DashboardMetrics result = new DashboardMetrics();
        result.weeklyGoal = activity.weeklyGoal;
        result.accessDays = activity.accessDaysThisWeek;
        // Older servers do not supply the history; use the current confirmed week only.
        result.weeklyAccesses = activity.weeklyAccesses == null || activity.weeklyAccesses.length == 0
                ? new int[]{result.accessDays} : activity.weeklyAccesses.clone();
        Set<String> completed = new HashSet<>();
        for (ModuleProgressListResponse.Item record : moduleProgress.items) {
            if (record == null || blank(record.itemId)) throw new IllegalArgumentException("Invalid progress");
            if (record.userId != null && !userId.equals(record.userId)) throw new IllegalArgumentException("Wrong user");
            if (record.completed) completed.add(record.itemId);
        }
        Set<String> moduleIds = new HashSet<>();
        for (LearningModuleResponse module : modules.items) {
            if (module == null || blank(module.id) || module.items == null) throw new IllegalArgumentException("Invalid module");
            if (!module.active || !moduleIds.add(module.id)) continue;
            Count count = BEGINNER.equals(module.id) ? result.beginner : INTERMEDIATE.equals(module.id) ? result.intermediate : null;
            if (count == null) continue;
            Set<String> itemIds = new HashSet<>();
            for (LearningModuleItemResponse item : module.items) {
                if (item == null || blank(item.id)) throw new IllegalArgumentException("Invalid lesson");
                if (!item.active || !itemIds.add(item.id)) continue;
                count.total++;
                if (completed.contains(item.id)) count.completed++;
            }
        }
        Set<String> learned = new HashSet<>(), songIds = new HashSet<>();
        for (SongProgressResponse record : songProgress.items) {
            if (record == null || blank(record.song_id)) throw new IllegalArgumentException("Invalid song progress");
            if (record.user_id != null && !userId.equals(record.user_id)) throw new IllegalArgumentException("Wrong user");
            if (record.learned) learned.add(record.song_id);
        }
        for (SongResponse song : catalog.items) {
            if (song == null || !song.isValid()) throw new IllegalArgumentException("Invalid song");
            if (!song.isPublished() || !songIds.add(song.id)) continue;
            String difficulty = Song.normalizeDifficulty(song.difficulty);
            Count count = Song.HARD.equals(difficulty) ? result.hard : Song.MEDIUM.equals(difficulty) ? result.medium : result.easy;
            count.total++; result.songs.total++;
            if (learned.contains(song.id)) { count.completed++; result.songs.completed++; }
        }
        if (!result.valid()) throw new IllegalArgumentException("Invalid metrics");
        return result;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
