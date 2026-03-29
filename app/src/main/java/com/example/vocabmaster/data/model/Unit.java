package com.example.vocabmaster.data.model;

import com.google.firebase.firestore.Exclude;
import java.util.ArrayList;
import java.util.List;

public class Unit {
    private String unitId;
    private String courseId;
    private String title;
    private int orderNum;
    private boolean isUnlocked;
    
    @Exclude
    private List<Lesson> lessons = new ArrayList<>();

    public Unit() {}

    public Unit(String title, int orderNum, boolean isUnlocked) {
        this.title = title;
        this.orderNum = orderNum;
        this.isUnlocked = isUnlocked;
    }

    public String getUnitId() { return unitId; }
    public void setUnitId(String unitId) { this.unitId = unitId; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getOrderNum() { return orderNum; }
    public void setOrderNum(int orderNum) { this.orderNum = orderNum; }
    public boolean isUnlocked() { return isUnlocked; }
    public void setUnlocked(boolean unlocked) { isUnlocked = unlocked; }
    
    @Exclude
    public List<Lesson> getLessons() { return lessons; }
    @Exclude
    public void setLessons(List<Lesson> lessons) { this.lessons = lessons; }
}
