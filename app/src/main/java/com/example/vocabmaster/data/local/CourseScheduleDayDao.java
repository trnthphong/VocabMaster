package com.example.vocabmaster.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.vocabmaster.data.model.CourseScheduleDay;

import java.util.Date;
import java.util.List;

@Dao
public interface CourseScheduleDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CourseScheduleDay> days);

    @Update
    void update(CourseScheduleDay day);

    @Query("SELECT * FROM course_schedule_days WHERE userId = :userId AND courseId = :courseId ORDER BY date ASC")
    LiveData<List<CourseScheduleDay>> getScheduleForCourse(String userId, String courseId);

    @Query("SELECT * FROM course_schedule_days WHERE userId = :userId ORDER BY date ASC")
    List<CourseScheduleDay> getScheduleForCourseSync(String userId);

    @Query("SELECT * FROM course_schedule_days WHERE userId = :userId AND date = :date LIMIT 1")
    LiveData<CourseScheduleDay> getScheduleForDate(String userId, Date date);

    @Query("DELETE FROM course_schedule_days WHERE userId = :userId AND courseId = :courseId")
    void deleteScheduleByCourse(String userId, String courseId);

    @Transaction
    default void recreateSchedule(String userId, String courseId, List<CourseScheduleDay> newDays) {
        deleteScheduleByCourse(userId, courseId);
        insertAll(newDays);
    }
}
