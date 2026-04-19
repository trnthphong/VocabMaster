package com.example.vocabmaster.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.util.Date;
import java.util.List;

public class User {
    private String uid;
    private String name;
    private String email;
    private String avatar;
    private String avatarUrl;
    private String role;
    private String shortId;
    
    private boolean premium; 
    
    private Object premiumUntil; // Dùng Object để nhận cả Long và Timestamp
    private List<String> savedSets;
    private Object createdAt; // Dùng Object để nhận cả Long và Timestamp

    // Gamification
    private long xp;
    private int hearts;
    private Timestamp lastHeartRegen;
    private int streak;
    private int longestStreak;
    private Timestamp lastActive;
    private String timezone;
    private int dailyGoal;
    private int friendsCount;

    // Settings
    private boolean darkMode;
    private String language;
    private boolean notificationsEnabled;
    private String currentUnitTitle;
    
    private String activeCourseId;
    private String proficiencyLevel;

    // Stripe
    private String stripeCustomerId;

    public User() {
        this.hearts = 5;
    }

    public boolean isActivePremium() {
        if (premium) return true;
        Timestamp until = getPremiumUntilTimestamp();
        if (until == null) return false;
        return until.toDate().getTime() > System.currentTimeMillis();
    }

    // Helper to handle both Long and Timestamp from Firestore
    private Timestamp convertToTimestamp(Object value) {
        if (value instanceof Timestamp) {
            return (Timestamp) value;
        } else if (value instanceof Long) {
            return new Timestamp(new Date((Long) value));
        } else if (value instanceof com.google.firebase.firestore.FieldValue) {
            return Timestamp.now();
        }
        return null;
    }

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
    public String getShortId() { return shortId; }
    public void setShortId(String shortId) { this.shortId = shortId; }

    @PropertyName("isPremium")
    public boolean isPremium() { return premium; }
    @PropertyName("isPremium")
    public void setPremium(boolean premium) { this.premium = premium; }

    @PropertyName("premium")
    public boolean getPremium() { return premium; }
    @PropertyName("premium")
    public void setPremiumActual(boolean premium) { this.premium = premium; }

    public Object getPremiumUntil() { return premiumUntil; }
    public void setPremiumUntil(Object premiumUntil) { this.premiumUntil = premiumUntil; }
    
    public Timestamp getPremiumUntilTimestamp() {
        return convertToTimestamp(premiumUntil);
    }

    public List<String> getSavedSets() { return savedSets; }
    public void setSavedSets(List<String> savedSets) { this.savedSets = savedSets; }

    public Object getCreatedAt() { return createdAt; }
    public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getCreatedAtTimestamp() {
        return convertToTimestamp(createdAt);
    }

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
    public int getFriendsCount() { return friendsCount; }
    public void setFriendsCount(int friendsCount) { this.friendsCount = friendsCount; }
    public boolean isDarkMode() { return darkMode; }
    public void setDarkMode(boolean darkMode) { this.darkMode = darkMode; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public String getCurrentUnitTitle() { return currentUnitTitle; }
    public void setCurrentUnitTitle(String currentUnitTitle) { this.currentUnitTitle = currentUnitTitle; }
    public String getActiveCourseId() { return activeCourseId; }
    public void setActiveCourseId(String activeCourseId) { this.activeCourseId = activeCourseId; }
    public String getProficiencyLevel() { return proficiencyLevel; }
    public void setProficiencyLevel(String proficiencyLevel) { this.proficiencyLevel = proficiencyLevel; }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
}
