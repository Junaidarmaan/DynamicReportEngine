package org.openelisglobal.report.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "SAMPLE_HUMAN")
public class SampleHuman {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sample_human_seq")
    @SequenceGenerator(name = "sample_human_seq", sequenceName = "sample_human_seq", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    // Plain string — not a JPA association (matches HBM exactly)
    @Column(name = "SAMP_ID", nullable = false)
    private String sampleId;

    // Plain string — not a JPA association (matches HBM exactly)
    @Column(name = "PATIENT_ID")
    private String patientId;

    public Long getId() { return id; }
    public String getSampleId() { return sampleId; }
    public void setSampleId(String sampleId) { this.sampleId = sampleId; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
}
