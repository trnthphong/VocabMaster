package com.example.vocabmaster.data.gamification;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

public class GamificationCalculatorTest {
    private static final long INTERVAL = GamificationConstants.HEART_REGEN_INTERVAL_MILLIS;

    @Test
    public void heartRecovery_addsOneHeartAfterFifteenMinutes() {
        GamificationCalculator.HeartRecoveryResult result =
                GamificationCalculator.calculateHeartRecovery(0, 1_000L, 1_000L + INTERVAL);

        assertEquals(1, result.getHearts());
        assertEquals(1, result.getHeartsRecovered());
        assertEquals(1_000L + INTERVAL, result.getLastHeartRegenMillis());
    }

    @Test
    public void heartRecovery_addsTwoHeartsAfterThirtyMinutes() {
        GamificationCalculator.HeartRecoveryResult result =
                GamificationCalculator.calculateHeartRecovery(0, 2_000L, 2_000L + (2 * INTERVAL));

        assertEquals(2, result.getHearts());
        assertEquals(2, result.getHeartsRecovered());
    }

    @Test
    public void heartRecovery_capsAtMaxHearts() {
        GamificationCalculator.HeartRecoveryResult result =
                GamificationCalculator.calculateHeartRecovery(4, 3_000L, 3_000L + (4 * INTERVAL));

        assertEquals(GamificationConstants.MAX_HEARTS, result.getHearts());
        assertEquals(-1L, result.getNextHeartAtMillis());
    }

    @Test
    public void streak_startsAtOneWhenLastActiveIsMissing() {
        long now = millisForDate("2026-06-20");

        GamificationCalculator.StreakResult result =
                GamificationCalculator.calculateStreak(0, 0, null, now, "Asia/Saigon");

        assertEquals(1, result.getStreak());
        assertEquals(1, result.getLongestStreak());
    }

    @Test
    public void streak_keepsValueForSameDay() {
        long now = millisForDate("2026-06-20") + (12 * 60 * 60 * 1000L);
        long earlierToday = now - 60_000L;

        GamificationCalculator.StreakResult result =
                GamificationCalculator.calculateStreak(4, 6, earlierToday, now, "Asia/Saigon");

        assertEquals(4, result.getStreak());
        assertEquals(6, result.getLongestStreak());
    }

    @Test
    public void streak_incrementsFromYesterday() {
        long now = millisForDate("2026-06-20");
        long yesterday = millisForDate("2026-06-19");

        GamificationCalculator.StreakResult result =
                GamificationCalculator.calculateStreak(4, 4, yesterday, now, "Asia/Saigon");

        assertEquals(5, result.getStreak());
        assertEquals(5, result.getLongestStreak());
    }

    @Test
    public void streak_resetsAfterMissedDays() {
        long now = millisForDate("2026-06-20");
        long oldDay = millisForDate("2026-06-17");

        GamificationCalculator.StreakResult result =
                GamificationCalculator.calculateStreak(4, 7, oldDay, now, "Asia/Saigon");

        assertEquals(1, result.getStreak());
        assertEquals(7, result.getLongestStreak());
    }

    private long millisForDate(String isoDate) {
        return LocalDate.parse(isoDate)
                .atStartOfDay(ZoneId.of("Asia/Saigon"))
                .toInstant()
                .toEpochMilli();
    }
}
