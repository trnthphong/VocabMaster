package com.example.vocabmaster.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.vocabmaster.data.model.PendingUserProgress;

import java.util.List;

@Dao
public interface PendingUserProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PendingUserProgress progress);

    @Query("SELECT * FROM pending_user_progress ORDER BY createdAtMillis ASC LIMIT :limit")
    List<PendingUserProgress> getOldest(int limit);

    @Query("DELETE FROM pending_user_progress WHERE progressId = :progressId")
    void deleteById(String progressId);

    @Query("UPDATE pending_user_progress SET attemptCount = attemptCount + 1 WHERE progressId = :progressId")
    void incrementAttempt(String progressId);

    @Query("SELECT COUNT(*) FROM pending_user_progress")
    int count();
}
