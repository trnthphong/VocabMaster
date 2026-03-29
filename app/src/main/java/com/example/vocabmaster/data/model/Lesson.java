package com.example.vocabmaster.data.model;

import java.util.ArrayList;
import java.util.List;

public class Lesson {
    private String lessonId;
    private String unitId;
    private String title;
    private String type; // vocabulary, grammar, listening, speaking, reading, quiz
    private int durationMinutes;
    private int xpPoints;
    private boolean isCompleted;
    private List<String> vocabWords; // Danh sách các từ vựng trong bài học này
    private int orderNum;

    public Lesson() {
        this.xpPoints = 10;
        this.isCompleted = false;
        this.vocabWords = new ArrayList<>();
    }

    public Lesson(String title, String type, int durationMinutes, int xpPoints) {
        this();
        this.title = title;
        this.type = type;
        this.durationMinutes = durationMinutes;
        this.xpPoints = xpPoints;
    }

    public String getLessonId() { return lessonId; }
    public void setLessonId(String lessonId) { this.lessonId = lessonId; }
    public String getUnitId() { return unitId; }
    public void setUnitId(String unitId) { this.unitId = unitId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public int getXpPoints() { return xpPoints; }
    public void setXpPoints(int xpPoints) { this.xpPoints = xpPoints; }
    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
    public List<String> getVocabWords() { return vocabWords; }
    public void setVocabWords(List<String> vocabWords) { this.vocabWords = vocabWords; }
    public int getOrderNum() { return orderNum; }
    public void setOrderNum(int orderNum) { this.orderNum = orderNum; }
}
