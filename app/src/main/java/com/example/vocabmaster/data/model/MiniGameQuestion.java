package com.example.vocabmaster.data.model;

import java.util.ArrayList;
import java.util.List;

public class MiniGameQuestion {
    private String mode;
    private String prompt;
    private String answer;
    private List<String> options;

    public MiniGameQuestion() {
        options = new ArrayList<>();
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
}
