package com.example.dedilharte;

import static org.junit.Assert.*;
import com.example.dedilharte.data.DashboardMetrics;
import com.example.dedilharte.network.model.*;
import java.util.Arrays;
import java.util.ArrayList;
import org.junit.Test;

public class DashboardMetricsTest {
    private LearningModuleListResponse modules = new LearningModuleListResponse();
    private ModuleProgressListResponse progress = new ModuleProgressListResponse();
    private SongListResponse songs = new SongListResponse();
    private SongProgressListResponse learned = new SongProgressListResponse();
    private ActivitySummaryResponse activity = new ActivitySummaryResponse();

    public DashboardMetricsTest() {
        modules.items = new ArrayList<>(); progress.items = new ArrayList<>();
        songs.items = new ArrayList<>(); learned.items = new ArrayList<>();
        activity.weeklyGoal = 3; activity.accessDaysThisWeek = 2;
        activity.weeklyAccesses = new int[]{0, 1, 3, 2};
    }
    private DashboardMetrics calculate() { return DashboardMetrics.calculate("a", modules, progress, songs, learned, activity); }

    @Test public void percentagesHandleRoundingEmptyAndOverGoal() {
        assertEquals(67, DashboardMetrics.percentage(2, 3));
        assertEquals(20, DashboardMetrics.percentage(1, 5));
        assertEquals(100, DashboardMetrics.percentage(7, 3));
        assertEquals(0, DashboardMetrics.percentage(0, 0));
        assertEquals(0, DashboardMetrics.percentage(-1, 4));
    }
    @Test public void countsArePerTriviaIgnoreInactiveDuplicatesAndOrphans() {
        LearningModuleResponse beginner = module(DashboardMetrics.BEGINNER, "b", 5);
        modules.items.add(beginner); modules.items.add(beginner);
        modules.items.add(module(DashboardMetrics.INTERMEDIATE, "i", 3));
        LearningModuleItemResponse hidden = new LearningModuleItemResponse(); hidden.id = "hidden"; hidden.active = false;
        beginner.items.add(hidden); beginner.items.add(beginner.items.get(0));
        complete("b0"); complete("b0"); complete("i0"); complete("i1"); complete("hidden"); complete("removed");
        DashboardMetrics data = calculate();
        assertEquals(5, data.beginner.total); assertEquals(1, data.beginner.completed); assertEquals(20, data.beginner.percent());
        assertEquals(3, data.intermediate.total); assertEquals(2, data.intermediate.completed); assertEquals(67, data.intermediate.percent());
    }
    @Test public void songsUsePublishedCatalogAndDifficultyDenominators() {
        addSong("easy", "easy", true); addSong("medium", "Média", true); addSong("hard", "hard", true);
        addSong("hidden", "hard", false); songs.items.add(songs.items.get(0));
        for (String id : Arrays.asList("easy", "hard", "hidden", "removed")) {
            SongProgressResponse record = new SongProgressResponse(); record.user_id = "a"; record.song_id = id; record.learned = true;
            learned.items.add(record);
        }
        DashboardMetrics data = calculate();
        assertEquals(3, data.songs.total); assertEquals(2, data.songs.completed); assertEquals(67, data.songs.percent());
        assertEquals(100, data.easy.percent()); assertEquals(0, data.medium.percent()); assertEquals(100, data.hard.percent());
    }
    @Test public void emptyCatalogAndProgressAreRealZeroes() {
        DashboardMetrics data = calculate();
        assertEquals(0, data.beginner.total); assertEquals(0, data.songs.total); assertEquals("Iniciante", data.level());
    }
    @Test public void levelUsesRealTriviaAndSongCompletion() {
        modules.items.add(module(DashboardMetrics.BEGINNER, "b", 1));
        modules.items.add(module(DashboardMetrics.INTERMEDIATE, "i", 1));
        complete("b0"); complete("i0");
        activity.weeklyAccesses = new int[]{3, 3, 3, 3};
        assertEquals("Intermediário", calculate().level());
        progress.items.clear(); assertEquals("Iniciante", calculate().level());
    }
    @Test public void incompleteAndCrossUserResponsesAreRejected() {
        progress.items = null;
        assertThrows(IllegalArgumentException.class, this::calculate);
        progress.items = new ArrayList<>(); complete("b0"); progress.items.get(0).userId = "other";
        assertThrows(IllegalArgumentException.class, this::calculate);
        progress.items.clear(); activity.weeklyAccesses = new int[]{9};
        assertThrows(IllegalArgumentException.class, this::calculate);
    }
    private LearningModuleResponse module(String id, String prefix, int size) {
        LearningModuleResponse module = new LearningModuleResponse(); module.id = id;
        for (int i = 0; i < size; i++) { LearningModuleItemResponse item = new LearningModuleItemResponse(); item.id = prefix + i; module.items.add(item); }
        return module;
    }
    private void complete(String id) { ModuleProgressListResponse.Item item = new ModuleProgressListResponse.Item(); item.itemId = id; item.userId = "a"; item.completed = true; progress.items.add(item); }
    private void addSong(String id, String difficulty, boolean active) { SongResponse song = new SongResponse(); song.id = id; song.title = id; song.difficulty = difficulty; song.active = active; songs.items.add(song); }
}
