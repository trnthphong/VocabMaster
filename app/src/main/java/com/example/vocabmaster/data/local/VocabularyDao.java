package com.example.vocabmaster.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.vocabmaster.data.model.Vocabulary;

import java.util.List;

@Dao
public interface VocabularyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Vocabulary> vocabularies);

    @Update
    void update(Vocabulary vocabulary);

    @androidx.room.Delete
    void delete(Vocabulary vocabulary);

    @Query("SELECT * FROM vocabularies_local WHERE topic = :topic")
    List<Vocabulary> getVocabulariesByTopic(String topic);

    @Query("SELECT * FROM vocabularies_local WHERE topic = :topic AND learnStatus = 0")
    List<Vocabulary> getNewWordsByTopic(String topic);

    @Query("SELECT * FROM vocabularies_local WHERE topic = :topic AND learnStatus > 0")
    List<Vocabulary> getLearnedWordsByTopic(String topic);

    @Query("SELECT COUNT(*) FROM vocabularies_local WHERE topic = :topic")
    int getCountByTopic(String topic);

    @Query("SELECT COUNT(*) FROM vocabularies_local WHERE topic = :topic AND learnStatus = 2")
    int getLearnedCountByTopic(String topic);

    @Query("SELECT COUNT(*) FROM vocabularies_local WHERE topic = :topic AND learnStatus = 1")
    int getLearningCountByTopic(String topic);

    @Query("SELECT COUNT(*) FROM vocabularies_local WHERE learnStatus = 2")
    int getLearnedWordsCount();

    @Query("UPDATE vocabularies_local SET learnStatus = :status, learnedAt = :timestamp WHERE vocabularyId = :id")
    void updateLearnStatus(String id, int status, long timestamp);

    @Query("DELETE FROM vocabularies_local WHERE topic = :topic")
    void deleteByTopic(String topic);

    @Query("DELETE FROM vocabularies_local WHERE vocabularyId = :id")
    void deleteById(String id);

    @Query("SELECT COUNT(*) > 0 FROM vocabularies_local WHERE topic = :topic AND vietnamese_translation IS NOT NULL AND vietnamese_translation != ''")
    boolean hasVietnamese(String topic);

    @Query("SELECT * FROM vocabularies_local ORDER BY RANDOM() LIMIT :limit")
    List<Vocabulary> getRandomVocabularies(int limit);
}
