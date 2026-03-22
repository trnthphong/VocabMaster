package com.example.vocabmaster.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "flashcards")
public class Flashcard {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String firestoreId;
    private String term;
    private String definition;
    private String imageUrl;
    private int courseId;
    private long lastReviewTime;
    private long nextReviewAt;
    private int interval; // for Spaced Repetition

    public Flashcard() {
    }

    public Flashcard(String term, String definition, int courseId) {
        this.term = term;
        this.definition = definition;
        this.courseId = courseId;
        this.lastReviewTime = System.currentTimeMillis();
        this.nextReviewAt = this.lastReviewTime;
        this.interval = 1;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }
    public long getLastReviewTime() { return lastReviewTime; }
    public void setLastReviewTime(long lastReviewTime) { this.lastReviewTime = lastReviewTime; }
    public long getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(long nextReviewAt) { this.nextReviewAt = nextReviewAt; }
    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = interval; }
}