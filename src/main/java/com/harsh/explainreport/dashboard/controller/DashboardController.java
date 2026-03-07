package com.harsh.explainreport.dashboard.controller;

import com.harsh.explainreport.dashboard.dto.AnalysisResponse;
import com.harsh.explainreport.dashboard.dto.ChatRequest;
import com.harsh.explainreport.dashboard.dto.ChatResponse;
import com.harsh.explainreport.dashboard.dto.GuidedQuery;
import com.harsh.explainreport.dashboard.dto.InsightRequest;
import com.harsh.explainreport.dashboard.dto.InsightResponse;
import com.harsh.explainreport.dashboard.exception.PdfScanException;
import com.harsh.explainreport.dashboard.service.ExportService;
import com.harsh.explainreport.dashboard.service.GroqService;
import com.harsh.explainreport.dashboard.service.PdfService;
import com.harsh.explainreport.dashboard.service.RedFlagService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class DashboardController {

    private final GroqService groqService;
    private final PdfService pdfService;
    private final ExportService exportService;
    private final RedFlagService redFlagService;

    public DashboardController(GroqService groqService, PdfService pdfService, ExportService exportService, RedFlagService redFlagService) {
        this.groqService = groqService;
        this.pdfService = pdfService;
        this.exportService = exportService;
        this.redFlagService = redFlagService;
    }

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        model.addAttribute("summary", session.getAttribute("summary"));
        model.addAttribute("keyFindings", session.getAttribute("keyFindings"));
        model.addAttribute("riskFlags", session.getAttribute("riskFlags"));
        model.addAttribute("questions", session.getAttribute("questions"));
        model.addAttribute("nextSteps", session.getAttribute("nextSteps"));
        model.addAttribute("redFlagActive", session.getAttribute("redFlagActive"));
        model.addAttribute("redFlagFindings", session.getAttribute("redFlagFindings"));
        model.addAttribute("redFlagInstructions", session.getAttribute("redFlagInstructions"));
        return "index";
    }

    @PostMapping("/clear")
    public String clear(HttpSession session) {
        clearSessionReport(session);
        return "redirect:/";
    }

    @PostMapping("/upload")
    public String uploadPdf(@RequestParam("file") MultipartFile file, Model model, HttpSession session) {

        String text;
        try {
            text = pdfService.extractText(file);
        } catch (PdfScanException e) {
            clearSessionReport(session);
            model.addAttribute("scanError", e.getMessage());
            return "index";
        } catch (RuntimeException e) {
            clearSessionReport(session);
            model.addAttribute("scanError", "Not able to scan the PDF. Please upload a clearer report.");
            return "index";
        }

        session.setAttribute("reportText", text);

        var redFlagReport = redFlagService.evaluate(text);
        session.setAttribute("redFlagActive", redFlagReport.isActive());
        session.setAttribute("redFlagFindings", redFlagReport.getCriticalFindings());
        session.setAttribute("redFlagInstructions", redFlagReport.getInstructions());

        AnalysisResponse result = groqService.analyzeText(text);
        session.setAttribute("summary", result.getSummary());
        session.setAttribute("keyFindings", result.getKeyFindings());
        session.setAttribute("riskFlags", result.getRiskFlags());
        session.setAttribute("questions", result.getDoctorQuestions());
        session.setAttribute("nextSteps", result.getNextSteps());

        model.addAttribute("redFlagActive", redFlagReport.isActive());
        model.addAttribute("redFlagFindings", redFlagReport.getCriticalFindings());
        model.addAttribute("redFlagInstructions", redFlagReport.getInstructions());
        model.addAttribute("summary", result.getSummary());
        model.addAttribute("keyFindings", result.getKeyFindings());
        model.addAttribute("riskFlags", result.getRiskFlags());
        model.addAttribute("questions", result.getDoctorQuestions());
        model.addAttribute("nextSteps", result.getNextSteps());

        return "index";
    }


    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, HttpSession session) {

        String reportText = (String) session.getAttribute("reportText");
        if (reportText == null || reportText.isBlank()) {
            return ResponseEntity.badRequest().body(ChatResponse.error("Please upload a report first."));
        }

        String question = request == null ? null : request.getQuestion();
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(ChatResponse.error("Please enter a question."));
        }

        var answer = groqService.chatWithReport(reportText, question);

        return ResponseEntity.ok(ChatResponse.success(answer));
    }

    @PostMapping(path = "/insight", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<InsightResponse> insight(@RequestBody InsightRequest request, HttpSession session) {
        String reportText = (String) session.getAttribute("reportText");
        if (reportText == null || reportText.isBlank()) {
            return ResponseEntity.badRequest().body(InsightResponse.error("Please upload a report first."));
        }

        String type = request == null ? null : request.getType();
        GuidedQuery query = GuidedQuery.fromType(type);
        if (query == null) {
            return ResponseEntity.badRequest().body(InsightResponse.error("Invalid query type."));
        }

        var items = groqService.guidedInsights(reportText, query);
        session.setAttribute("lastInsightTitle", query.getTitle());
        session.setAttribute("lastInsightItems", items);
        return ResponseEntity.ok(InsightResponse.success(query.getTitle(), items));
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam("format") String format, HttpSession session) {
        var summary = exportService.getList(session, "summary");
        var keyFindings = exportService.getList(session, "keyFindings");
        var riskFlags = exportService.getList(session, "riskFlags");
        var questions = exportService.getList(session, "questions");
        var nextSteps = exportService.getList(session, "nextSteps");
        var lastInsightTitle = exportService.getString(session, "lastInsightTitle");
        var lastInsightItems = exportService.getList(session, "lastInsightItems");
        var redFlagActive = exportService.getBoolean(session, "redFlagActive");
        var redFlagFindings = exportService.getList(session, "redFlagFindings");
        var redFlagInstructions = exportService.getList(session, "redFlagInstructions");

        if (exportService.isAllEmpty(summary, keyFindings, riskFlags, questions, nextSteps, lastInsightItems, redFlagFindings)) {
            return ResponseEntity.badRequest().build();
        }

        String exportText = exportService.buildExportText(
                summary,
                keyFindings,
                riskFlags,
                questions,
                nextSteps,
                redFlagActive,
                redFlagFindings,
                redFlagInstructions,
                lastInsightTitle,
                lastInsightItems
        );

        if ("txt".equalsIgnoreCase(format)) {
            byte[] body = exportService.toTextBytes(exportText);
            return exportService.textResponse(body, "explainreport.txt");
        }

        byte[] body = exportService.toPdfBytes(exportText);
        return exportService.pdfResponse(body, "explainreport.pdf");
    }

    private void clearSessionReport(HttpSession session) {
        session.removeAttribute("reportText");
        session.removeAttribute("summary");
        session.removeAttribute("keyFindings");
        session.removeAttribute("riskFlags");
        session.removeAttribute("questions");
        session.removeAttribute("nextSteps");
        session.removeAttribute("lastInsightTitle");
        session.removeAttribute("lastInsightItems");
        session.removeAttribute("redFlagActive");
        session.removeAttribute("redFlagFindings");
        session.removeAttribute("redFlagInstructions");
    }
}

