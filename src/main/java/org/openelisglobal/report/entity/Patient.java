package org.openelisglobal.report.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "PATIENT")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "patient_seq")
    @SequenceGenerator(name = "patient_seq", sequenceName = "patient_seq", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PERSON_ID", nullable = false)
    private Person person;

    @Column(name = "NATIONAL_ID")
    private String nationalId;

    @Column(name = "GENDER")
    private String gender;

    @Column(name = "BIRTH_DATE")
    private java.sql.Timestamp birthDate;

    @Column(name = "EXTERNAL_ID")
    private String externalId;

    public Long getId() { return id; }
    public Person getPerson() { return person; }
    public void setPerson(Person person) { this.person = person; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public java.sql.Timestamp getBirthDate() { return birthDate; }
    public void setBirthDate(java.sql.Timestamp birthDate) { this.birthDate = birthDate; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
}
