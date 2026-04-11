package org.openelisglobal.report.metadata;

import jakarta.persistence.*;

@Entity
@Table(name = "dataset_fields")
public class DatasetField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    // e.g. "sample.accessionNumber", "result.value", "person.firstName"
    @Column(name = "field_path", nullable = false)
    private String fieldPath;

    // Display label for UI
    @Column(name = "label")
    private String label;

    // DATE, STRING, NUMBER
    @Column(name = "data_type")
    private String dataType;

    // Whether this field can be used as a filter
    @Column(name = "filterable")
    private boolean filterable;

    public Long getId() { return id; }
    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }
    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public boolean isFilterable() { return filterable; }
    public void setFilterable(boolean filterable) { this.filterable = filterable; }
}
