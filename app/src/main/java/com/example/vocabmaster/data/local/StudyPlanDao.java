package com.example.vocabmaster.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.vocabmaster.data.model.StudyPlan;

import java.util.List;

@Dao
public interface StudyPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(StudyPlan plan);

    @Update
    void update(StudyPlan plan);

    @Query("SELECT * FROM study_plans WHERE userId = :userId AND isActive = 1 LIMIT 1")
    LiveData<StudyPlan> getActivePlanByUserId(String userId);

    @Query("SELECT * FROM study_plans WHERE userId = :userId")
    List<StudyPlan> getAllPlansByUserId(String userId);

    @Delete
    void delete(StudyPlan plan);
}
