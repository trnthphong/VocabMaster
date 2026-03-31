package com.example.vocabmaster.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.vocabmaster.data.local.Converters;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity(tableName = "courses")
@TypeConverters(Converters.class)
public class Course {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String firestoreId;
    private String title;
    private String description;
    private String theme;
    private String creatorId;
    private boolean isPublic;
    private int flashcardCount;
    private int level; // Cấp độ của khóa học (legacy)

    // New fields from schema
    private int targetLanguageId;
    private int sourceLanguageId;
    private int dailyTimeMinutes;
    private List<Integer> favoriteTopics;
    private String proficiencyLevel; // beginner, elementary, intermediate, advanced, proficient
    private String learningGoal;
    private Date startDate;
    private Date lastActiveDate;
    private int streakDays;
    private double progressPercentage;
    private String status; // active, paused, completed, archived
    private Date createdAt;
    private Date updatedAt;

    public Course() {
        this.level = 1;
        this.favoriteTopics = new ArrayList<>();
        this.startDate = new Date();
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.streakDays = 0;
        this.progressPercentage = 0.0;
        this.status = "active";
        this.sourceLanguageId = 1; // Default to Vietnamese (as per schema requirement)
    }

    // Constructor cũ để tương thích
    public Course(String title, String description, String creatorId, boolean isPublic) {
        this();
        this.title = title;
        this.description = description;
        this.creatorId = creatorId;
        this.isPublic = isPublic;
    }

    public Course(String title, String description, String theme, String creatorId, boolean isPublic) {
        this();
        this.title = title;
        this.description = description;
        this.theme = theme;
        this.creatorId = creatorId;
        this.isPublic = isPublic;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public int getFlashcardCount() { return flashcardCount; }
    public void setFlashcardCount(int flashcardCount) { this.flashcardCount = flashcardCount; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getTargetLanguageId() { return targetLanguageId; }
    public void setTargetLanguageId(int targetLanguageId) { this.targetLanguageId = targetLanguageId; }
    public int getSourceLanguageId() { return sourceLanguageId; }
    public void setSourceLanguageId(int sourceLanguageId) { this.sourceLanguageId = sourceLanguageId; }
    public int getDailyTimeMinutes() { return dailyTimeMinutes; }
    public void setDailyTimeMinutes(int dailyTimeMinutes) { this.dailyTimeMinutes = dailyTimeMinutes; }
    public List<Integer> getFavoriteTopics() { return favoriteTopics; }
    public void setFavoriteTopics(List<Integer> favoriteTopics) { this.favoriteTopics = favoriteTopics; }
    public String getProficiencyLevel() { return proficiencyLevel; }
    public void setProficiencyLevel(String proficiencyLevel) { this.proficiencyLevel = proficiencyLevel; }
    public String getLearningGoal() { return learningGoal; }
    public void setLearningGoal(String learningGoal) { this.learningGoal = learningGoal; }
    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }
    public Date getLastActiveDate() { return lastActiveDate; }
    public void setLastActiveDate(Date lastActiveDate) { this.lastActiveDate = lastActiveDate; }
    public int getStreakDays() { return streakDays; }
    public void setStreakDays(int streakDays) { this.streakDays = streakDays; }
    public double getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(double progressPercentage) { this.progressPercentage = progressPercentage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
