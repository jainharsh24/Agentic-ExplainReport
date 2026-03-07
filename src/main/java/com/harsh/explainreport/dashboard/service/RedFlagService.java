package com.harsh.explainreport.dashboard.service;

import com.harsh.explainreport.dashboard.dto.RedFlagReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RedFlagService {
    private static final int MIN_CRITICAL_COUNT = 3;

    private static final List<Rule> RULES = List.of(
            new Rule("Hemoglobin", patternFor("hemoglobin|hb\\b"), RuleType.LOW, 8.0),
            new Rule("Hemoglobin", patternFor("hemoglobin|hb\\b"), RuleType.HIGH, 18.0),
            new Rule("WBC", patternFor("wbc|white\\s*blood\\s*cell"), RuleType.HIGH, 20000),
            new Rule("WBC", patternFor("wbc|white\\s*blood\\s*cell"), RuleType.LOW, 2500),
            new Rule("Platelets", patternFor("platelets|plt"), RuleType.LOW, 50000),
            new Rule("Hematocrit", patternFor("hematocrit|hct"), RuleType.LOW, 25),
            new Rule("MCV", patternFor("mcv|mean\\s*corpuscular\\s*volume"), RuleType.HIGH, 120),
            new Rule("MCV", patternFor("mcv|mean\\s*corpuscular\\s*volume"), RuleType.LOW, 60),
            new Rule("Creatinine", patternFor("creatinine"), RuleType.HIGH, 4.0),
            new Rule("BUN", patternFor("bun|blood\\s*urea\\s*nitrogen|urea"), RuleType.HIGH, 80),
            new Rule("Potassium", patternFor("potassium|k\\+"), RuleType.HIGH, 6.0),
            new Rule("eGFR", patternFor("egfr|e-gfr"), RuleType.LOW, 15),
            new Rule("Sodium", patternFor("sodium|na\\+"), RuleType.LOW, 120),
            new Rule("Sodium", patternFor("sodium|na\\+"), RuleType.HIGH, 160),
            new Rule("Total Bilirubin", patternFor("total\\s*bilirubin|bilirubin"), RuleType.HIGH, 10),
            new Rule("ALT", patternFor("alt|sgpt|alanine\\s*aminotransferase"), RuleType.HIGH, 500),
            new Rule("AST", patternFor("ast|sgot|aspartate\\s*aminotransferase"), RuleType.HIGH, 500),
            new Rule("Alkaline Phosphatase", patternFor("alkaline\\s*phosphatase|alp"), RuleType.HIGH, 350),
            new Rule("Albumin", patternFor("albumin"), RuleType.LOW, 2.5),
            new Rule("Troponin", patternFor("troponin"), RuleType.HIGH, 1.0),
            new Rule("CK-MB", patternFor("ck\\s*-?mb"), RuleType.HIGH, 80),
            new Rule("Heart Rate", patternFor("heart\\s*rate|pulse"), RuleType.HIGH, 130),
            new Rule("Heart Rate", patternFor("heart\\s*rate|pulse"), RuleType.LOW, 40),
            new Rule("Fasting Blood Sugar", patternFor("fasting\\s*blood\\s*sugar|fasting\\s*glucose"), RuleType.HIGH, 300),
            new Rule("Random Blood Sugar", patternFor("random\\s*blood\\s*sugar|random\\s*glucose"), RuleType.HIGH, 400),
            new Rule("HbA1c", patternFor("hba1c|hb\\s*a1c|glycated\\s*hemoglobin"), RuleType.HIGH, 10),
            new Rule("CRP", patternFor("c-?reactive\\s*protein|crp"), RuleType.HIGH, 100),
            new Rule("Procalcitonin", patternFor("procalcitonin|pct"), RuleType.HIGH, 5),
            new Rule("Lactate", patternFor("lactate"), RuleType.HIGH, 4)
    );

    private static final List<KeywordRule> KEYWORD_RULES = List.of(
            new KeywordRule(patternFor("acute\\s*liver\\s*failure"), "Acute liver failure mentioned."),
            new KeywordRule(patternFor("hepatic\\s*encephalopathy"), "Hepatic encephalopathy risk mentioned."),
            new KeywordRule(patternFor("st-?segment\\s*elevation"), "ST-segment elevation mentioned."),
            new KeywordRule(patternFor("myocardial\\s*infarction"), "Acute myocardial infarction mentioned."),
            new KeywordRule(patternFor("urgent\\s*icu|icu\\s*admission"), "ICU admission recommended.")
    );

    private static final Pattern BP_PATTERN = Pattern.compile("(?i)(bp|blood\\s*pressure)\\s*[:\\-]?\\s*(\\d{2,3})\\s*/\\s*(\\d{2,3})");

    public RedFlagReport evaluate(String reportText) {
        if (reportText == null || reportText.isBlank()) {
            return new RedFlagReport(false, List.of(), List.of());
        }

        List<String> findings = new ArrayList<>();
        Set<String> matched = new HashSet<>();

        String[] lines = reportText.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            for (KeywordRule keywordRule : KEYWORD_RULES) {
                if (keywordRule.pattern.matcher(line).find()) {
                    if (matched.add(keywordRule.message)) {
                        findings.add(keywordRule.message);
                    }
                }
            }

            Matcher bpMatcher = BP_PATTERN.matcher(line);
            if (bpMatcher.find()) {
                int systolic = parseInt(bpMatcher.group(2));
                int diastolic = parseInt(bpMatcher.group(3));
                if (systolic >= 180 || diastolic >= 120) {
                    if (matched.add("Blood Pressure")) {
                        findings.add("Critical blood pressure " + systolic + "/" + diastolic + " (>= 180/120).");
                    }
                }
                if (systolic > 0 && diastolic > 0 && (systolic <= 80 || diastolic <= 50)) {
                    if (matched.add("Blood Pressure Low")) {
                        findings.add("Critical blood pressure " + systolic + "/" + diastolic + " (<= 80/50).");
                    }
                }
            }

            for (Rule rule : RULES) {
                Matcher matcher = rule.pattern.matcher(line);
                if (!matcher.find()) {
                    continue;
                }
                Double value = extractValue(line, matcher.end());
                if (value == null) {
                    continue;
                }
                if (rule.isCritical(value)) {
                    String key = rule.name + ":" + rule.type;
                    if (matched.add(key)) {
                        findings.add(rule.toMessage(value));
                    }
                }
            }
        }

        boolean active = findings.size() >= MIN_CRITICAL_COUNT;
        List<String> instructions = active ? criticalInstructions() : List.of();

        return new RedFlagReport(active, findings, instructions);
    }

    private static Pattern patternFor(String keyword) {
        return Pattern.compile("(?i)\\b(" + keyword + ")\\b");
    }

    private static Double extractValue(String line, int startIndex) {
        String tail = line.substring(Math.min(startIndex, line.length()));
        Matcher matcher = Pattern.compile("(-?\\d{1,4}(?:,\\d{3})*(?:\\.\\d+)?)").matcher(tail);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static List<String> criticalInstructions() {
        return List.of(
                "Seek urgent in-person medical evaluation as soon as possible.",
                "Do not change or stop medications without a clinician's advice.",
                "Avoid heavy oily meals and high-sugar foods until reviewed.",
                "Stay hydrated unless your doctor has advised fluid restriction.",
                "Ask your doctor if imaging or further tests are urgently needed.",
                "If severe symptoms occur, go to emergency care immediately."
        );
    }

    private enum RuleType {
        HIGH, LOW
    }

    private static class Rule {
        private final String name;
        private final Pattern pattern;
        private final RuleType type;
        private final double threshold;

        private Rule(String name, Pattern pattern, RuleType type, double threshold) {
            this.name = name;
            this.pattern = pattern;
            this.type = type;
            this.threshold = threshold;
        }

        private boolean isCritical(double value) {
            return type == RuleType.HIGH ? value >= threshold : value <= threshold;
        }

        private String toMessage(double value) {
            String comparator = type == RuleType.HIGH ? ">=" : "<=";
            return name + " " + value + " (" + comparator + " " + threshold + ").";
        }
    }

    private static class KeywordRule {
        private final Pattern pattern;
        private final String message;

        private KeywordRule(Pattern pattern, String message) {
            this.pattern = pattern;
            this.message = message;
        }
    }
}
