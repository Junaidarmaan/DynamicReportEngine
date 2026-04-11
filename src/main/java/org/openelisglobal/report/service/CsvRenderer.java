package org.openelisglobal.report.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.openelisglobal.report.metadata.ReportColumn;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

@Component
public class CsvRenderer {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public String render(List<ReportColumn> columns, List<Map<String, Object>> rows) {
        // Sort columns by order_index
        columns.sort((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()));

        String[] headers = columns.stream()
                .map(ReportColumn::getColumnLabel)
                .toArray(String[]::new);

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.withHeader(headers))) {
            for (Map<String, Object> row : rows) {
                Object[] values = columns.stream()
                        .map(col -> formatValue(row.get(col.getField().getFieldPath())))
                        .toArray();
                printer.printRecord(values);
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV rendering failed", e);
        }
        return sw.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof Timestamp ts) {
            return DATE_FORMAT.format(ts);
        }
        return value.toString();
    }
}
