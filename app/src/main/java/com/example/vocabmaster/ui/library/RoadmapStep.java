package com.example.vocabmaster.ui.library;

public class RoadmapStep {
    private String level;
    private String title;
    private String description;
    private int iconRes;

    public RoadmapStep(String level, String title, String description, int iconRes) {
        this.level = level;
        this.title = title;
        this.description = description;
        this.iconRes = iconRes;
    }

    public String getLevel() { return level; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getIconRes() { return iconRes; }
}
