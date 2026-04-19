package com.example.vocabmaster.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;
import java.util.List;

@Entity(tableName = "study_plans")
public class StudyPlan {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String userId;
    private String courseId;
    private int dailyMinutes;
    private int sessionsPerWeek;
    private List<Integer> daysOfWeek; // 1 = Sunday, 2 = Monday, ...
    private Date startDate;
    private Date targetEndDate;
    private boolean isActive;

    public StudyPlan() {
        this.isActive = true;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public int getDailyMinutes() { return dailyMinutes; }
    public void setDailyMinutes(int dailyMinutes) { this.dailyMinutes = dailyMinutes; }
    public int getSessionsPerWeek() { return sessionsPerWeek; }
    public void setSessionsPerWeek(int sessionsPerWeek) { this.sessionsPerWeek = sessionsPerWeek; }
    public List<Integer> getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(List<Integer> daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }
    public Date getTargetEndDate() { return targetEndDate; }
    public void setTargetEndDate(Date targetEndDate) { this.targetEndDate = targetEndDate; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
