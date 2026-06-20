package com.example.vocabmaster.data.srs;

import com.example.vocabmaster.data.model.UserProgress;
import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class SpacedRepetitionCalculator {
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

    public enum Rating {
        HARD,
        MEDIUM,
        EASY
    }

    public static final class ReviewResult {
        private final int interval;
        private final float easeFactor;
        private final long nextReviewMillis;
        private final String status;

        private ReviewResult(int interval, float easeFactor, long nextReviewMillis, String status) {
            this.interval = interval;
            this.easeFactor = easeFactor;
            this.nextReviewMillis = nextReviewMillis;
            this.status = status;
        }

        public int getInterval() {
            return interval;
        }

        public float getEaseFactor() {
            return easeFactor;
        }

        public long getNextReviewMillis() {
            return nextReviewMillis;
        }

        public String getStatus() {
            return status;
        }
    }

    private SpacedRepetitionCalculator() {
    }

    public static ReviewResult calculate(UserProgress previous, Rating rating, long nowMillis) {
        int previousInterval = previous != null ? Math.max(0, previous.getInterval()) : 0;
        float previousEase = previous != null && previous.getEaseFactor() > 0f
                ? previous.getEaseFactor()
                : SpacedRepetitionConstants.DEFAULT_EASE;

        switch (rating) {
            case HARD:
                float hardEase = Math.max(SpacedRepetitionConstants.MIN_EASE, previousEase - 0.2f);
                return new ReviewResult(
                        0,
                        hardEase,
                        nowMillis + TimeUnit.MINUTES.toMillis(SpacedRepetitionConstants.HARD_RELEARN_MINUTES),
                        "learning"
                );
            case MEDIUM:
                int mediumInterval = previousInterval == 0
                        ? 1
                        : Math.max(1, Math.round(previousInterval * previousEase));
                return new ReviewResult(
                        mediumInterval,
                        previousEase,
                        nowMillis + mediumInterval * DAY_MILLIS,
                        "review"
                );
            case EASY:
            default:
                float easyEase = previousEase + 0.15f;
                int easyInterval = previousInterval == 0
                        ? 3
                        : Math.max(1, Math.round(previousInterval * (previousEase + 0.3f)));
                return new ReviewResult(
                        easyInterval,
                        easyEase,
                        nowMillis + easyInterval * DAY_MILLIS,
                        easyInterval >= 7 ? "mastered" : "review"
                );
        }
    }

    public static UserProgress applyResult(UserProgress target, ReviewResult result) {
        target.setInterval(result.getInterval());
        target.setEaseFactor(result.getEaseFactor());
        target.setStatus(result.getStatus());
        target.setNextReview(new Timestamp(new Date(result.getNextReviewMillis())));
        return target;
    }
}
