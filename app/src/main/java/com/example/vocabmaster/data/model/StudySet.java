package com.example.vocabmaster.data.model;

import com.google.firebase.Timestamp;

public class StudySet {
    private String setId;
    private String title;
    private String description;
    private String creatorId;
    private String category;
    private boolean isPublic;
    private boolean isAiGenerated;
    private String originalSetId;
    private int cardCount;
    private String shareCode;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public StudySet() {} // Required for Firestore

    // Getters and Setters
    public String getSetId() { return setId; }
    public void setSetId(String setId) { this.setId = setId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public boolean isAiGenerated() { return isAiGenerated; }
    public void setAiGenerated(boolean aiGenerated) { isAiGenerated = aiGenerated; }
    public String getOriginalSetId() { return originalSetId; }
    public void setOriginalSetId(String originalSetId) { this.originalSetId = originalSetId; }
    public int getCardCount() { return cardCount; }
    public void setCardCount(int cardCount) { this.cardCount = cardCount; }
    public String getShareCode() { return shareCode; }
    public void setShareCode(String shareCode) { this.shareCode = shareCode; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}