package com.example.vocabmaster.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.vocabmaster.data.model.Flashcard;

import java.util.List;

@Dao
public interface FlashcardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Flashcard flashcard);

    @Update
    void update(Flashcard flashcard);

    @Delete
    void delete(Flashcard flashcard);

    @Query("SELECT * FROM flashcards WHERE courseId = :courseId")
    LiveData<List<Flashcard>> getFlashcardsByCourse(int courseId);

    @Query("SELECT * FROM flashcards")
    LiveData<List<Flashcard>> getAllFlashcards();

    @Query("SELECT * FROM flashcards WHERE courseId = -1")
    LiveData<List<Flashcard>> getPersonalFlashcards();

    @Query("SELECT * FROM flashcards WHERE courseId = :courseId AND lastReviewTime + interval * 86400000 <= :currentTime")
    LiveData<List<Flashcard>> getFlashcardsToReview(int courseId, long currentTime);

    @Query("SELECT * FROM flashcards WHERE firestoreId = :firestoreId LIMIT 1")
    Flashcard getFlashcardByFirestoreId(String firestoreId);
}
