package com.harsh.explainreport.dashboard.dto;

import java.util.Arrays;

public enum GuidedQuery {
    RISK_FLAGS("risk_flags", "Explain Risk Flags",
            "Explain the risk flags and why they matter."),
    ABNORMAL_VALUES("abnormal_values", "Abnormal Values",
            "List abnormal values and what they indicate."),
    FOLLOW_UP("follow_up", "Follow-Up Checks",
            "Suggest follow-up checks or tests implied by the report."),
    KEY_TERMS("key_terms", "Explain Key Terms",
            "Explain important medical terms found in the report.");

    private final String type;
    private final String title;
    private final String prompt;

    GuidedQuery(String type, String title, String prompt) {
        this.type = type;
        this.title = title;
        this.prompt = prompt;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getPrompt() {
        return prompt;
    }

    public static GuidedQuery fromType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.type.equalsIgnoreCase(type.trim()))
                .findFirst()
                .orElse(null);
    }
}
