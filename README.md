# OpenELIS Dynamic Report Engine — GSoC 2026 Prototype

**Applicant:** Pinjari Junaid  
**Organization:** OpenELIS Global  
**Mentor:** Mutesasira Moses (@mossyy)  
**Project:** Metadata-Driven Dynamic Query Engine for Patient Reporting  

---

## What This Prototype Demonstrates

The current OpenELIS reporting system requires a developer to write a new Java class, add an entry to `if-else` chain in `ReportImplementationFactory.java`, and redeploy the application every time a new report is needed.

This prototype proves that reports can instead be **database records** — not Java classes. A lab administrator can define a new report through a UI, selecting fields and filters from a pre-approved whitelist. The engine reads this definition at runtime and constructs a safe, type-checked query dynamically. No Java is written. No redeployment happens. The new report is live instantly.

This is the core argument of the GSoC proposal, demonstrated end to end.

---

## Project Structure

```
openelis-report-engine/
├── backend.py                          # Runnable Python demo (same logic as Spring Boot)
├── index.html                          # Lab tech UI — run reports, download CSV
├── admin.html                          # Admin UI — create new ad-hoc reports
├── pom.xml                             # Spring Boot build file
└── src/main/java/org/openelisglobal/report/
    ├── ReportEngineApplication.java    # Spring Boot entry point
    ├── entity/                         # Real OpenELIS entities (exact table/column names from HBM)
    │   ├── Person.java                 # PERSON table
    │   ├── Patient.java                # PATIENT table
    │   ├── Sample.java                 # SAMPLE table
    │   ├── SampleHuman.java            # SAMPLE_HUMAN table
    │   ├── SampleItem.java             # SAMPLE_ITEM table
    │   ├── Test.java                   # TEST table
    │   ├── Analysis.java               # ANALYSIS table
    │   └── Result.java                 # RESULT table
    ├── metadata/                       # 5 new metadata tables (proposed by this project)
    │   ├── Dataset.java                # datasets table
    │   ├── DatasetField.java           # dataset_fields table (whitelist)
    │   ├── Report.java                 # reports table
    │   ├── ReportColumn.java           # report_columns table
    │   └── ReportFilter.java           # report_filters table
    ├── engine/
    │   ├── DynamicReportQueryEngine.java   # Core: builds JPA Criteria queries from metadata
    │   └── ReportValidationException.java  # Thrown when any security check fails
    ├── controller/
    │   ├── ReportController.java       # Lab tech endpoints (run, preview, list)
    │   └── AdminController.java        # Admin endpoints (create reports, list fields)
    ├── service/
    │   ├── ReportExecutionService.java # Orchestrates report execution
    │   ├── AdminReportService.java     # Handles ad-hoc report creation + validation
    │   └── CsvRenderer.java           # Converts query results to CSV
    ├── repository/                     # JPA repositories (one interface per file)
    │   ├── ReportRepository.java
    │   ├── ReportColumnRepository.java
    │   ├── ReportFilterRepository.java
    │   ├── DatasetFieldRepository.java
    │   └── DatasetRepository.java
    └── seeder/
        └── DataSeeder.java            # Seeds OpenELIS dummy data + metadata on startup
```

---

## The Problem This Solves

### Current OpenELIS Reporting Architecture

Every report in OpenELIS today is a hardcoded Java class:

```
ReportImplementationFactory.java
  ├── if (reportName.equals("patient_report_1")) return new PatientReport1();
  ├── if (reportName.equals("hivVL_report"))     return new HivViralLoadReport();
  ├── if (reportName.equals("cd4_report"))       return new CD4Report();
  └── ... 70+ more branches
```

Adding a new report means:
1. Writing a new Java class
2. Adding a new branch to the factory
3. Writing or modifying a JRXML template
4. Redeploying the entire application

This blocks lab administrators from defining reports themselves. Every new reporting need requires developer intervention.

### This Project's Solution

Reports become database records:

```
reports table
  ├── id=1  name="Patient Test Results by Date Range"  report_type=STANDARD
  ├── id=2  name="HIV Viral Load Report"               report_type=STANDARD
  └── id=3  name="Test Execution Status Report"        report_type=STANDARD

report_columns table
  ├── report_id=1, field=sample.accessionNumber, order=1
  ├── report_id=1, field=patient.nationalId,     order=2
  └── ...

report_filters table
  ├── report_id=2, field=test.description,        FIXED,       value="HIV Viral Load"
  └── report_id=2, field=sample.receivedTimestamp, PROMPT_USER, value=null
```

The engine reads these rows at runtime and constructs a JPA Criteria query dynamically. Adding a new report is inserting rows — no Java, no redeploy.

---

## The 5 Metadata Tables

These are the new tables this project adds to OpenELIS. Everything else — `ANALYSIS`, `RESULT`, `SAMPLE`, `PATIENT` etc. — already exists in OpenELIS.

### `datasets`
Defines the two query roots. Every report must start from one of these.

| id | name | description |
|----|------|-------------|
| 1 | ANALYSIS_TEST | Root: Analysis. Use for test execution data. No result value available. |
| 2 | RESULT_VALUE | Root: Result. Use when result values are needed. |

**Why two roots?** JPA Criteria queries require a single root entity. `result.value` is unreachable from `Root<Analysis>` because Result points *to* Analysis, not the other way. If a report needs result values, it must start from `Root<Result>` and join Analysis from there.

### `dataset_fields`
The whitelist. Every field that can ever appear in any query — as a column or as a filter — must have a row here. The engine rejects any field not in this table before touching the query builder.

| id | dataset | field_path | label | data_type | filterable |
|----|---------|------------|-------|-----------|------------|
| 1 | RESULT_VALUE | sample.accessionNumber | Accession Number | STRING | true |
| 2 | RESULT_VALUE | sample.receivedTimestamp | Received Date | DATE | true |
| 7 | RESULT_VALUE | test.description | Test Name | STRING | true |
| 8 | RESULT_VALUE | result.value | Result Value | STRING | false |

### `reports`
One row per report definition. `report_type` is the routing field.

- `STANDARD` → routed to the dynamic query engine
- `LEGACY_PIVOT` → delegated to the existing Java implementation (existing ARV, EID, VL reports are untouched)

### `report_columns`
Defines which fields appear in the CSV output for a given report, and in what order. `order_index` controls column sequence.

### `report_filters`
The most important design decision in the schema. Two fundamentally different filter behaviors:

**FIXED** — value is stored in the database row. Applied unconditionally at query time. Never returned to the UI. Cannot be overridden by user input. This is how row-level access control works — a report can have `test.description = 'HIV Viral Load'` baked in permanently so a lab tech running that report can never see other test types.

**PROMPT_USER** — no value stored. The UI reads this filter and renders an input field. The user fills in the value at runtime. The value is type-validated before reaching the query builder.

---

## The Query Engine

`DynamicReportQueryEngine.java` is the core of the prototype. It converts metadata table rows into an executable JPA Criteria query with no string SQL and no JPQL concatenation.

### Two Root Modes

```
ANALYSIS_TEST → Root<Analysis>
  └── JOIN SampleItem  (analysis.sampitem_id = sampleitem.id)
        └── JOIN Sample    (sampleitem.samp_id = sample.id)
  └── JOIN Test        (analysis.test_id = test.id)

RESULT_VALUE → Root<r>
  └── JOIN Analysis    (result.analysis_id = analysis.id)
        └── JOIN SampleItem  (analysis.sampitem_id = sampleitem.id)
              └── JOIN Sample    (sampleitem.samp_id = sample.id)
        └── JOIN Test    (analysis.test_id = test.id)
```

### Patient Scoping via Correlated Subquery

`SampleHuman.patientId` and `SampleHuman.sampleId` are **plain String properties** in the OpenELIS HBM mapping — not mapped JPA associations. A direct Criteria join is impossible. Patient scoping therefore uses a correlated subquery:

```java
Subquery<String> shSubquery = cq.subquery(String.class);
Root<SampleHuman> shRoot = shSubquery.from(SampleHuman.class);
shSubquery.select(shRoot.get("patientId"))
          .where(cb.equal(
              shRoot.get("sampleId"),
              sampleJoin.get("id").as(String.class)
          ));

// Link patient
predicates.add(cb.equal(
    patientRoot.get("id").as(String.class),
    shSubquery
));
```

This was discovered by reading the actual OpenELIS HBM mapping files — `SampleHuman.hbm.xml` maps `PATIENT_ID` and `SAMP_ID` as plain `LIMSStringNumberUserType` properties, not as `many-to-one` associations.

### Security Layer

Before any query object is built, the engine enforces:

- All selected field paths must exist in `dataset_fields` for the given dataset
- All filter operators must be from the allowed set: `EQUAL`, `BETWEEN`, `IN`, `LIKE`
- Input types are validated against `data_type` (`DATE`, `NUMBER`, `STRING`)
- FIXED filters are applied unconditionally — they cannot be skipped or overridden
- Row cap enforced via `setMaxResults(5000)` — prevents unbounded result sets

---

## REST API

### Lab Tech Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/reports` | List all report definitions |
| GET | `/api/reports/{id}/filters` | Get PROMPT_USER filters for a report (for UI rendering) |
| POST | `/api/reports/preview` | Execute report, return JSON (for table preview) |
| POST | `/api/reports/run` | Execute report, return CSV download |

### Admin Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/datasets` | List available datasets |
| GET | `/api/datasets/{id}/fields` | Get whitelisted fields for a dataset |
| POST | `/api/reports` | Create a new ad-hoc report definition |

### Request/Response Examples

**Run a report:**
```json
POST /api/reports/preview
{
  "reportId": 2,
  "filters": {
    "sample.receivedTimestamp": ["2024-01-01", "2024-12-31"]
  }
}
```

Response — only HIV Viral Load rows because `test.description = 'HIV Viral Load'` is a FIXED filter on report 2:
```json
{
  "count": 5,
  "headers": ["Accession No.", "National ID", "First Name", "Last Name", "Test", "Viral Load Value", "Received Date"],
  "rows": [
    { "sample.accessionNumber": "ACC-2024-001", "patient.nationalId": "NID-1001", "person.firstName": "John", ... }
  ]
}
```

**Create a new ad-hoc report:**
```json
POST /api/reports
{
  "name": "CD4 Count Tests by Date Range",
  "description": "All CD4 count test executions filtered by date",
  "datasetId": 1,
  "columns": [
    { "fieldId": 10, "orderIndex": 1, "columnLabel": "Accession No." },
    { "fieldId": 14, "orderIndex": 2, "columnLabel": "National ID" },
    { "fieldId": 13, "orderIndex": 3, "columnLabel": "Test" }
  ],
  "filters": [
    { "fieldId": 13, "operator": "EQUAL",   "filterType": "FIXED",       "fixedValue": "CD4 Count" },
    { "fieldId": 11, "operator": "BETWEEN", "filterType": "PROMPT_USER", "fixedValue": null }
  ]
}
```

Response:
```json
{ "id": 4, "name": "CD4 Count Tests by Date Range", "message": "Report created successfully" }
```

The new report is immediately available in `GET /api/reports` and can be run without restarting the application.

---

## Seeded Data

`DataSeeder.java` runs automatically on startup (`CommandLineRunner`). It seeds:

**OpenELIS entities (real table names):**
- 5 patients: John Doe, Amina Hassan, David Osei, Fatima Nkosi, Emmanuel Diallo
- 5 tests: HIV Viral Load, CD4 Count, Malaria RDT, Hepatitis B Surface Antigen, TB GeneXpert
- 12 samples with full chain: `SAMPLE → SAMPLE_HUMAN → SAMPLE_ITEM → ANALYSIS → RESULT`

**Metadata (new tables):**
- 2 datasets: `RESULT_VALUE`, `ANALYSIS_TEST`
- 16 whitelisted fields across both datasets
- 3 pre-defined reports to demonstrate different configurations

---

## How to Run

### Option 1 — Spring Boot (recommended)

Requirements: Java 17+, Maven

```bash
cd openelis-report-engine
mvn spring-boot:run
```

Backend starts at `http://localhost:8080`. Data is seeded automatically. Open `index.html` in your browser.

H2 console available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:openelis`).

### Option 2 — Python demo

Requirements: Python 3.9+

```bash
pip install flask flask-cors
python backend.py
```

Backend starts at `http://localhost:5000`. Same logic, same endpoints, same data — written in Python/Flask to allow immediate demo without Maven setup. Change `const API = 'http://localhost:5000/api'` in both HTML files to use this.

---

## The Two UIs

### `index.html` — Lab Tech View

- Dropdown populated from `GET /api/reports`
- On report select: fetches `PROMPT_USER` filters from `GET /api/reports/{id}/filters` and renders them as date pickers or text inputs
- FIXED filters are never shown — they are silently applied on the backend
- "Run report" button hits `POST /api/reports/preview` and renders a table
- "Download CSV" button hits `POST /api/reports/run` and triggers a file download
- Link to `admin.html` for report creation

### `admin.html` — Admin View

A 5-step form for creating new reports without writing Java:

1. Enter report name and description
2. Select dataset (ANALYSIS_TEST or RESULT_VALUE) — hint explains what each root can access
3. Pick columns from the whitelist (checkboxes populated from `GET /api/datasets/{id}/fields`)
4. Add filters — pick field, operator, and filter type. FIXED requires a value input. PROMPT_USER leaves it empty.
5. Save — hits `POST /api/reports`. On success, shows confirmation with link back to `index.html`

The new report appears in the lab tech dropdown immediately. No restart needed.

---

## Key Design Decisions Explained

### Why not just use JPQL string building?

String-based query construction is an injection surface. The Criteria API builds predicates programmatically — every join, selection, and predicate is a typed Java object. There is no string that a malicious input could escape from.

### Why a fixed traversal tree instead of arbitrary joins?

Allowing arbitrary joins would require the engine to understand the entire OpenELIS entity graph at runtime, which introduces unbounded complexity and security risk. The fixed tree — verified against the actual HBM mapping files — means the engine only ever produces queries that a security-conscious developer would have written by hand. Adding a new hop requires only a new registry entry.

### Why LEGACY_PIVOT routing?

Existing complex reports (ARV cohort, EID, VL crosstab) use pivot logic that does not map cleanly to a generic column-based output. These reports are preserved exactly as they are. The routing field `report_type` ensures they are delegated to the existing Java implementation untouched. The new engine only handles reports explicitly defined as `STANDARD`.

### Why two datasets instead of one universal root?

`result.value` is unreachable from `Root<Analysis>` in JPA. The `RESULT` table has a foreign key pointing *to* `ANALYSIS`, so traversal must start from `Root<r>` to include result values. Rather than hiding this constraint, the design surfaces it as a first-class concept — admins choose the appropriate root when defining a report, and the hint in the UI explains what each root can access.

---

## What This Prototype Does Not Cover

These are intentional scope limits, not missing features:

- **No aggregation** (COUNT, SUM, AVG) — the architecture supports it as a post-GSoC extension without structural changes
- **CSV output only** — no PDF or JRXML rendering
- **Max 3 join hops** — enforced by the depth check in the query validator
- **Two datasets only** — Phase 1 of the proposal; additional roots can be added as new dataset rows
- **No authentication** — out of scope for prototype; OpenELIS has its own auth layer
- **H2 in-memory database** — replaced by PostgreSQL in the real OpenELIS integration

---

## Relation to the GSoC Proposal

This prototype directly implements the core components described in the proposal:

| Proposal Component | Prototype Implementation |
|---|---|
| Metadata schema (5 tables) | `metadata/` package, seeded via `DataSeeder.java` |
| Field path resolver | `buildPathMap()` in `DynamicReportQueryEngine.java` |
| Join builder with depth enforcement | Join chain construction in `runQuery()` |
| Predicate builder (EQUAL, BETWEEN, IN, LIKE) | `buildPredicate()` with type validation |
| FIXED/PROMPT_USER filter types | `ReportFilter.filterType` + enforcement in engine |
| Patient correlated subquery | `shSubquery` in `runQuery()` |
| STANDARD vs LEGACY_PIVOT routing | `report_type` field + routing in `ReportExecutionService` |
| Whitelist security layer | `whitelistedPaths` check before query build |
| Row cap | `setMaxResults(5000)` |
| CSV rendering | `CsvRenderer.java` |
| Admin UI for ad-hoc report creation | `admin.html` + `AdminController.java` + `AdminReportService.java` |
| Lab tech UI | `index.html` + `ReportController.java` |
