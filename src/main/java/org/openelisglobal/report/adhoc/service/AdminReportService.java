package org.openelisglobal.report.adhoc.service;

import org.openelisglobal.report.adhoc.controller.CreateReportRequest;
import org.openelisglobal.report.engine.ReportValidationException;
import org.openelisglobal.report.metadata.*;
import org.openelisglobal.report.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminReportService {

    private static final Set<String> ALLOWED_OPERATORS = Set.of("EQUAL", "BETWEEN", "IN", "LIKE");
    private static final Set<String> ALLOWED_FILTER_TYPES = Set.of("FIXED", "PROMPT_USER");

    private final DatasetRepository datasetRepository;
    private final DatasetFieldRepository datasetFieldRepository;
    private final ReportRepository reportRepository;
    private final ReportColumnRepository reportColumnRepository;
    private final ReportFilterRepository reportFilterRepository;

    public AdminReportService(
            DatasetRepository datasetRepository,
            DatasetFieldRepository datasetFieldRepository,
            ReportRepository reportRepository,
            ReportColumnRepository reportColumnRepository,
            ReportFilterRepository reportFilterRepository) {
        this.datasetRepository = datasetRepository;
        this.datasetFieldRepository = datasetFieldRepository;
        this.reportRepository = reportRepository;
        this.reportColumnRepository = reportColumnRepository;
        this.reportFilterRepository = reportFilterRepository;
    }

    // GET /api/datasets — list all datasets for the admin UI dropdown
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllDatasets() {
        return datasetRepository.findAll().stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "name", d.getName(),
                        "description", d.getDescription()))
                .toList();
    }

    // GET /api/datasets/{id}/fields — return whitelisted fields for a dataset
    // Admin uses this to know what columns/filters are available to pick from
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFieldsForDataset(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ReportValidationException("Dataset not found: " + datasetId));

        return datasetFieldRepository.findByDataset(dataset).stream()
                .map(f -> Map.<String, Object>of(
                        "id", f.getId(),
                        "fieldPath", f.getFieldPath(),
                        "label", f.getLabel(),
                        "dataType", f.getDataType(),
                        "filterable", f.isFilterable()))
                .toList();
    }

    // POST /api/reports — create a new ad-hoc report definition
    @Transactional
    public Map<String, Object> createReport(CreateReportRequest request) {

        // --- Validate request ---
        if (request.name() == null || request.name().isBlank()) {
            throw new ReportValidationException("Report name is required");
        }
        if (request.datasetId() == null) {
            throw new ReportValidationException("Dataset is required");
        }
        if (request.columns() == null || request.columns().isEmpty()) {
            throw new ReportValidationException("At least one column is required");
        }

        // --- Load dataset ---
        Dataset dataset = datasetRepository.findById(request.datasetId())
                .orElseThrow(() -> new ReportValidationException("Dataset not found: " + request.datasetId()));

        // --- Build whitelist for this dataset ---
        Set<Long> whitelistedFieldIds = datasetFieldRepository.findByDataset(dataset)
                .stream()
                .map(DatasetField::getId)
                .collect(Collectors.toSet());

        // --- Validate all column field IDs are in whitelist ---
        for (CreateReportRequest.ColumnRequest col : request.columns()) {
            if (!whitelistedFieldIds.contains(col.fieldId())) {
                throw new ReportValidationException(
                        "Column field ID " + col.fieldId() + " is not whitelisted for dataset " + dataset.getName());
            }
        }

        // --- Validate all filter field IDs and operators ---
        if (request.filters() != null) {
            for (CreateReportRequest.FilterRequest filter : request.filters()) {
                if (!whitelistedFieldIds.contains(filter.fieldId())) {
                    throw new ReportValidationException(
                            "Filter field ID " + filter.fieldId() + " is not whitelisted for dataset "
                                    + dataset.getName());
                }
                if (!ALLOWED_OPERATORS.contains(filter.operator())) {
                    throw new ReportValidationException("Operator not allowed: " + filter.operator());
                }
                if (!ALLOWED_FILTER_TYPES.contains(filter.filterType())) {
                    throw new ReportValidationException("Filter type must be FIXED or PROMPT_USER");
                }
                if ("FIXED".equals(filter.filterType()) &&
                        (filter.fixedValue() == null || filter.fixedValue().isBlank())) {
                    throw new ReportValidationException("FIXED filter must have a fixedValue");
                }
            }
        }

        // --- Create the report ---
        Report report = new Report();
        report.setName(request.name().trim());
        report.setDescription(request.description() != null ? request.description().trim() : "");
        report.setReportType("STANDARD");
        report.setDataset(dataset);
        reportRepository.save(report);

        // --- Create columns ---
        for (CreateReportRequest.ColumnRequest colReq : request.columns()) {
            DatasetField field = datasetFieldRepository.findById(colReq.fieldId())
                    .orElseThrow(() -> new ReportValidationException("Field not found: " + colReq.fieldId()));

            ReportColumn col = new ReportColumn();
            col.setReport(report);
            col.setField(field);
            col.setOrderIndex(colReq.orderIndex());
            col.setColumnLabel(colReq.columnLabel() != null && !colReq.columnLabel().isBlank()
                    ? colReq.columnLabel()
                    : field.getLabel());
            reportColumnRepository.save(col);
        }

        // --- Create filters ---
        if (request.filters() != null) {
            for (CreateReportRequest.FilterRequest filterReq : request.filters()) {
                DatasetField field = datasetFieldRepository.findById(filterReq.fieldId())
                        .orElseThrow(() -> new ReportValidationException("Field not found: " + filterReq.fieldId()));

                ReportFilter filter = new ReportFilter();
                filter.setReport(report);
                filter.setField(field);
                filter.setOperator(filterReq.operator());
                filter.setFilterType(filterReq.filterType());
                filter.setFixedValue(filterReq.fixedValue());
                reportFilterRepository.save(filter);
            }
        }

        return Map.of(
                "id", report.getId(),
                "name", report.getName(),
                "message", "Report created successfully");
    }
}
