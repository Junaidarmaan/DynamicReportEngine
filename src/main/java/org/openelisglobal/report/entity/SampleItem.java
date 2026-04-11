package org.openelisglobal.report.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "SAMPLE_ITEM")
public class SampleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sample_item_seq")
    @SequenceGenerator(name = "sample_item_seq", sequenceName = "sample_item_seq", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SAMP_ID")
    private Sample sample;

    @Column(name = "STATUS")
    private String status;

    public Long getId() { return id; }
    public Sample getSample() { return sample; }
    public void setSample(Sample sample) { this.sample = sample; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
