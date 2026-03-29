package com.example.vocabmaster.ui.library;

public class RoadmapStep {
    private String id;
    private String level; // VD: Unit 1
    private String title; // VD: Warm-up
    private String description;
    private int iconRes;
    private boolean isLocked;
    private boolean isCompleted;
    private String type;

    public RoadmapStep(String id, String level, String title, String description, int iconRes, boolean isLocked, boolean isCompleted, String type) {
        this.id = id;
        this.level = level;
        this.title = title;
        this.description = description;
        this.iconRes = iconRes;
        this.isLocked = isLocked;
        this.isCompleted = isCompleted;
        this.type = type;
    }

    public String getId() { return id; }
    public String getLevel() { return level; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getIconRes() { return iconRes; }
    public boolean isLocked() { return isLocked; }
    public boolean isCompleted() { return isCompleted; }
    public String getType() { return type; }
}
