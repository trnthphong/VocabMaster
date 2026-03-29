package com.example.vocabmaster.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.util.ArrayList;
import java.util.List;

public class Vocabulary {
    private String vocabularyId;
    private String word;
    private String cefr_level;
    private String part_of_speech;
    private String definition_en;
    private String definition_vi;
    private List<String> example_sentences;
    private String audio_url;
    private String image_url;
    private int difficulty;
    private int frequency_rank;
    private List<String> topics;
    private Timestamp created_at;

    public Vocabulary() {
        this.example_sentences = new ArrayList<>();
        this.topics = new ArrayList<>();
    }

    // Mapping Firestore snake_case to Java getters/setters
    @PropertyName("cefr_level")
    public String getCefrLevel() { return cefr_level; }
    @PropertyName("cefr_level")
    public void setCefrLevel(String cefr_level) { this.cefr_level = cefr_level; }

    @PropertyName("part_of_speech")
    public String getPartOfSpeech() { return part_of_speech; }
    @PropertyName("part_of_speech")
    public void setPartOfSpeech(String part_of_speech) { this.part_of_speech = part_of_speech; }

    @PropertyName("definition_en")
    public String getDefinitionEn() { return definition_en; }
    @PropertyName("definition_en")
    public void setDefinitionEn(String definition_en) { this.definition_en = definition_en; }

    @PropertyName("definition_vi")
    public String getDefinitionVi() { return definition_vi; }
    @PropertyName("definition_vi")
    public void setDefinitionVi(String definition_vi) { this.definition_vi = definition_vi; }

    @PropertyName("example_sentences")
    public List<String> getExampleSentences() { return example_sentences; }
    @PropertyName("example_sentences")
    public void setExampleSentences(List<String> example_sentences) { this.example_sentences = example_sentences; }

    @PropertyName("audio_url")
    public String getAudioUrl() { return audio_url; }
    @PropertyName("audio_url")
    public void setAudioUrl(String audio_url) { this.audio_url = audio_url; }

    @PropertyName("image_url")
    public String getImageUrl() { return image_url; }
    @PropertyName("image_url")
    public void setImageUrl(String image_url) { this.image_url = image_url; }

    @PropertyName("frequency_rank")
    public int getFrequencyRank() { return frequency_rank; }
    @PropertyName("frequency_rank")
    public void setFrequencyRank(int frequency_rank) { this.frequency_rank = frequency_rank; }

    @PropertyName("created_at")
    public Timestamp getCreatedAt() { return created_at; }
    @PropertyName("created_at")
    public void setCreatedAt(Timestamp created_at) { this.created_at = created_at; }

    public String getVocabularyId() { return vocabularyId; }
    public void setVocabularyId(String vocabularyId) { this.vocabularyId = vocabularyId; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
    public List<String> getTopics() { return topics; }
    public void setTopics(List<String> topics) { this.topics = topics; }
}
