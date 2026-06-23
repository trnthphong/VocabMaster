package com.example.vocabmaster.data.model;

import java.util.ArrayList;
import java.util.List;

public class Challenge {
    private String id;
    private String lessonId;
    private String type; // SELECT, ASSIST
    private String question;
    private int orderNum;
    private String cefrLevel;
    private String skill;
    private int difficulty;
    private String targetText;
    private String promptTemplate;
    private String reviewTag;
    private List<ChallengeOption> options = new ArrayList<>();

    public Challenge() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLessonId() { return lessonId; }
    public void setLessonId(String lessonId) { this.lessonId = lessonId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public int getOrderNum() { return orderNum; }
    public void setOrderNum(int orderNum) { this.orderNum = orderNum; }
    public String getCefrLevel() { return cefrLevel; }
    public void setCefrLevel(String cefrLevel) { this.cefrLevel = cefrLevel; }
    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
    public String getTargetText() { return targetText; }
    public void setTargetText(String targetText) { this.targetText = targetText; }
    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
    public String getReviewTag() { return reviewTag; }
    public void setReviewTag(String reviewTag) { this.reviewTag = reviewTag; }
    public List<ChallengeOption> getOptions() { return options; }
    public void setOptions(List<ChallengeOption> options) { this.options = options; }

    public static class ChallengeOption {
        private String text;
        private String imageUrl;
        private String audioUrl;
        private boolean correct;

        public ChallengeOption() {}

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getAudioUrl() { return audioUrl; }
        public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
        public boolean isCorrect() { return correct; }
        public void setCorrect(boolean correct) { this.correct = correct; }
    }
}
