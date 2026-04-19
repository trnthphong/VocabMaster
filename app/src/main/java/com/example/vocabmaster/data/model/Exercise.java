package com.example.vocabmaster.data.model;

import java.util.List;

public class Exercise {
    private String exerciseId;
    private String lessonId;
    private String type; // multiple_choice, fill_blank, matching, speaking_practice
    private String question;
    private String contentUrl; // for audio/image questions
    private List<String> options;
    private String correctAnswer;
    private String explanation;
    private int difficulty; // 1-10

    public Exercise() {}

    public String getExerciseId() { return exerciseId; }
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }
    public String getLessonId() { return lessonId; }
    public void setLessonId(String lessonId) { this.lessonId = lessonId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getContentUrl() { return contentUrl; }
    public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
}
