package com.harsh.explainreport.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedFlagReport {
    private boolean active;
    private List<String> criticalFindings;
    private List<String> instructions;
}
