package org.openelisglobal.report.adhoc.controller;

import java.util.List;

public record CreateReportRequest(
                String name,
                String description,
                Long datasetId,
                List<ColumnRequest> columns,
                List<FilterRequest> filters) {
        public record ColumnRequest(
                        Long fieldId,
                        int orderIndex,
                        String columnLabel) {
        }

        public record FilterRequest(
                        Long fieldId,
                        String operator,
                        String filterType, // FIXED or PROMPT_USER
                        String fixedValue // null if PROMPT_USER
        ) {
        }
}
