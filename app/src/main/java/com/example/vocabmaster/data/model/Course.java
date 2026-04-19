package com.example.vocabmaster.data.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.vocabmaster.data.local.Converters;
import com.google.firebase.firestore.PropertyName;

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
    private String imageUrl;
    private String theme;
    private String creatorId;
    private boolean isPublic;
    private String language; 
    private int flashcardCount;
    private int level;
    
    @Ignore
    private boolean isTopic; // To distinguish between Course and Topic in Library

    private int targetLanguageId;
    private int sourceLanguageId;
    private int dailyTimeMinutes;
    private List<String> favoriteTopics; 
    private String proficiencyLevel; 
    private String learningGoal;
    private Date startDate;
    private Date lastActiveDate;
    private int streakDays;
    private double progressPercentage;
    private String status; 
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
        this.sourceLanguageId = 1;
        this.isTopic = false;
    }

    public boolean isTopic() { return isTopic; }
    public void setTopic(boolean topic) { isTopic = topic; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    
    @PropertyName("isPublic")
    public boolean isPublic() { return isPublic; }
    
    @PropertyName("isPublic")
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    @PropertyName("public")
    public boolean isPublicLegacy() { return isPublic; }
    
    @PropertyName("public")
    public void setPublicLegacy(boolean isPublic) { this.isPublic = isPublic; }

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
    public List<String> getFavoriteTopics() { return favoriteTopics; }
    public void setFavoriteTopics(List<String> favoriteTopics) { this.favoriteTopics = favoriteTopics; }
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
