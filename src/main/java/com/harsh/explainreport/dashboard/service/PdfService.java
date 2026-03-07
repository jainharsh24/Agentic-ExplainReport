package com.harsh.explainreport.dashboard.service;

import com.harsh.explainreport.dashboard.exception.PdfScanException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfService {

    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z]{2,}");

    public String extractText(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            validateExtractedText(text);
            return text;
        } catch (PdfScanException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("PDF Read Error");
        }
    }

    private void validateExtractedText(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new PdfScanException("Not able to scan the PDF. Please upload a clearer report.");
        }

        int wordCount = 0;
        Matcher matcher = WORD_PATTERN.matcher(trimmed);
        while (matcher.find()) {
            wordCount++;
        }

        int letterCount = 0;
        int nonWhitespaceCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (!Character.isWhitespace(ch)) {
                nonWhitespaceCount++;
                if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                    letterCount++;
                }
            }
        }

        double letterRatio = nonWhitespaceCount == 0 ? 0.0 : (double) letterCount / nonWhitespaceCount;

        if (wordCount < 10 || letterCount < 50 || letterRatio < 0.35) {
            throw new PdfScanException("Not able to scan the PDF. Please upload a clearer report.");
        }
    }
}

