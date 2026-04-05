package com.example.vocabmaster.data.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "flashcards", 
        indices = {@Index(value = {"firestoreId"}, unique = true)})
public class Flashcard {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String firestoreId;
    private int courseId;
    private String term;
    private String definition;
    private String example;
    private String imageUrl;
    private String audioUrl;
    private String phonetic; // Added phonetic field
    private String tag;
    private int orderIndex;
    
    private long lastReviewTime;
    private long nextReviewAt;
    private int interval;

    public Flashcard() {}

    public Flashcard(String term, String definition) {
        this.term = term;
        this.definition = definition;
    }

    public Flashcard(String term, String definition, int interval) {
        this.term = term;
        this.definition = definition;
        this.interval = interval;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public String getPhonetic() { return phonetic; }
    public void setPhonetic(String phonetic) { this.phonetic = phonetic; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public long getLastReviewTime() { return lastReviewTime; }
    public void setLastReviewTime(long lastReviewTime) { this.lastReviewTime = lastReviewTime; }

    public long getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(long nextReviewAt) { this.nextReviewAt = nextReviewAt; }

    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = interval; }
}
