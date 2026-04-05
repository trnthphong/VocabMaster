package com.example.vocabmaster.data.model;

public class ChallengeProgress {
    private String id; // userId_challengeId
    private String userId;
    private String challengeId;
    private boolean completed;

    public ChallengeProgress() {}

    public ChallengeProgress(String userId, String challengeId, boolean completed) {
        this.userId = userId;
        this.challengeId = challengeId;
        this.completed = completed;
        this.id = userId + "_" + challengeId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
