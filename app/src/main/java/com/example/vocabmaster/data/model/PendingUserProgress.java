package com.example.vocabmaster.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_user_progress")
public class PendingUserProgress {
    @PrimaryKey
    @NonNull
    private String progressId = "";
    private String userId;
    private String cardId;
    private String setId;
    private String status;
    private int interval;
    private float easeFactor;
    private long nextReviewMillis;
    private long lastReviewedMillis;
    private long createdAtMillis;
    private int attemptCount;

    public PendingUserProgress() {}

    @NonNull
    public String getProgressId() { return progressId; }
    public void setProgressId(@NonNull String progressId) { this.progressId = progressId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getSetId() { return setId; }
    public void setSetId(String setId) { this.setId = setId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = interval; }
    public float getEaseFactor() { return easeFactor; }
    public void setEaseFactor(float easeFactor) { this.easeFactor = easeFactor; }
    public long getNextReviewMillis() { return nextReviewMillis; }
    public void setNextReviewMillis(long nextReviewMillis) { this.nextReviewMillis = nextReviewMillis; }
    public long getLastReviewedMillis() { return lastReviewedMillis; }
    public void setLastReviewedMillis(long lastReviewedMillis) { this.lastReviewedMillis = lastReviewedMillis; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
}
