package org.openelisglobal.report.service;

import org.openelisglobal.report.engine.DynamicReportQueryEngine;
import org.openelisglobal.report.engine.ReportValidationException;
import org.openelisglobal.report.metadata.*;
import org.openelisglobal.report.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportExecutionService {

    private final ReportRepository reportRepository;
    private final ReportColumnRepository columnRepository;
    private final ReportFilterRepository filterRepository;
    private final DatasetFieldRepository fieldRepository;
    private final DynamicReportQueryEngine queryEngine;

    public ReportExecutionService(
            ReportRepository reportRepository,
            ReportColumnRepository columnRepository,
            ReportFilterRepository filterRepository,
            DatasetFieldRepository fieldRepository,
            DynamicReportQueryEngine queryEngine) {
        this.reportRepository = reportRepository;
        this.columnRepository = columnRepository;
        this.filterRepository = filterRepository;
        this.fieldRepository = fieldRepository;
        this.queryEngine = queryEngine;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> runReport(Long reportId, Map<String, List<String>> userFilterValues) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportValidationException("Report not found: " + reportId));

        if ("LEGACY_PIVOT".equals(report.getReportType())) {
            throw new ReportValidationException("Legacy reports not supported in this prototype");
        }

        List<ReportColumn> columns = columnRepository.findByReportOrderByOrderIndex(report);
        List<ReportFilter> filters = filterRepository.findByReport(report);

        // Build whitelist from dataset_fields
        Set<String> whitelistedPaths = fieldRepository.findByDataset(report.getDataset())
                .stream()
                .map(DatasetField::getFieldPath)
                .collect(Collectors.toSet());

        return queryEngine.execute(report, columns, filters, userFilterValues, whitelistedPaths);
    }

    @Transactional(readOnly = true)
    public List<Report> getAllReports() {
        return reportRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ReportFilter> getPromptUserFilters(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportValidationException("Report not found: " + reportId));
        return filterRepository.findByReport(report).stream()
                .filter(f -> "PROMPT_USER".equals(f.getFilterType()))
                .collect(Collectors.toList());
    }
}
