package com.example.vocabmaster.data.srs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.vocabmaster.data.model.UserProgress;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class SpacedRepetitionCalculatorTest {
    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void hardOnNewCardSchedulesTenMinuteRelearn() {
        SpacedRepetitionCalculator.ReviewResult result =
                SpacedRepetitionCalculator.calculate(null, SpacedRepetitionCalculator.Rating.HARD, NOW);

        assertEquals(0, result.getInterval());
        assertEquals(2.3f, result.getEaseFactor(), 0.001f);
        assertEquals(NOW + TimeUnit.MINUTES.toMillis(10), result.getNextReviewMillis());
        assertEquals("learning", result.getStatus());
    }

    @Test
    public void hardNeverDropsEaseBelowMinimum() {
        UserProgress previous = progress(3, 1.31f);

        SpacedRepetitionCalculator.ReviewResult result =
                SpacedRepetitionCalculator.calculate(previous, SpacedRepetitionCalculator.Rating.HARD, NOW);

        assertEquals(SpacedRepetitionConstants.MIN_EASE, result.getEaseFactor(), 0.001f);
    }

    @Test
    public void mediumOnNewCardSchedulesOneDayReview() {
        SpacedRepetitionCalculator.ReviewResult result =
                SpacedRepetitionCalculator.calculate(null, SpacedRepetitionCalculator.Rating.MEDIUM, NOW);

        assertEquals(1, result.getInterval());
        assertEquals(SpacedRepetitionConstants.DEFAULT_EASE, result.getEaseFactor(), 0.001f);
        assertEquals(NOW + TimeUnit.DAYS.toMillis(1), result.getNextReviewMillis());
        assertEquals("review", result.getStatus());
    }

    @Test
    public void mediumMultipliesExistingIntervalByEase() {
        UserProgress previous = progress(4, 2.25f);

        SpacedRepetitionCalculator.ReviewResult result =
                SpacedRepetitionCalculator.calculate(previous, SpacedRepetitionCalculator.Rating.MEDIUM, NOW);

        assertEquals(9, result.getInterval());
        assertEquals(NOW + TimeUnit.DAYS.toMillis(9), result.getNextReviewMillis());
    }

    @Test
    public void easyOnNewCardSchedulesThreeDays() {
        SpacedRepetitionCalculator.ReviewResult result =
                SpacedRepetitionCalculator.calculate(null, SpacedRepetitionCalculator.Rating.EASY, NOW);

        assertEquals(3, result.getInterval());
        assertEquals(2.65f, result.getEaseFactor(), 0.001f);
        assertEquals("review", result.getStatus());
    }

    @Test
    public void easyCanPromoteToMastered() {
        UserProgress previous = progress(3, 2.5f);

        SpacedRepetitionCalculator.ReviewResult result =
                SpacedRepetitionCalculator.calculate(previous, SpacedRepetitionCalculator.Rating.EASY, NOW);

        assertEquals(8, result.getInterval());
        assertEquals("mastered", result.getStatus());
        assertTrue(result.getNextReviewMillis() > NOW);
    }

    private static UserProgress progress(int interval, float easeFactor) {
        UserProgress progress = new UserProgress();
        progress.setInterval(interval);
        progress.setEaseFactor(easeFactor);
        return progress;
    }
}
