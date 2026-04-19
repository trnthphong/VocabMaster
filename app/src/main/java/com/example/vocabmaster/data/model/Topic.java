package com.example.vocabmaster.data.model;

import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

@IgnoreExtraProperties
public class Topic {
    private String id;
    private String name;
    private String description;
    private String imageUrl;
    private int wordCount;
    private int order; // Trường order dùng để hiển thị số từ
    private boolean isDownloaded;

    public Topic() {}

    public Topic(String id, String name, int wordCount) {
        this.id = id;
        this.name = name;
        this.wordCount = wordCount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @PropertyName("imageUrl")
    public String getImageUrl() { return imageUrl; }
    @PropertyName("imageUrl")
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    @PropertyName("word_count")
    public int getWordCount() { return wordCount; }
    @PropertyName("word_count")
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public boolean isDownloaded() { return isDownloaded; }
    public void setDownloaded(boolean downloaded) { isDownloaded = downloaded; }
}
