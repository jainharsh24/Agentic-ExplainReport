package com.harsh.explainreport.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisResponse {

    private List<String> summary;
    private List<String> keyFindings;
    private List<String> riskFlags;
    private List<String> doctorQuestions;
    private List<String> nextSteps;
}

