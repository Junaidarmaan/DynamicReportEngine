package org.openelisglobal.report.entity;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "SAMPLE")
public class Sample {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sample_seq")
    @SequenceGenerator(name = "sample_seq", sequenceName = "sample_seq", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ACCESSION_NUMBER", nullable = false, unique = true)
    private String accessionNumber;

    @Column(name = "RECEIVED_DATE", nullable = false)
    private Timestamp receivedTimestamp;

    @Column(name = "STATUS")
    private String status;

    public Long getId() { return id; }
    public String getAccessionNumber() { return accessionNumber; }
    public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }
    public Timestamp getReceivedTimestamp() { return receivedTimestamp; }
    public void setReceivedTimestamp(Timestamp receivedTimestamp) { this.receivedTimestamp = receivedTimestamp; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
