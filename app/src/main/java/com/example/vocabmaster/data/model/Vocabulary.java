package com.example.vocabmaster.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

@IgnoreExtraProperties
public class Vocabulary {
    private String vocabularyId;
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
    
    private Object synonyms;
    private Object antonyms;
    private Object collocations;
    private Object related_forms;
    
    private String turkish_translation;
    private Timestamp created_at;

    public Vocabulary() {}

    public String getVocabularyId() { return vocabularyId; }
    public void setVocabularyId(String vocabularyId) { this.vocabularyId = vocabularyId; }

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    @PropertyName("part_of_speech")
    public String getPartOfSpeech() { return part_of_speech; }
    @PropertyName("part_of_speech")
    public void setPartOfSpeech(String part_of_speech) { this.part_of_speech = part_of_speech; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    @PropertyName("example_sentence")
    public String getExampleSentence() { return example_sentence; }
    @PropertyName("example_sentence")
    public void setExampleSentence(String example_sentence) { this.example_sentence = example_sentence; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    // Trường audio từ script import
    @PropertyName("audio")
    public String getAudio() { return audio; }
    @PropertyName("audio")
    public void setAudio(String audio) { this.audio = audio; }

    // Trường audio_url dự phòng
    @PropertyName("audio_url")
    public String getAudioUrl() { return audio_url; }
    @PropertyName("audio_url")
    public void setAudioUrl(String audio_url) { this.audio_url = audio_url; }

    @PropertyName("image_url")
    public String getImageUrl() { return image_url; }
    @PropertyName("image_url")
    public void setImageUrl(String image_url) { this.image_url = image_url; }

    public String getPhonetic() { return phonetic; }
    public void setPhonetic(String phonetic) { this.phonetic = phonetic; }

    public String getCefr() { return cefr; }
    public void setCefr(String cefr) { this.cefr = cefr; }

    public Object getSynonyms() { return synonyms; }
    public void setSynonyms(Object synonyms) { this.synonyms = synonyms; }

    public Object getAntonyms() { return antonyms; }
    public void setAntonyms(Object antonyms) { this.antonyms = antonyms; }

    public Object getCollocations() { return collocations; }
    public void setCollocations(Object collocations) { this.collocations = collocations; }

    @PropertyName("related_forms")
    public Object getRelatedForms() { return related_forms; }
    @PropertyName("related_forms")
    public void setRelatedForms(Object related_forms) { this.related_forms = related_forms; }

    @PropertyName("turkish_translation")
    public String getTurkishTranslation() { return turkish_translation; }
    @PropertyName("turkish_translation")
    public void setTurkishTranslation(String turkish_translation) { this.turkish_translation = turkish_translation; }

    @PropertyName("created_at")
    public Timestamp getCreatedAt() { return created_at; }
    @PropertyName("created_at")
    public void setCreatedAt(Timestamp created_at) { this.created_at = created_at; }
    
    // Helper để lấy bất kỳ URL âm thanh nào khả dụng
    public String getAnyAudioUrl() {
        if (audio != null && !audio.trim().isEmpty()) return audio;
        return audio_url;
    }
}
