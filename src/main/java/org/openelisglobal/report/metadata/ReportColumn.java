package org.openelisglobal.report.metadata;

import jakarta.persistence.*;

@Entity
@Table(name = "report_columns")
public class ReportColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private DatasetField field;

    // Controls CSV column ordering
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    // CSV column header
    @Column(name = "column_label")
    private String columnLabel;

    public Long getId() { return id; }
    public Report getReport() { return report; }
    public void setReport(Report report) { this.report = report; }
    public DatasetField getField() { return field; }
    public void setField(DatasetField field) { this.field = field; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public String getColumnLabel() { return columnLabel; }
    public void setColumnLabel(String columnLabel) { this.columnLabel = columnLabel; }
}
