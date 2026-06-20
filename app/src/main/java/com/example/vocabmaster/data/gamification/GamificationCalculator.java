package com.example.vocabmaster.data.gamification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class GamificationCalculator {

    private GamificationCalculator() {}

    public static HeartRecoveryResult calculateHeartRecovery(
            int currentHearts,
            Long lastHeartRegenMillis,
            long nowMillis
    ) {
        int normalizedHearts = Math.max(0, Math.min(currentHearts, GamificationConstants.MAX_HEARTS));
        boolean changed = normalizedHearts != currentHearts;

        if (normalizedHearts >= GamificationConstants.MAX_HEARTS) {
            return new HeartRecoveryResult(
                    GamificationConstants.MAX_HEARTS,
                    lastHeartRegenMillis != null ? lastHeartRegenMillis : nowMillis,
                    -1L,
                    0,
                    changed || lastHeartRegenMillis == null
            );
        }

        if (lastHeartRegenMillis == null || lastHeartRegenMillis <= 0L) {
            return new HeartRecoveryResult(
                    normalizedHearts,
                    nowMillis,
                    nowMillis + GamificationConstants.HEART_REGEN_INTERVAL_MILLIS,
                    0,
                    true
            );
        }

        long elapsed = Math.max(0L, nowMillis - lastHeartRegenMillis);
        long heartsRecovered = elapsed / GamificationConstants.HEART_REGEN_INTERVAL_MILLIS;
        if (heartsRecovered <= 0L) {
            return new HeartRecoveryResult(
                    normalizedHearts,
                    lastHeartRegenMillis,
                    lastHeartRegenMillis + GamificationConstants.HEART_REGEN_INTERVAL_MILLIS,
                    0,
                    changed
            );
        }

        int recoveredHearts = (int) Math.min(
                GamificationConstants.MAX_HEARTS,
                normalizedHearts + heartsRecovered
        );
        long consumedIntervals = recoveredHearts - normalizedHearts;
        long updatedLastRegen = recoveredHearts >= GamificationConstants.MAX_HEARTS
                ? nowMillis
                : lastHeartRegenMillis
                + consumedIntervals * GamificationConstants.HEART_REGEN_INTERVAL_MILLIS;
        long nextRegenAt = recoveredHearts >= GamificationConstants.MAX_HEARTS
                ? -1L
                : updatedLastRegen + GamificationConstants.HEART_REGEN_INTERVAL_MILLIS;

        return new HeartRecoveryResult(
                recoveredHearts,
                updatedLastRegen,
                nextRegenAt,
                recoveredHearts - normalizedHearts,
                true
        );
    }

    public static long nextHeartAtMillis(int hearts, Long lastHeartRegenMillis, long nowMillis) {
        return calculateHeartRecovery(hearts, lastHeartRegenMillis, nowMillis).getNextHeartAtMillis();
    }

    public static StreakResult calculateStreak(
            int currentStreak,
            int currentLongestStreak,
            Long lastActiveMillis,
            long nowMillis,
            String timezoneId
    ) {
        ZoneId zoneId = resolveZone(timezoneId);
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate();

        int nextStreak;
        if (lastActiveMillis == null || lastActiveMillis <= 0L) {
            nextStreak = 1;
        } else {
            LocalDate lastActive = Instant.ofEpochMilli(lastActiveMillis).atZone(zoneId).toLocalDate();
            if (lastActive.equals(today)) {
                nextStreak = Math.max(1, currentStreak);
            } else if (lastActive.plusDays(1).equals(today)) {
                nextStreak = Math.max(0, currentStreak) + 1;
            } else {
                nextStreak = 1;
            }
        }

        int nextLongest = Math.max(Math.max(0, currentLongestStreak), nextStreak);
        return new StreakResult(nextStreak, nextLongest);
    }

    private static ZoneId resolveZone(String timezoneId) {
        if (timezoneId != null && !timezoneId.trim().isEmpty()) {
            try {
                return ZoneId.of(timezoneId);
            } catch (Exception ignored) {
                // Fall through to the device default.
            }
        }
        return ZoneId.systemDefault();
    }

    public static final class HeartRecoveryResult {
        private final int hearts;
        private final long lastHeartRegenMillis;
        private final long nextHeartAtMillis;
        private final long heartsRecovered;
        private final boolean changed;

        public HeartRecoveryResult(
                int hearts,
                long lastHeartRegenMillis,
                long nextHeartAtMillis,
                long heartsRecovered,
                boolean changed
        ) {
            this.hearts = hearts;
            this.lastHeartRegenMillis = lastHeartRegenMillis;
            this.nextHeartAtMillis = nextHeartAtMillis;
            this.heartsRecovered = heartsRecovered;
            this.changed = changed;
        }

        public int getHearts() { return hearts; }
        public long getLastHeartRegenMillis() { return lastHeartRegenMillis; }
        public long getNextHeartAtMillis() { return nextHeartAtMillis; }
        public long getHeartsRecovered() { return heartsRecovered; }
        public boolean isChanged() { return changed; }
    }

    public static final class StreakResult {
        private final int streak;
        private final int longestStreak;

        public StreakResult(int streak, int longestStreak) {
            this.streak = streak;
            this.longestStreak = longestStreak;
        }

        public int getStreak() { return streak; }
        public int getLongestStreak() { return longestStreak; }
    }
}
