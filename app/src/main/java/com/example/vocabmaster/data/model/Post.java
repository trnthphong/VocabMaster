package com.example.vocabmaster.data.model;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Post {
    private String id;
    private String userId;
    private String userName;
    private String userAvatar;
    private String content;
    private String courseId;
    private String courseTitle;
    private int flashcardCount;
    @ServerTimestamp
    private Date createdAt;

    public Post() {}

    public Post(String userId, String userName, String userAvatar, String content, String courseId, String courseTitle, int flashcardCount) {
        this.userId = userId;
        this.userName = userName;
        this.userAvatar = userAvatar;
        this.content = content;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.flashcardCount = flashcardCount;
        this.createdAt = new Date();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String userAvatar) { this.userAvatar = userAvatar; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public int getFlashcardCount() { return flashcardCount; }
    public void setFlashcardCount(int flashcardCount) { this.flashcardCount = flashcardCount; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
