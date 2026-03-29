package com.example.vocabmaster.data.model;

import java.util.List;

public class StudyTask {
    public enum Type {
        FLASHCARD,
        IMAGE_CHOICE,
        AUDIO_CHOICE,
        MATCHING,
        FILL_BLANK
    }

    private Type type;
    private Vocabulary targetVocab;
    private List<String> options; // For choices
    private List<Vocabulary> matchingPairs; // For matching
    private String correctWord;

    public StudyTask(Type type, Vocabulary targetVocab) {
        this.type = type;
        this.targetVocab = targetVocab;
        this.correctWord = targetVocab.getWord();
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public Vocabulary getTargetVocab() { return targetVocab; }
    public void setTargetVocab(Vocabulary targetVocab) { this.targetVocab = targetVocab; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }

    public List<Vocabulary> getMatchingPairs() { return matchingPairs; }
    public void setMatchingPairs(List<Vocabulary> matchingPairs) { this.matchingPairs = matchingPairs; }

    public String getCorrectWord() { return correctWord; }
    public void setCorrectWord(String correctWord) { this.correctWord = correctWord; }
}
