package com.example.vocabmaster.data.model;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Comment {
    private String id;
    private String userId;
    private String userName;
    private String userAvatar;
    private String content;
    @ServerTimestamp
    private Date createdAt;

    public Comment() {}

    public Comment(String userId, String userName, String userAvatar, String content) {
        this.userId = userId;
        this.userName = userName;
        this.userAvatar = userAvatar;
        this.content = content;
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
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
