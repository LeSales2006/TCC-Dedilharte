package com.example.dedilharte;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.dedilharte.data.LessonRepository;
import com.example.dedilharte.model.Lesson;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LessonRepositoryTest {

    @Test
    public void catalogContainsSevenLessonsPerLevel() {
        assertEquals(7, LessonRepository.getLessons(LessonRepository.BEGINNER).size());
        assertEquals(7, LessonRepository.getLessons(LessonRepository.INTERMEDIATE).size());
        assertEquals(14, LessonRepository.getAll().size());
    }

    @Test
    public void everyLessonHasUniqueIdAndValidPattern() {
        Set<String> ids = new HashSet<>();

        for (Lesson lesson : LessonRepository.getAll()) {
            assertTrue(ids.add(lesson.getId()));
            assertFalse(lesson.getTitle().trim().isEmpty());
            assertFalse(lesson.getObjective().trim().isEmpty());
            assertTrue(lesson.getBpm() >= 40);
            assertTrue(lesson.getBpm() <= 140);
            assertTrue(lesson.getPattern().length > 0);

            for (int stringNumber : lesson.getPattern()) {
                assertTrue(stringNumber >= 1);
                assertTrue(stringNumber <= 6);
            }
        }
    }

    @Test
    public void lessonsAreOrderedInsideEachLevel() {
        verifyOrder(LessonRepository.getLessons(LessonRepository.BEGINNER));
        verifyOrder(LessonRepository.getLessons(LessonRepository.INTERMEDIATE));
    }

    @Test
    public void lessonCanBeFoundById() {
        Lesson lesson = LessonRepository.getById("ini_1");
        assertNotNull(lesson);
        assertEquals("Conhecendo as cordas", lesson.getTitle());
    }

    private void verifyOrder(List<Lesson> lessons) {
        for (int index = 0; index < lessons.size(); index++) {
            assertEquals(index + 1, lessons.get(index).getOrder());
        }
    }
}
