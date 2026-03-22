package com.example.vocabmaster.data.model;

import com.google.firebase.Timestamp;

public class Relationship {
    private String relationshipId; // followerId_followingId
    private String followerId;
    private String followingId;
    private Timestamp createdAt;

    public Relationship() {}

    public Relationship(String followerId, String followingId) {
        this.relationshipId = followerId + "_" + followingId;
        this.followerId = followerId;
        this.followingId = followingId;
        this.createdAt = Timestamp.now();
    }

    // Getters and Setters
    public String getRelationshipId() { return relationshipId; }
    public void setRelationshipId(String relationshipId) { this.relationshipId = relationshipId; }
    public String getFollowerId() { return followerId; }
    public void setFollowerId(String followerId) { this.followerId = followerId; }
    public String getFollowingId() { return followingId; }
    public void setFollowingId(String followingId) { this.followingId = followingId; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}