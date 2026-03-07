package com.harsh.explainreport.dashboard.dto;

import java.util.Collections;
import java.util.List;

public class ChatResponse {
    private List<String> answers;
    private String error;

    public ChatResponse() {
    }

    public ChatResponse(List<String> answers, String error) {
        this.answers = answers;
        this.error = error;
    }

    public static ChatResponse success(List<String> answers) {
        return new ChatResponse(answers, null);
    }

    public static ChatResponse error(String message) {
        return new ChatResponse(Collections.emptyList(), message);
    }

    public List<String> getAnswers() {
        return answers;
    }

    public void setAnswers(List<String> answers) {
        this.answers = answers;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

