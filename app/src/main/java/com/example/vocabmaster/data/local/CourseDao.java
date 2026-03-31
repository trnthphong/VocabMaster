package com.example.vocabmaster.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.vocabmaster.data.model.Course;

import java.util.List;

@Dao
public interface CourseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Course course);

    @Update
    void update(Course course);

    @Delete
    void delete(Course course);

    @Query("SELECT * FROM courses ORDER BY id DESC")
    LiveData<List<Course>> getAllCourses();

    @Query("SELECT * FROM courses WHERE creatorId = :userId")
    LiveData<List<Course>> getCoursesByUser(String userId);

    @Query("SELECT * FROM courses WHERE title LIKE :searchQuery")
    LiveData<List<Course>> searchCourses(String searchQuery);
    
    @Query("SELECT * FROM courses WHERE firestoreId = :firestoreId LIMIT 1")
    Course getCourseByFirestoreId(String firestoreId);
}
