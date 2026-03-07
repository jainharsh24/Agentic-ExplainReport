package com.harsh.explainreport.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InsightResponse {
    private String title;
    private List<String> items;
    private String error;

    public static InsightResponse success(String title, List<String> items) {
        return new InsightResponse(title, items, null);
    }

    public static InsightResponse error(String message) {
        return new InsightResponse(null, Collections.emptyList(), message);
    }
}
