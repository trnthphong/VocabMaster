package com.example.vocabmaster.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.vocabmaster.data.local.Converters;

import java.util.List;
import java.util.Map;

@Entity(tableName = "learning_profiles")
@TypeConverters(Converters.class)
public class LearningProfile {
    @PrimaryKey
    @NonNull
    private String userId;
    private String profileId;
    private String cefrLevel; // A1, A2, B1, B2, C1, C2
    private List<String> weakPoints;
    private List<String> topicsOfInterest;
    private String learningStyle;
    private Map<String, Double> skillScores; // Reading, Listening, Speaking, Writing
    private int dailyCommitmentMinutes;
    private String suggestedCefr;
    private boolean isActive;

    public LearningProfile() {
        this.userId = ""; // Initialize non-null field
        this.isActive = true;
    }

    public LearningProfile(String profileId, String cefrLevel) {
        this();
        this.profileId = profileId;
        this.cefrLevel = cefrLevel;
    }

    @NonNull
    public String getUserId() { return userId; }
    public void setUserId(@NonNull String userId) { this.userId = userId; }
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
    public String getCefrLevel() { return cefrLevel; }
    public void setCefrLevel(String cefrLevel) { this.cefrLevel = cefrLevel; }
    public List<String> getWeakPoints() { return weakPoints; }
    public void setWeakPoints(List<String> weakPoints) { this.weakPoints = weakPoints; }
    public List<String> getTopicsOfInterest() { return topicsOfInterest; }
    public void setTopicsOfInterest(List<String> topicsOfInterest) { this.topicsOfInterest = topicsOfInterest; }
    public String getLearningStyle() { return learningStyle; }
    public void setLearningStyle(String learningStyle) { this.learningStyle = learningStyle; }
    public Map<String, Double> getSkillScores() { return skillScores; }
    public void setSkillScores(Map<String, Double> skillScores) { this.skillScores = skillScores; }
    public int getDailyCommitmentMinutes() { return dailyCommitmentMinutes; }
    public void setDailyCommitmentMinutes(int dailyCommitmentMinutes) { this.dailyCommitmentMinutes = dailyCommitmentMinutes; }
    public String getSuggestedCefr() { return suggestedCefr; }
    public void setSuggestedCefr(String suggestedCefr) { this.suggestedCefr = suggestedCefr; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
