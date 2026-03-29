package com.example.vocabmaster.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "courses")
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
    private int level; // Cấp độ của khóa học

    public Course() {
        this.level = 1;
    }

    // Constructor cũ để tương thích với LibraryFragment
    public Course(String title, String description, String creatorId, boolean isPublic) {
        this.title = title;
        this.description = description;
        this.creatorId = creatorId;
        this.isPublic = isPublic;
        this.flashcardCount = 0;
        this.level = 1;
    }

    public Course(String title, String description, String theme, String creatorId, boolean isPublic) {
        this.title = title;
        this.description = description;
        this.theme = theme;
        this.creatorId = creatorId;
        this.isPublic = isPublic;
        this.flashcardCount = 0;
        this.level = 1;
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
}