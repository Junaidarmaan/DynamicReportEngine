package org.openelisglobal.report.metadata;

import jakarta.persistence.*;

@Entity
@Table(name = "datasets")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name; // ANALYSIS_TEST or RESULT_VALUE

    @Column(name = "description")
    private String description;

    @Column(name = "root_entity")
    private String rootEntity; // fully qualified JPA entity name

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRootEntity() { return rootEntity; }
    public void setRootEntity(String rootEntity) { this.rootEntity = rootEntity; }
}
