package com.harsh.explainreport.dashboard.service;

import com.harsh.explainreport.dashboard.dto.AnalysisResponse;
import com.harsh.explainreport.dashboard.dto.GuidedQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    private final WebClient webClient = WebClient.builder().build();

    public AnalysisResponse analyzeText(String text) {

        String prompt = """
        Return output STRICTLY in this format:

        SUMMARY:
        - <bullet point>
        - <bullet point>

        KEY FINDINGS:
        - <bullet point>
        - <bullet point>

        RISK FLAGS:
        - <bullet point>
        - <bullet point>

        QUESTIONS:
        1) <question>
        2) <question>
        3) <question>
        4) <question>
        5) <question>

        NEXT STEPS:
        - <bullet point>
        - <bullet point>

        Report:
        """ + text;

        String response = callGroq(prompt);

        String summarySection = extractSection(response, "SUMMARY:", "KEY FINDINGS:");
        String keyFindingsSection = extractSection(response, "KEY FINDINGS:", "RISK FLAGS:");
        String riskSection = extractSection(response, "RISK FLAGS:", "QUESTIONS:");
        String questionsSection = extractSection(response, "QUESTIONS:", "NEXT STEPS:");
        String nextStepsSection = extractSection(response, "NEXT STEPS:", null);

        return new AnalysisResponse(
                parseList(summarySection),
                parseList(keyFindingsSection),
                parseList(riskSection),
                parseList(questionsSection),
                parseList(nextStepsSection)
        );
    }

    public List<String> chatWithReport(String reportText, String question) {

        String prompt = """
        You are a medical assistant AI.

        Use the following patient report to answer the question.

        Respond with 3-5 concise bullet points.
        Each bullet must be under 18 words.
        Do not add headings or extra text.

        REPORT:
        """ + reportText + """

        QUESTION:
        """ + question;

        String response = callGroq(prompt);
        List<String> items = parseList(response);
        if (items.isEmpty() && response != null && !response.isBlank()) {
            items = List.of(response.trim());
        }

        return items.stream()
                .limit(5)
                .map(item -> limitWords(item, 18))
                .collect(Collectors.toList());
    }

    public List<String> guidedInsights(String reportText, GuidedQuery query) {
        if (query == null) {
            return Collections.emptyList();
        }

        String prompt = """
        You are a medical report analyzer.

        TASK:
        """ + query.getPrompt() + """

        RULES:
        - Provide 4 to 6 concise bullet points.
        - Each bullet must be under 18 words.
        - Do not add headings or extra text.
        - If information is missing, say "Not found in report."

        REPORT:
        """ + reportText;

        String response = callGroq(prompt);
        List<String> items = parseList(response);
        if (items.isEmpty() && response != null && !response.isBlank()) {
            items = List.of(response.trim());
        }

        return items.stream()
                .limit(6)
                .map(item -> limitWords(item, 18))
                .collect(Collectors.toList());
    }

    private String callGroq(String prompt) {

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "llama-3.3-70b-versatile");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        requestBody.put("messages", messages);

        return webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new RuntimeException("Groq Error: " + body)))
                .bodyToMono(Map.class)
                .map(response -> {
                    List<Map<String, Object>> choices =
                            (List<Map<String, Object>>) response.get("choices");

                    Map<String, Object> message =
                            (Map<String, Object>) choices.get(0).get("message");

                    return message.get("content").toString();
                })
                .block();
    }

    private String extractSection(String response, String startHeader, String endHeader) {
        if (response == null || response.isBlank()) {
            return "";
        }

        int startIndex = response.indexOf(startHeader);
        if (startIndex < 0) {
            return "";
        }
        startIndex += startHeader.length();

        int endIndex = endHeader == null ? -1 : response.indexOf(endHeader, startIndex);
        String section = endIndex < 0 ? response.substring(startIndex) : response.substring(startIndex, endIndex);
        return section.trim();
    }

    private List<String> parseList(String section) {
        if (section == null || section.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(section.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.replaceFirst("^(?:[-*]|\\d+[\\).])\\s*", ""))
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    private String limitWords(String text, int maxWords) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String[] words = trimmed.split("\\s+");
        if (words.length <= maxWords) {
            return trimmed;
        }

        return String.join(" ", Arrays.copyOfRange(words, 0, maxWords)) + "...";
    }
}

