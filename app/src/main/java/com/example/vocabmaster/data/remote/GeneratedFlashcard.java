package com.example.vocabmaster.data.remote;

import com.google.gson.annotations.SerializedName;

public class GeneratedFlashcard {
    private String word;
    private String definition;
    @SerializedName("vietnamese_translation")
    private String vietnamese_translation;
    @SerializedName("part_of_speech")
    private String part_of_speech;
    private String phonetic;
    @SerializedName("example_sentence")
    private String example_sentence;
    @SerializedName("image_url")
    private String image_url;

    public GeneratedFlashcard() {}

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    public String getVietnamese_translation() { return vietnamese_translation; }
    public void setVietnamese_translation(String vietnamese_translation) {
        this.vietnamese_translation = vietnamese_translation;
    }

    public String getPart_of_speech() { return part_of_speech; }
    public void setPart_of_speech(String part_of_speech) { this.part_of_speech = part_of_speech; }

    public String getPhonetic() { return phonetic; }
    public void setPhonetic(String phonetic) { this.phonetic = phonetic; }

    public String getExample_sentence() { return example_sentence; }
    public void setExample_sentence(String example_sentence) {
        this.example_sentence = example_sentence;
    }

    public String getImage_url() { return image_url; }
    public void setImage_url(String image_url) { this.image_url = image_url; }
}
