package com.example.vocabmaster.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

@IgnoreExtraProperties
@Entity(tableName = "vocabularies_local")
public class Vocabulary {
    @PrimaryKey
    @NonNull
    private String vocabularyId = "";
    private String word;
    private String part_of_speech;
    private String definition;
    private String example_sentence;
    private String topic;
    private String lang;
    private String audio;
    private String audio_url;
    private String image_url;
    private String phonetic;
    private String cefr;
    
    // Note: Room doesn't support complex objects directly without converters
    // For simplicity in offline mode, we might just store the main strings.
    // If these are needed, you'll need TypeConverters for List/Object.
    private String turkish_translation;
    private String vietnamese_translation;
    // Learning status: 0=new, 1=learning, 2=learned
    private int learnStatus = 0;
    private long learnedAt = 0;

    public Vocabulary() {}

    @NonNull
    public String getVocabularyId() { return vocabularyId; }
    public void setVocabularyId(@NonNull String vocabularyId) { this.vocabularyId = vocabularyId; }

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    @PropertyName("part_of_speech")
    public String getPart_of_speech() { return part_of_speech; }
    @PropertyName("part_of_speech")
    public void setPart_of_speech(String part_of_speech) { this.part_of_speech = part_of_speech; }

    @Ignore
    public String getPartOfSpeech() { return part_of_speech; }
    @Ignore
    public void setPartOfSpeech(String part_of_speech) { this.part_of_speech = part_of_speech; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    @PropertyName("example_sentence")
    public String getExample_sentence() { return example_sentence; }
    @PropertyName("example_sentence")
    public void setExample_sentence(String example_sentence) { this.example_sentence = example_sentence; }

    @Ignore
    public String getExampleSentence() { return example_sentence; }
    @Ignore
    public void setExampleSentence(String example_sentence) { this.example_sentence = example_sentence; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    @PropertyName("audio")
    public String getAudio() { return audio; }
    @PropertyName("audio")
    public void setAudio(String audio) { this.audio = audio; }

    @PropertyName("audio_url")
    public String getAudio_url() { return audio_url; }
    @PropertyName("audio_url")
    public void setAudio_url(String audio_url) { this.audio_url = audio_url; }

    @Ignore
    public String getAudioUrl() { return audio_url; }
    @Ignore
    public void setAudioUrl(String audio_url) { this.audio_url = audio_url; }

    @PropertyName("image_url")
    public String getImage_url() { return image_url; }
    @PropertyName("image_url")
    public void setImage_url(String image_url) { this.image_url = image_url; }

    @Ignore
    public String getImageUrl() { return image_url; }
    @Ignore
    public void setImageUrl(String image_url) { this.image_url = image_url; }

    public String getPhonetic() { return phonetic; }
    public void setPhonetic(String phonetic) { this.phonetic = phonetic; }

    public String getCefr() { return cefr; }
    public void setCefr(String cefr) { this.cefr = cefr; }

    @PropertyName("turkish_translation")
    public String getTurkish_translation() { return turkish_translation; }
    @PropertyName("turkish_translation")
    public void setTurkish_translation(String turkish_translation) { this.turkish_translation = turkish_translation; }

    @Ignore
    public String getTurkishTranslation() { return turkish_translation; }
    @Ignore
    public void setTurkishTranslation(String turkish_translation) { this.turkish_translation = turkish_translation; }

    @PropertyName("vietnamese_translation")
    public String getVietnamese_translation() { return vietnamese_translation; }
    @PropertyName("vietnamese_translation")
    public void setVietnamese_translation(String vietnamese_translation) { this.vietnamese_translation = vietnamese_translation; }

    public String getVietnameseTranslation() { return vietnamese_translation; }

    public int getLearnStatus() { return learnStatus; }
    public void setLearnStatus(int learnStatus) { this.learnStatus = learnStatus; }

    public long getLearnedAt() { return learnedAt; }
    public void setLearnedAt(long learnedAt) { this.learnedAt = learnedAt; }
    
    public String getAnyAudioUrl() {
        if (audio != null && !audio.trim().isEmpty()) return audio;
        return audio_url;
    }
}
