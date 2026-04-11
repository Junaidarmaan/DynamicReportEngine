package org.openelisglobal.report.controller;

import org.openelisglobal.report.engine.ReportValidationException;
import org.openelisglobal.report.metadata.*;
import org.openelisglobal.report.repository.ReportColumnRepository;
import org.openelisglobal.report.service.CsvRenderer;
import org.openelisglobal.report.service.ReportExecutionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ReportController {

        private final ReportExecutionService executionService;
        private final CsvRenderer csvRenderer;
        private final ReportColumnRepository columnRepository;

        public ReportController(ReportExecutionService executionService,
                        CsvRenderer csvRenderer,
                        ReportColumnRepository columnRepository) {
                this.executionService = executionService;
                this.csvRenderer = csvRenderer;
                this.columnRepository = columnRepository;
        }

        // GET /api/reports — list all report definitions
        @GetMapping("/reports")
        public List<Map<String, Object>> getReports() {
                return executionService.getAllReports().stream()
                                .map(r -> Map.<String, Object>of(
                                                "id", r.getId(),
                                                "name", r.getName(),
                                                "description", r.getDescription(),
                                                "reportType", r.getReportType(),
                                                "dataset", r.getDataset().getName()))
                                .toList();
        }

        // GET /api/reports/{id}/filters — return only PROMPT_USER filters for UI
        // rendering
        @GetMapping("/reports/{id}/filters")
        public List<Map<String, Object>> getFilters(@PathVariable Long id) {
                return executionService.getPromptUserFilters(id).stream()
                                .map(f -> Map.<String,Object>of(
                                                "fieldPath", f.getField().getFieldPath(),
                                                "label", f.getField().getLabel(),
                                                "operator", f.getOperator(),
                                                "dataType", f.getField().getDataType()))
                                .toList();
        }

        // POST /api/reports/run — execute a report and return CSV
        @PostMapping("/reports/run")
        public ResponseEntity<byte[]> runReport(@RequestBody RunReportRequest request) {
                List<Map<String, Object>> rows = executionService.runReport(
                                request.reportId(), request.filters());

                // Get columns for CSV rendering
                Report report = executionService.getAllReports().stream()
                                .filter(r -> r.getId().equals(request.reportId()))
                                .findFirst()
                                .orElseThrow(() -> new ReportValidationException("Report not found"));

                List<ReportColumn> columns = columnRepository.findByReportOrderByOrderIndex(report);
                String csv = csvRenderer.render(columns, rows);

                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"report-" + request.reportId() + ".csv\"")
                                .contentType(MediaType.parseMediaType("text/csv"))
                                .body(csv.getBytes());
        }

        // POST /api/reports/preview — same as run but returns JSON (for UI table
        // preview)
        @PostMapping("/reports/preview")
        public ResponseEntity<Map<String, Object>> previewReport(@RequestBody RunReportRequest request) {
                List<Map<String, Object>> rows = executionService.runReport(
                                request.reportId(), request.filters());

                Report report = executionService.getAllReports().stream()
                                .filter(r -> r.getId().equals(request.reportId()))
                                .findFirst()
                                .orElseThrow(() -> new ReportValidationException("Report not found"));

                List<ReportColumn> columns = columnRepository.findByReportOrderByOrderIndex(report);
                List<String> headers = columns.stream().map(ReportColumn::getColumnLabel).toList();
                List<String> fieldPaths = columns.stream()
                                .map(c -> c.getField().getFieldPath()).toList();

                // Format timestamps in rows
                List<Map<String, Object>> formattedRows = rows.stream().map(row -> {
                        Map<String, Object> formatted = new java.util.LinkedHashMap<>();
                        for (String fp : fieldPaths) {
                                Object val = row.get(fp);
                                formatted.put(fp, val instanceof java.sql.Timestamp ts
                                                ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(ts)
                                                : val != null ? val.toString() : "");
                        }
                        return formatted;
                }).toList();

                return ResponseEntity.ok(Map.of(
                                "headers", headers,
                                "fieldPaths", fieldPaths,
                                "rows", formattedRows,
                                "count", rows.size()));
        }

        @ExceptionHandler(ReportValidationException.class)
        public ResponseEntity<Map<String, String>> handleValidation(ReportValidationException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
}

record RunReportRequest(Long reportId, Map<String, List<String>> filters) {
}
