package com.example.vocabmaster.data.model;

import com.google.firebase.Timestamp;
import java.util.List;
import java.util.Map;

public class User {
    private String uid;
    private String name;
    private String email;
    private String avatar; // Matches Firestore field
    private String avatarUrl;
    private String role;
    private boolean isPremium;
    private Timestamp premiumUntil;
    private List<String> savedSets;
    private Timestamp createdAt;

    // Gamification
    private long xp;
    private int hearts;
    private Timestamp lastHeartRegen;
    private int streak;
    private int longestStreak;
    private Timestamp lastActive;
    private String timezone;
    private int dailyGoal;

    // Settings
    private boolean darkMode;
    private String language;
    private boolean notificationsEnabled;
    private String currentUnitTitle;

    public User() {} // Required for Firestore

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }
    public Timestamp getPremiumUntil() { return premiumUntil; }
    public void setPremiumUntil(Timestamp premiumUntil) { this.premiumUntil = premiumUntil; }
    public List<String> getSavedSets() { return savedSets; }
    public void setSavedSets(List<String> savedSets) { this.savedSets = savedSets; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public long getXp() { return xp; }
    public void setXp(long xp) { this.xp = xp; }
    public int getHearts() { return hearts; }
    public void setHearts(int hearts) { this.hearts = hearts; }
    public Timestamp getLastHeartRegen() { return lastHeartRegen; }
    public void setLastHeartRegen(Timestamp lastHeartRegen) { this.lastHeartRegen = lastHeartRegen; }
    public int getStreak() { return streak; }
    public void setStreak(int streak) { this.streak = streak; }
    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }
    public Timestamp getLastActive() { return lastActive; }
    public void setLastActive(Timestamp lastActive) { this.lastActive = lastActive; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public int getDailyGoal() { return dailyGoal; }
    public void setDailyGoal(int dailyGoal) { this.dailyGoal = dailyGoal; }
    public boolean isDarkMode() { return darkMode; }
    public void setDarkMode(boolean darkMode) { this.darkMode = darkMode; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public String getCurrentUnitTitle() { return currentUnitTitle; }
    public void setCurrentUnitTitle(String currentUnitTitle) { this.currentUnitTitle = currentUnitTitle; }
}
