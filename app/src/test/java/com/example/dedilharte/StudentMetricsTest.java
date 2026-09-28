package com.example.dedilharte;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.dedilharte.data.StudentMetrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class StudentMetricsTest {

    @Test
    public void weeklyStatusClassifiesRequestedCases() {
        assertEquals(StudentMetrics.WeeklyStatus.EXEMPLAR, StudentMetrics.weeklyStatus(5, 5));
        assertEquals(StudentMetrics.WeeklyStatus.EFFORT, StudentMetrics.weeklyStatus(5, 3));
        assertEquals(StudentMetrics.WeeklyStatus.BACK_ROW, StudentMetrics.weeklyStatus(5, 2));
    }

    @Test
    public void levelScoreRequiresBeginnerTriviaGateForIntermediate() {
        float highScoreWithoutBeginnerGate = StudentMetrics.levelScore(.60f, 1f, 10, 10, 1f);
        assertTrue(highScoreWithoutBeginnerGate >= StudentMetrics.INTERMEDIATE_SCORE_THRESHOLD);
        assertEquals("Iniciante", StudentMetrics.calculatedLevel(highScoreWithoutBeginnerGate, .60f));

        float eligibleScore = StudentMetrics.levelScore(.70f, .50f, 10, 10, 1f);
        assertTrue(eligibleScore >= StudentMetrics.INTERMEDIATE_SCORE_THRESHOLD);
        assertEquals("Intermediário", StudentMetrics.calculatedLevel(eligibleScore, .70f));
    }

    @Test
    public void progressStoreDoesNotPreCompleteFirstLesson() throws Exception {
        File source = new File("src/main/java/com/example/dedilharte/data/ProgressStore.java");
        String code = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);

        assertTrue(code.contains("initializePrototypeState"));
        assertTrue(code.contains("restoreInitialState"));
        assertEquals(-1, code.indexOf("putBoolean(completedKey(\"ini_1\"), true)"));
    }
}
