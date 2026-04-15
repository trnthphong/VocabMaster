package com.example.vocabmaster.data.model;

import com.google.firebase.Timestamp;

public class Notification {
    private String id;
    private String type; // "follow", "system", etc.
    private String title;
    private String message;
    private String fromUserId;
    private String fromUserName;
    private String fromUserAvatar;
    private Timestamp timestamp;
    private boolean read;

    public Notification() {
        this.timestamp = Timestamp.now();
        this.read = false;
    }

    public Notification(String type, String title, String message, String fromUserId, String fromUserName) {
        this();
        this.type = type;
        this.title = title;
        this.message = message;
        this.fromUserId = fromUserId;
        this.fromUserName = fromUserName;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
    public String getFromUserName() { return fromUserName; }
    public void setFromUserName(String fromUserName) { this.fromUserName = fromUserName; }
    public String getFromUserAvatar() { return fromUserAvatar; }
    public void setFromUserAvatar(String fromUserAvatar) { this.fromUserAvatar = fromUserAvatar; }
    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}