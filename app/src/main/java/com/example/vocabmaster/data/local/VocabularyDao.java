package com.example.vocabmaster.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.vocabmaster.data.model.Vocabulary;

import java.util.List;

@Dao
public interface VocabularyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Vocabulary> vocabularies);

    @Query("SELECT * FROM vocabularies_local WHERE topic = :topic")
    List<Vocabulary> getVocabulariesByTopic(String topic);

    @Query("SELECT COUNT(*) FROM vocabularies_local WHERE topic = :topic")
    int getCountByTopic(String topic);

    @Query("DELETE FROM vocabularies_local WHERE topic = :topic")
    void deleteByTopic(String topic);

    @Query("SELECT * FROM vocabularies_local ORDER BY RANDOM() LIMIT :limit")
    List<Vocabulary> getRandomVocabularies(int limit);
}
