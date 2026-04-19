package com.example.vocabmaster.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import com.example.vocabmaster.data.local.Converters;
import java.util.Date;
import java.util.List;

@Entity(tableName = "course_schedule_days")
@TypeConverters(Converters.class)
public class CourseScheduleDay {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String userId;
    private String courseId;
    private Date date;
    private List<String> lessonIds;
    private String status; // pending, completed, skipped
    private int dailyMinutesGoal;
    private int actualMinutesSpent;

    public CourseScheduleDay() {
        this.status = "pending";
        this.actualMinutesSpent = 0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    public List<String> getLessonIds() { return lessonIds; }
    public void setLessonIds(List<String> lessonIds) { this.lessonIds = lessonIds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getDailyMinutesGoal() { return dailyMinutesGoal; }
    public void setDailyMinutesGoal(int dailyMinutesGoal) { this.dailyMinutesGoal = dailyMinutesGoal; }
    public int getActualMinutesSpent() { return actualMinutesSpent; }
    public void setActualMinutesSpent(int actualMinutesSpent) { this.actualMinutesSpent = actualMinutesSpent; }
}
