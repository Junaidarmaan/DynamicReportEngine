package org.openelisglobal.report.seeder;

import org.openelisglobal.report.entity.*;
import org.openelisglobal.report.metadata.*;
import org.openelisglobal.report.service.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    private final EntityManager em;

    public DataSeeder(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedOpenElisData();
        seedMetadata();
    }

    // -------------------------------------------------------------------------
    // OpenELIS data — real table names, real column names
    // -------------------------------------------------------------------------
    private void seedOpenElisData() {

        // Tests
        Test hivVl = test("HIV Viral Load", "Y");
        Test cd4 = test("CD4 Count", "Y");
        Test malaria = test("Malaria RDT", "Y");
        Test hepB = test("Hepatitis B Surface Antigen", "Y");
        Test tb = test("TB GeneXpert", "Y");

        // Patients
        Patient p1 = patient("NID-1001", "M", "John", "Doe", "Kampala");
        Patient p2 = patient("NID-1002", "F", "Amina", "Hassan", "Nairobi");
        Patient p3 = patient("NID-1003", "M", "David", "Osei", "Accra");
        Patient p4 = patient("NID-1004", "F", "Fatima", "Nkosi", "Lusaka");
        Patient p5 = patient("NID-1005", "M", "Emmanuel", "Diallo", "Dakar");

        // Samples + Results
        sampleWithResult(p1, "ACC-2024-001", date("2024-01-10"), hivVl, "1200", "N");
        sampleWithResult(p1, "ACC-2024-002", date("2024-02-14"), cd4, "450", "N");
        sampleWithResult(p2, "ACC-2024-003", date("2024-01-20"), hivVl, "850", "N");
        sampleWithResult(p2, "ACC-2024-004", date("2024-03-05"), malaria, "Positive", "A");
        sampleWithResult(p3, "ACC-2024-005", date("2024-01-28"), cd4, "312", "N");
        sampleWithResult(p3, "ACC-2024-006", date("2024-02-19"), hepB, "Reactive", "A");
        sampleWithResult(p4, "ACC-2024-007", date("2024-02-01"), hivVl, "3400", "N");
        sampleWithResult(p4, "ACC-2024-008", date("2024-03-11"), tb, "MTB Detected", "A");
        sampleWithResult(p5, "ACC-2024-009", date("2024-01-15"), cd4, "620", "N");
        sampleWithResult(p5, "ACC-2024-010", date("2024-03-22"), hivVl, "560", "N");
        sampleWithResult(p1, "ACC-2024-011", date("2024-04-02"), tb, "MTB Not Detected", "A");
        sampleWithResult(p3, "ACC-2024-012", date("2024-04-10"), hivVl, "2100", "N");
    }

    // -------------------------------------------------------------------------
    // Metadata — the 5 new tables
    // -------------------------------------------------------------------------
    private void seedMetadata() {

        // ---- datasets ----
        Dataset analysisDataset = new Dataset();
        analysisDataset.setName("ANALYSIS_TEST");
        analysisDataset.setDescription("Reports rooted at Analysis — test execution data");
        analysisDataset.setRootEntity("org.openelisglobal.report.entity.Analysis");
        em.persist(analysisDataset);

        Dataset resultDataset = new Dataset();
        resultDataset.setName("RESULT_VALUE");
        resultDataset.setDescription("Reports rooted at Result — includes result values");
        resultDataset.setRootEntity("org.openelisglobal.report.entity.Result");
        em.persist(resultDataset);

        // ---- dataset_fields (whitelist) for RESULT_VALUE ----
        DatasetField fAccession = field(resultDataset, "sample.accessionNumber", "Accession Number", "STRING", true);
        DatasetField fReceived = field(resultDataset, "sample.receivedTimestamp", "Received Date", "DATE", true);
        DatasetField fNationalId = field(resultDataset, "patient.nationalId", "National ID", "STRING", true);
        DatasetField fFirstName = field(resultDataset, "person.firstName", "First Name", "STRING", false);
        DatasetField fLastName = field(resultDataset, "person.lastName", "Last Name", "STRING", false);
        DatasetField fGender = field(resultDataset, "patient.gender", "Gender", "STRING", true);
        DatasetField fTestDesc = field(resultDataset, "test.description", "Test Name", "STRING", true);
        DatasetField fResultVal = field(resultDataset, "result.value", "Result Value", "STRING", false);
        DatasetField fResultType = field(resultDataset, "result.resultType", "Result Type", "STRING", true);

        // ---- dataset_fields for ANALYSIS_TEST ----
        DatasetField afAccession = field(analysisDataset, "sample.accessionNumber", "Accession Number", "STRING", true);
        DatasetField afReceived = field(analysisDataset, "sample.receivedTimestamp", "Received Date", "DATE", true);
        DatasetField afStatus = field(analysisDataset, "analysis.status", "Analysis Status", "STRING", true);
        DatasetField afTestDesc = field(analysisDataset, "test.description", "Test Name", "STRING", true);
        DatasetField afNatId = field(analysisDataset, "patient.nationalId", "National ID", "STRING", true);
        DatasetField afFirstName = field(analysisDataset, "person.firstName", "First Name", "STRING", false);
        DatasetField afLastName = field(analysisDataset, "person.lastName", "Last Name", "STRING", false);

        // ---- Report 1: Patient Test Results by Date Range (RESULT_VALUE) ----
        Report report1 = new Report();
        report1.setName("Patient Test Results by Date Range");
        report1.setDescription("All test results for patients within a specified date range");
        report1.setReportType("STANDARD");
        report1.setDataset(resultDataset);
        em.persist(report1);

        // Columns for report 1
        reportColumn(report1, fAccession, 1, "Accession No.");
        reportColumn(report1, fNationalId, 2, "National ID");
        reportColumn(report1, fFirstName, 3, "First Name");
        reportColumn(report1, fLastName, 4, "Last Name");
        reportColumn(report1, fGender, 5, "Gender");
        reportColumn(report1, fTestDesc, 6, "Test");
        reportColumn(report1, fResultVal, 7, "Result");
        reportColumn(report1, fReceived, 8, "Received Date");

        // Filters for report 1
        reportFilter(report1, fReceived, "BETWEEN", "PROMPT_USER", null); // user picks date range
        reportFilter(report1, fResultType, "EQUAL", "FIXED", "N"); 
        
        // ---- Report 2: HIV Viral Load Results (RESULT_VALUE) ----
        Report report2 = new Report();
        report2.setName("HIV Viral Load Report");
        report2.setDescription("All HIV Viral Load results — test filter is fixed, only date is prompted");
        report2.setReportType("STANDARD");
        report2.setDataset(resultDataset);
        em.persist(report2);

        reportColumn(report2, fAccession, 1, "Accession No.");
        reportColumn(report2, fNationalId, 2, "National ID");
        reportColumn(report2, fFirstName, 3, "First Name");
        reportColumn(report2, fLastName, 4, "Last Name");
        reportColumn(report2, fTestDesc, 5, "Test");
        reportColumn(report2, fResultVal, 6, "Viral Load Value");
        reportColumn(report2, fReceived, 7, "Received Date");

        // FIXED: only HIV Viral Load — user cannot change this
        reportFilter(report2, fTestDesc, "EQUAL", "FIXED", "HIV Viral Load");
        // PROMPT_USER: date range
        reportFilter(report2, fReceived, "BETWEEN", "PROMPT_USER", null);

        // ---- Report 3: Analysis Status Report (ANALYSIS_TEST) ----
        Report report3 = new Report();
        report3.setName("Test Execution Status Report");
        report3.setDescription("Analysis status for all tests — rooted at Analysis");
        report3.setReportType("STANDARD");
        report3.setDataset(analysisDataset);
        em.persist(report3);

        reportColumn(report3, afAccession, 1, "Accession No.");
        reportColumn(report3, afNatId, 2, "National ID");
        reportColumn(report3, afFirstName, 3, "First Name");
        reportColumn(report3, afLastName, 4, "Last Name");
        reportColumn(report3, afTestDesc, 5, "Test");
        reportColumn(report3, afStatus, 6, "Status");
        reportColumn(report3, afReceived, 7, "Received Date");

        reportFilter(report3, afReceived, "BETWEEN", "PROMPT_USER", null);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Test test(String description, String isActive) {
        Test t = new Test();
        t.setDescription(description);
        t.setIsActive(isActive);
        em.persist(t);
        return t;
    }

    private Patient patient(String nationalId, String gender, String firstName, String lastName, String city) {
        Person person = new Person();
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setCity(city);
        em.persist(person);

        Patient p = new Patient();
        p.setPerson(person);
        p.setNationalId(nationalId);
        p.setGender(gender);
        em.persist(p);
        return p;
    }

    private void sampleWithResult(Patient patient, String accessionNumber, Timestamp received,
            Test test, String resultValue, String resultType) {
        Sample sample = new Sample();
        sample.setAccessionNumber(accessionNumber);
        sample.setReceivedTimestamp(received);
        sample.setStatus("C");
        em.persist(sample);

        SampleHuman sh = new SampleHuman();
        sh.setSampleId(String.valueOf(sample.getId()));
        sh.setPatientId(String.valueOf(patient.getId()));
        em.persist(sh);

        SampleItem si = new SampleItem();
        si.setSample(sample);
        si.setStatus("C");
        em.persist(si);

        Analysis analysis = new Analysis();
        analysis.setSampleItem(si);
        analysis.setTest(test);
        analysis.setStatus("C");
        analysis.setRevision("0");
        em.persist(analysis);

        Result result = new Result();
        result.setAnalysis(analysis);
        result.setResultValue(resultValue);
        result.setResultType(resultType);
        em.persist(result);
    }

    private DatasetField field(Dataset dataset, String path, String label, String dataType, boolean filterable) {
        DatasetField f = new DatasetField();
        f.setDataset(dataset);
        f.setFieldPath(path);
        f.setLabel(label);
        f.setDataType(dataType);
        f.setFilterable(filterable);
        em.persist(f);
        return f;
    }

    private void reportColumn(Report report, DatasetField field, int order, String label) {
        ReportColumn rc = new ReportColumn();
        rc.setReport(report);
        rc.setField(field);
        rc.setOrderIndex(order);
        rc.setColumnLabel(label);
        em.persist(rc);
    }

    private void reportFilter(Report report, DatasetField field, String operator,
            String filterType, String fixedValue) {
        ReportFilter rf = new ReportFilter();
        rf.setReport(report);
        rf.setField(field);
        rf.setOperator(operator);
        rf.setFilterType(filterType);
        rf.setFixedValue(fixedValue);
        em.persist(rf);
    }

    private Timestamp date(String d) {
        return Timestamp.valueOf(LocalDate.parse(d).atStartOfDay());
    }
}
