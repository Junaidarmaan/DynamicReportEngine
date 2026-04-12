package org.openelisglobal.report.adhoc.controller;

import org.openelisglobal.report.engine.ReportValidationException;
import org.openelisglobal.report.adhoc.service.AdminReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminReportService adminReportService;

    public AdminController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    // GET /api/datasets
    // Returns all datasets — admin picks one to define the report root
    @GetMapping("/datasets")
    public List<Map<String, Object>> getDatasets() {
        return adminReportService.getAllDatasets();
    }

    // GET /api/datasets/{id}/fields
    // Returns whitelisted fields for a dataset
    // Admin uses this to pick columns and filters from a known-safe list
    @GetMapping("/datasets/{id}/fields")
    public List<Map<String, Object>> getDatasetFields(@PathVariable Long id) {
        return adminReportService.getFieldsForDataset(id);
    }

    // POST /api/reports
    // Creates a new ad-hoc report definition — inserts into all 5 metadata tables
    // No Java class needed, no redeployment needed
    @PostMapping("/reports")
    public ResponseEntity<Map<String, Object>> createReport(@RequestBody CreateReportRequest request) {
        Map<String, Object> result = adminReportService.createReport(request);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(ReportValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ReportValidationException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
