package com.example.vocabmaster.data.model;

import com.google.firebase.Timestamp;

public class UserProgress {
    private String progressId; // userId_cardId
    private String userId;
    private String cardId;
    private String setId;
    
    // SRS_Data
    private String status; // "learning", "mastered", "review"
    private int interval;
    private float easeFactor;
    private Timestamp nextReview;
    private Timestamp lastReviewed;

    public UserProgress() {}

    // Getters and Setters
    public String getProgressId() { return progressId; }
    public void setProgressId(String progressId) { this.progressId = progressId; }
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
    public Timestamp getNextReview() { return nextReview; }
    public void setNextReview(Timestamp nextReview) { this.nextReview = nextReview; }
    public Timestamp getLastReviewed() { return lastReviewed; }
    public void setLastReviewed(Timestamp lastReviewed) { this.lastReviewed = lastReviewed; }
}