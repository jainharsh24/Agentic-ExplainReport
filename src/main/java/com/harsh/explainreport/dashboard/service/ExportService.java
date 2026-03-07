package com.harsh.explainreport.dashboard.service;

import jakarta.servlet.http.HttpSession;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ExportService {
    private static final int MAX_LINE_CHARS = 96;

    public String buildExportText(
            List<String> summary,
            List<String> keyFindings,
            List<String> riskFlags,
            List<String> questions,
            List<String> nextSteps,
            boolean redFlagActive,
            List<String> redFlagFindings,
            List<String> redFlagInstructions,
            String lastInsightTitle,
            List<String> lastInsightItems
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("ExplainReport Export").append("\n");
        builder.append("Exported: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");

        appendSection(builder, "Summary", summary);
        appendSection(builder, "Key Findings", keyFindings);
        appendSection(builder, "Risk Flags", riskFlags);
        appendSection(builder, "Questions For Your Doctor", questions);
        appendSection(builder, "Next Steps", nextSteps);

        if (redFlagActive && redFlagFindings != null && !redFlagFindings.isEmpty()) {
            appendSection(builder, "Critical Alert Findings", redFlagFindings);
            appendSection(builder, "Critical Alert Instructions", redFlagInstructions);
        }

        if (lastInsightItems != null && !lastInsightItems.isEmpty()) {
            String title = lastInsightTitle == null || lastInsightTitle.isBlank() ? "Guided Insight" : lastInsightTitle;
            appendSection(builder, title, lastInsightItems);
        }

        return builder.toString().trim();
    }

    public ResponseEntity<byte[]> pdfResponse(byte[] body, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }

    public ResponseEntity<byte[]> textResponse(byte[] body, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }

    public byte[] toTextBytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toPdfBytes(String text) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(PDType1Font.HELVETICA, 11);

            float margin = 50f;
            float yStart = page.getMediaBox().getHeight() - margin;
            float yPosition = yStart;
            float leading = 14f;

            for (String line : wrapText(text)) {
                if (yPosition <= margin) {
                    contentStream.close();
                    page = new PDPage();
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    contentStream.setFont(PDType1Font.HELVETICA, 11);
                    yPosition = yStart;
                }

                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(line);
                contentStream.endText();
                yPosition -= leading;
            }

            contentStream.close();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            return toTextBytes(text);
        }
    }

    public List<String> getList(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return Collections.emptyList();
    }

    public String getString(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        return value == null ? null : value.toString();
    }

    public boolean getBoolean(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    public boolean isAllEmpty(List<String>... lists) {
        for (List<String> list : lists) {
            if (list != null && !list.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void appendSection(StringBuilder builder, String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        builder.append(title).append("\n");
        for (String item : items) {
            builder.append("- ").append(item).append("\n");
        }
        builder.append("\n");
    }

    private List<String> wrapText(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        String[] rawLines = text.split("\\R");
        for (String rawLine : rawLines) {
            if (rawLine.isEmpty()) {
                lines.add("");
                continue;
            }
            lines.addAll(wrapLine(rawLine));
        }
        return lines;
    }

    private List<String> wrapLine(String line) {
        if (line.length() <= MAX_LINE_CHARS) {
            return List.of(line);
        }

        List<String> wrapped = new ArrayList<>();
        String[] words = line.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
                continue;
            }
            if (current.length() + 1 + word.length() <= MAX_LINE_CHARS) {
                current.append(' ').append(word);
            } else {
                wrapped.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            wrapped.add(current.toString());
        }
        return wrapped;
    }
}
