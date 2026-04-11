package org.openelisglobal.report.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ANALYSIS")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_seq")
    @SequenceGenerator(name = "analysis_seq", sequenceName = "analysis_seq", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TEST_ID")
    private Test test;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SAMPITEM_ID")
    private SampleItem sampleItem;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "REVISION")
    private String revision;

    public Long getId() { return id; }
    public Test getTest() { return test; }
    public void setTest(Test test) { this.test = test; }
    public SampleItem getSampleItem() { return sampleItem; }
    public void setSampleItem(SampleItem sampleItem) { this.sampleItem = sampleItem; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }
}
