package org.openelisglobal.report.metadata;

import jakarta.persistence.*;

@Entity
@Table(name = "report_filters")
public class ReportFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private DatasetField field;

    // EQUAL, BETWEEN, IN, LIKE
    @Column(name = "operator", nullable = false)
    private String operator;

    // FIXED or PROMPT_USER
    @Column(name = "filter_type", nullable = false)
    private String filterType;

    // Only populated for FIXED filters. PROMPT_USER filters have null here.
    @Column(name = "fixed_value")
    private String fixedValue;

    public Long getId() { return id; }
    public Report getReport() { return report; }
    public void setReport(Report report) { this.report = report; }
    public DatasetField getField() { return field; }
    public void setField(DatasetField field) { this.field = field; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getFilterType() { return filterType; }
    public void setFilterType(String filterType) { this.filterType = filterType; }
    public String getFixedValue() { return fixedValue; }
    public void setFixedValue(String fixedValue) { this.fixedValue = fixedValue; }
}
