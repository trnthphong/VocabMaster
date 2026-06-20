package com.example.vocabmaster.data.gamification;

import java.util.concurrent.TimeUnit;

public final class GamificationConstants {
    public static final int MAX_HEARTS = 5;
    public static final int HEART_COST_WRONG = 1;
    public static final int HEART_REGEN_MINUTES = 15;
    public static final long HEART_REGEN_INTERVAL_MILLIS =
            TimeUnit.MINUTES.toMillis(HEART_REGEN_MINUTES);
    public static final int DEFAULT_LESSON_XP = 10;

    private GamificationConstants() {}
}
