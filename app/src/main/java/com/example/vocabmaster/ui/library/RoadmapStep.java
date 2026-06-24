package com.example.vocabmaster.ui.library;

import com.example.vocabmaster.data.gamification.GamificationConstants;

public class RoadmapStep {
    private String id;
    private String level; // VD: Unit 1
    private String title; // VD: Warm-up
    private String description;
    private int iconRes;
    private boolean isLocked;
    private boolean isCompleted;
    private String type;
    private int xpPoints;
    private int progressPercent;
    private int completedChallenges;
    private int totalChallenges;
    private int cupsEarned;

    public RoadmapStep(String id, String level, String title, String description, int iconRes, boolean isLocked, boolean isCompleted, String type) {
        this(id, level, title, description, iconRes, isLocked, isCompleted, type, GamificationConstants.DEFAULT_LESSON_XP);
    }

    public RoadmapStep(String id, String level, String title, String description, int iconRes, boolean isLocked, boolean isCompleted, String type, int xpPoints) {
        this.id = id;
        this.level = level;
        this.title = title;
        this.description = description;
        this.iconRes = iconRes;
        this.isLocked = isLocked;
        this.isCompleted = isCompleted;
        this.type = type;
        this.xpPoints = xpPoints;
    }

    public RoadmapStep(String id, String level, String title, String description, int iconRes,
                       boolean isLocked, boolean isCompleted, String type, int xpPoints,
                       int progressPercent, int completedChallenges, int totalChallenges,
                       int cupsEarned) {
        this(id, level, title, description, iconRes, isLocked, isCompleted, type, xpPoints);
        this.progressPercent = Math.max(0, Math.min(100, progressPercent));
        this.completedChallenges = Math.max(0, completedChallenges);
        this.totalChallenges = Math.max(1, totalChallenges);
        this.cupsEarned = Math.max(0, Math.min(3, cupsEarned));
    }

    public String getId() { return id; }
    public String getLevel() { return level; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getIconRes() { return iconRes; }
    public boolean isLocked() { return isLocked; }
    public boolean isCompleted() { return isCompleted; }
    public String getType() { return type; }
    public int getXpPoints() { return xpPoints; }
    public int getProgressPercent() { return progressPercent; }
    public int getCompletedChallenges() { return completedChallenges; }
    public int getTotalChallenges() { return totalChallenges; }
    public int getCupsEarned() { return cupsEarned; }
}
