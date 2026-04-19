package com.example.vocabmaster.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.vocabmaster.data.model.LearningProfile;

@Dao
public interface LearningProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LearningProfile profile);

    @Update
    void update(LearningProfile profile);

    @Query("SELECT * FROM learning_profiles WHERE userId = :userId LIMIT 1")
    LiveData<LearningProfile> getProfileByUserId(String userId);

    @Query("SELECT * FROM learning_profiles WHERE userId = :userId LIMIT 1")
    LearningProfile getProfileByUserIdSync(String userId);

    @Delete
    void delete(LearningProfile profile);
}
