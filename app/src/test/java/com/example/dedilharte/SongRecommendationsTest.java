package com.example.dedilharte;

import static org.junit.Assert.*;
import com.example.dedilharte.data.SongRepository;
import com.example.dedilharte.data.LessonRepository;
import com.example.dedilharte.model.Song;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SongRecommendationsTest {
    @Test public void prefersLevelThenFamiliarArtistsAndExcludesLearnedAndInactive() {
        List<Song> songs = Arrays.asList(
                song("learned", "Artist", Song.EASY, 0, true),
                song("other", "Other", Song.EASY, 1, true),
                song("familiar", " artist ", Song.EASY, 9, true),
                song("hard", "Artist", Song.HARD, 1, true),
                song("archived", "Artist", Song.EASY, 0, false));
        List<Song> result = SongRepository.recommendations(songs, Collections.singleton("learned"), LessonRepository.BEGINNER);
        assertEquals(Arrays.asList("familiar", "other", "hard"), result.stream().map(Song::getId).collect(Collectors.toList()));
        assertEquals(5, songs.size());
    }

    @Test public void intermediateLevelPrioritizesMediumAndLimitsCardsToThree() {
        List<Song> songs = Arrays.asList(song("easy", "", Song.EASY, 0, true),
                song("medium", "", Song.MEDIUM, 8, true), song("hard", "", Song.HARD, 1, true),
                song("extra", "", Song.EASY, 2, true));
        List<Song> result = SongRepository.recommendations(songs, Collections.emptySet(), LessonRepository.INTERMEDIATE);
        assertEquals(3, result.size());
        assertEquals("medium", result.get(0).getId());
    }

    @Test public void emptyOrEntirelyLearnedCatalogNeverGeneratesSuggestions() {
        assertTrue(SongRepository.recommendations(Collections.emptyList(), Collections.emptySet(), "Iniciante").isEmpty());
        assertTrue(SongRepository.recommendations(Collections.singletonList(song("learned", "", Song.EASY, 0, true)),
                Collections.singleton("learned"), "Iniciante").isEmpty());
    }

    private Song song(String id, String artist, String difficulty, int order, boolean active) {
        return new Song(id, id, artist, difficulty, 60, "", order, active,
                active ? Song.PUBLISHED : Song.ARCHIVED, 0, 0);
    }
}
