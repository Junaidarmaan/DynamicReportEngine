"""
OpenELIS Dynamic Report Engine - Prototype Backend
===================================================
Architecture mirrors the Spring Boot proposal exactly:
- Same 5 metadata tables (datasets, dataset_fields, reports, report_columns, report_filters)
- Same OpenELIS entity structure (PATIENT, PERSON, SAMPLE, SAMPLE_ITEM, ANALYSIS, RESULT, TEST, SAMPLE_HUMAN)
- Same query engine logic (whitelist validation, FIXED/PROMPT_USER filters, correlated subquery for patient)
- Same 4 REST endpoints

Uses SQLite (in-memory) to simulate H2 from the Spring Boot prototype.
"""

from flask import Flask, jsonify, request, Response
from flask_cors import CORS
import sqlite3
import csv
import io
from datetime import datetime, date

app = Flask(__name__)
CORS(app)

DB = ":memory:"
_conn = None

def get_conn():
    global _conn
    if _conn is None:
        _conn = sqlite3.connect(DB, check_same_thread=False)
        _conn.row_factory = sqlite3.Row
        _conn.execute("PRAGMA journal_mode=WAL")
    return _conn

# ─────────────────────────────────────────────
# SCHEMA — real OpenELIS table/column names
# ─────────────────────────────────────────────
SCHEMA = """
-- OpenELIS entities (real table + column names from HBM files)
CREATE TABLE PERSON (
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    FIRST_NAME TEXT,
    LAST_NAME  TEXT,
    MIDDLE_NAME TEXT,
    EMAIL TEXT,
    CITY TEXT
);

CREATE TABLE PATIENT (
    ID          INTEGER PRIMARY KEY AUTOINCREMENT,
    PERSON_ID   INTEGER NOT NULL REFERENCES PERSON(ID),
    NATIONAL_ID TEXT,
    GENDER      TEXT,
    BIRTH_DATE  TEXT,
    EXTERNAL_ID TEXT
);

CREATE TABLE SAMPLE (
    ID               INTEGER PRIMARY KEY AUTOINCREMENT,
    ACCESSION_NUMBER TEXT NOT NULL UNIQUE,
    RECEIVED_DATE    TEXT NOT NULL,
    STATUS           TEXT
);

-- SampleHuman: patientId and sampleId are plain strings (matches HBM exactly)
CREATE TABLE SAMPLE_HUMAN (
    ID         INTEGER PRIMARY KEY AUTOINCREMENT,
    SAMP_ID    TEXT NOT NULL,
    PATIENT_ID TEXT
);

CREATE TABLE SAMPLE_ITEM (
    ID      INTEGER PRIMARY KEY AUTOINCREMENT,
    SAMP_ID INTEGER REFERENCES SAMPLE(ID),
    STATUS  TEXT
);

CREATE TABLE TEST (
    ID          INTEGER PRIMARY KEY AUTOINCREMENT,
    DESCRIPTION TEXT NOT NULL UNIQUE,
    IS_ACTIVE   TEXT
);

CREATE TABLE ANALYSIS (
    ID          INTEGER PRIMARY KEY AUTOINCREMENT,
    TEST_ID     INTEGER REFERENCES TEST(ID),
    SAMPITEM_ID INTEGER REFERENCES SAMPLE_ITEM(ID),
    STATUS      TEXT,
    REVISION    TEXT
);

CREATE TABLE RESULT (
    ID          INTEGER PRIMARY KEY AUTOINCREMENT,
    ANALYSIS_ID INTEGER REFERENCES ANALYSIS(ID),
    VALUE       TEXT,
    RESULT_TYPE TEXT
);

-- 5 metadata tables (new — proposed by this GSoC project)
CREATE TABLE datasets (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL UNIQUE,
    description TEXT,
    root_entity TEXT
);

CREATE TABLE dataset_fields (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    dataset_id INTEGER REFERENCES datasets(id),
    field_path TEXT NOT NULL,
    label      TEXT,
    data_type  TEXT,
    filterable INTEGER DEFAULT 0
);

CREATE TABLE reports (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    description TEXT,
    report_type TEXT NOT NULL,
    dataset_id  INTEGER REFERENCES datasets(id)
);

CREATE TABLE report_columns (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    report_id    INTEGER REFERENCES reports(id),
    field_id     INTEGER REFERENCES dataset_fields(id),
    order_index  INTEGER NOT NULL,
    column_label TEXT
);

CREATE TABLE report_filters (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    report_id   INTEGER REFERENCES reports(id),
    field_id    INTEGER REFERENCES dataset_fields(id),
    operator    TEXT NOT NULL,
    filter_type TEXT NOT NULL,
    fixed_value TEXT
);
"""

# ─────────────────────────────────────────────
# SEED DATA
# ─────────────────────────────────────────────
def seed(conn):
    c = conn.cursor()

    # Tests
    tests = [
        ("HIV Viral Load", "Y"),
        ("CD4 Count", "Y"),
        ("Malaria RDT", "Y"),
        ("Hepatitis B Surface Antigen", "Y"),
        ("TB GeneXpert", "Y"),
    ]
    c.executemany("INSERT INTO TEST (DESCRIPTION, IS_ACTIVE) VALUES (?,?)", tests)

    # Patients (person + patient)
    patients_data = [
        ("John",    "Doe",    "Kampala", "NID-1001", "M"),
        ("Amina",   "Hassan", "Nairobi", "NID-1002", "F"),
        ("David",   "Osei",   "Accra",   "NID-1003", "M"),
        ("Fatima",  "Nkosi",  "Lusaka",  "NID-1004", "F"),
        ("Emmanuel","Diallo", "Dakar",   "NID-1005", "M"),
    ]
    patient_ids = []
    for first, last, city, nid, gender in patients_data:
        c.execute("INSERT INTO PERSON (FIRST_NAME, LAST_NAME, CITY) VALUES (?,?,?)", (first, last, city))
        person_id = c.lastrowid
        c.execute("INSERT INTO PATIENT (PERSON_ID, NATIONAL_ID, GENDER) VALUES (?,?,?)", (person_id, nid, gender))
        patient_ids.append(c.lastrowid)

    # Samples + results
    records = [
        (0, "ACC-2024-001", "2024-01-10", 0, "1200",              "N"),
        (0, "ACC-2024-002", "2024-02-14", 1, "450",               "N"),
        (1, "ACC-2024-003", "2024-01-20", 0, "850",               "N"),
        (1, "ACC-2024-004", "2024-03-05", 2, "Positive",          "A"),
        (2, "ACC-2024-005", "2024-01-28", 1, "312",               "N"),
        (2, "ACC-2024-006", "2024-02-19", 3, "Reactive",          "A"),
        (3, "ACC-2024-007", "2024-02-01", 0, "3400",              "N"),
        (3, "ACC-2024-008", "2024-03-11", 4, "MTB Detected",      "A"),
        (4, "ACC-2024-009", "2024-01-15", 1, "620",               "N"),
        (4, "ACC-2024-010", "2024-03-22", 0, "560",               "N"),
        (0, "ACC-2024-011", "2024-04-02", 4, "MTB Not Detected",  "A"),
        (2, "ACC-2024-012", "2024-04-10", 0, "2100",              "N"),
    ]
    for (pat_idx, accession, received, test_idx, result_val, result_type) in records:
        c.execute("INSERT INTO SAMPLE (ACCESSION_NUMBER, RECEIVED_DATE, STATUS) VALUES (?,?,?)",
                  (accession, received, "C"))
        sample_id = c.lastrowid

        # SampleHuman links patient to sample via plain strings (matches HBM)
        c.execute("INSERT INTO SAMPLE_HUMAN (SAMP_ID, PATIENT_ID) VALUES (?,?)",
                  (str(sample_id), str(patient_ids[pat_idx])))

        c.execute("INSERT INTO SAMPLE_ITEM (SAMP_ID, STATUS) VALUES (?,?)", (sample_id, "C"))
        si_id = c.lastrowid

        # test_idx+1 because SQLite autoincrement starts at 1
        c.execute("INSERT INTO ANALYSIS (TEST_ID, SAMPITEM_ID, STATUS, REVISION) VALUES (?,?,?,?)",
                  (test_idx + 1, si_id, "C", "0"))
        analysis_id = c.lastrowid

        c.execute("INSERT INTO RESULT (ANALYSIS_ID, VALUE, RESULT_TYPE) VALUES (?,?,?)",
                  (analysis_id, result_val, result_type))

    # ── metadata ──────────────────────────────

    # datasets
    c.execute("""INSERT INTO datasets (name, description, root_entity) VALUES
        ('RESULT_VALUE', 'Reports rooted at Result — includes result values',
         'org.openelisglobal.report.entity.Result')""")
    rv_dataset = c.lastrowid

    c.execute("""INSERT INTO datasets (name, description, root_entity) VALUES
        ('ANALYSIS_TEST', 'Reports rooted at Analysis — test execution data',
         'org.openelisglobal.report.entity.Analysis')""")
    at_dataset = c.lastrowid

    # dataset_fields — whitelist for RESULT_VALUE
    def field(dataset_id, path, label, dtype, filterable):
        c.execute("""INSERT INTO dataset_fields (dataset_id, field_path, label, data_type, filterable)
                     VALUES (?,?,?,?,?)""", (dataset_id, path, label, dtype, 1 if filterable else 0))
        return c.lastrowid

    fAccession  = field(rv_dataset, "sample.accessionNumber",   "Accession Number", "STRING", True)
    fReceived   = field(rv_dataset, "sample.receivedTimestamp", "Received Date",    "DATE",   True)
    fNationalId = field(rv_dataset, "patient.nationalId",       "National ID",      "STRING", True)
    fFirstName  = field(rv_dataset, "person.firstName",         "First Name",       "STRING", False)
    fLastName   = field(rv_dataset, "person.lastName",          "Last Name",        "STRING", False)
    fGender     = field(rv_dataset, "patient.gender",           "Gender",           "STRING", True)
    fTestDesc   = field(rv_dataset, "test.description",         "Test Name",        "STRING", True)
    fResultVal  = field(rv_dataset, "result.value",             "Result Value",     "STRING", False)
    fResultType = field(rv_dataset, "result.resultType",        "Result Type",      "STRING", True)

    # dataset_fields — whitelist for ANALYSIS_TEST
    afAccession = field(at_dataset, "sample.accessionNumber",   "Accession Number", "STRING", True)
    afReceived  = field(at_dataset, "sample.receivedTimestamp", "Received Date",    "DATE",   True)
    afStatus    = field(at_dataset, "analysis.status",          "Analysis Status",  "STRING", True)
    afTestDesc  = field(at_dataset, "test.description",         "Test Name",        "STRING", True)
    afNatId     = field(at_dataset, "patient.nationalId",       "National ID",      "STRING", True)
    afFirstName = field(at_dataset, "person.firstName",         "First Name",       "STRING", False)
    afLastName  = field(at_dataset, "person.lastName",          "Last Name",        "STRING", False)

    def report(name, desc, rtype, dataset_id):
        c.execute("INSERT INTO reports (name, description, report_type, dataset_id) VALUES (?,?,?,?)",
                  (name, desc, rtype, dataset_id))
        return c.lastrowid

    def col(report_id, field_id, order, label):
        c.execute("INSERT INTO report_columns (report_id, field_id, order_index, column_label) VALUES (?,?,?,?)",
                  (report_id, field_id, order, label))

    def filt(report_id, field_id, operator, filter_type, fixed_value=None):
        c.execute("""INSERT INTO report_filters (report_id, field_id, operator, filter_type, fixed_value)
                     VALUES (?,?,?,?,?)""", (report_id, field_id, operator, filter_type, fixed_value))

    # Report 1 — all results by date range
    r1 = report("Patient Test Results by Date Range",
                "All test results for patients within a specified date range",
                "STANDARD", rv_dataset)
    col(r1, fAccession,  1, "Accession No.")
    col(r1, fNationalId, 2, "National ID")
    col(r1, fFirstName,  3, "First Name")
    col(r1, fLastName,   4, "Last Name")
    col(r1, fGender,     5, "Gender")
    col(r1, fTestDesc,   6, "Test")
    col(r1, fResultVal,  7, "Result")
    col(r1, fReceived,   8, "Received Date")
    filt(r1, fReceived, "BETWEEN", "PROMPT_USER")   # user picks dates

    # Report 2 — HIV Viral Load only (test filter is FIXED)
    r2 = report("HIV Viral Load Report",
                "HIV Viral Load results only — test is fixed, date range is prompted",
                "STANDARD", rv_dataset)
    col(r2, fAccession,  1, "Accession No.")
    col(r2, fNationalId, 2, "National ID")
    col(r2, fFirstName,  3, "First Name")
    col(r2, fLastName,   4, "Last Name")
    col(r2, fTestDesc,   5, "Test")
    col(r2, fResultVal,  6, "Viral Load Value")
    col(r2, fReceived,   7, "Received Date")
    filt(r2, fTestDesc,  "EQUAL",   "FIXED",       "HIV Viral Load")  # FIXED — hidden from user
    filt(r2, fReceived,  "BETWEEN", "PROMPT_USER")                    # user picks dates

    # Report 3 — Analysis status (ANALYSIS_TEST dataset)
    r3 = report("Test Execution Status Report",
                "Analysis status for all tests — rooted at Analysis entity",
                "STANDARD", at_dataset)
    col(r3, afAccession, 1, "Accession No.")
    col(r3, afNatId,     2, "National ID")
    col(r3, afFirstName, 3, "First Name")
    col(r3, afLastName,  4, "Last Name")
    col(r3, afTestDesc,  5, "Test")
    col(r3, afStatus,    6, "Status")
    col(r3, afReceived,  7, "Received Date")
    filt(r3, afReceived, "BETWEEN", "PROMPT_USER")

    conn.commit()

# ─────────────────────────────────────────────
# QUERY ENGINE
# ─────────────────────────────────────────────
ALLOWED_OPERATORS = {"EQUAL", "BETWEEN", "IN", "LIKE"}

class ReportValidationException(Exception):
    pass

def execute_report(report_id, user_filter_values, conn):
    c = conn.cursor()

    report = c.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
    if not report:
        raise ReportValidationException(f"Report not found: {report_id}")

    if report["report_type"] == "LEGACY_PIVOT":
        raise ReportValidationException("Legacy reports not supported in this prototype")

    dataset = c.execute("SELECT * FROM datasets WHERE id=?", (report["dataset_id"],)).fetchone()
    dataset_name = dataset["name"]

    columns = c.execute("""
        SELECT rc.order_index, rc.column_label, df.field_path, df.data_type
        FROM report_columns rc
        JOIN dataset_fields df ON rc.field_id = df.id
        WHERE rc.report_id = ? ORDER BY rc.order_index
    """, (report_id,)).fetchall()

    filters = c.execute("""
        SELECT rf.operator, rf.filter_type, rf.fixed_value, df.field_path, df.data_type, df.label
        FROM report_filters rf
        JOIN dataset_fields df ON rf.field_id = df.id
        WHERE rf.report_id = ?
    """, (report_id,)).fetchall()

    # whitelist
    whitelist = {row["field_path"] for row in c.execute(
        "SELECT field_path FROM dataset_fields WHERE dataset_id=?", (report["dataset_id"],)
    ).fetchall()}

    # validate column paths
    for col in columns:
        if col["field_path"] not in whitelist:
            raise ReportValidationException(f"Field not in whitelist: {col['field_path']}")

    # Build SQL — same logic as JPA Criteria engine but in SQL for prototype
    # The join chain mirrors exactly what the Criteria API would produce
    if dataset_name == "RESULT_VALUE":
        base_query = """
            SELECT
                r.VALUE               as "result.value",
                r.RESULT_TYPE         as "result.resultType",
                a.STATUS              as "analysis.status",
                a.REVISION            as "analysis.revision",
                s.ACCESSION_NUMBER    as "sample.accessionNumber",
                s.RECEIVED_DATE       as "sample.receivedTimestamp",
                s.STATUS              as "sample.status",
                t.DESCRIPTION         as "test.description",
                t.IS_ACTIVE           as "test.isActive",
                p.NATIONAL_ID         as "patient.nationalId",
                p.GENDER              as "patient.gender",
                p.EXTERNAL_ID         as "patient.externalId",
                per.FIRST_NAME        as "person.firstName",
                per.LAST_NAME         as "person.lastName",
                per.EMAIL             as "person.email",
                per.CITY              as "person.city"
            FROM RESULT r
            JOIN ANALYSIS a        ON r.ANALYSIS_ID  = a.ID
            JOIN SAMPLE_ITEM si    ON a.SAMPITEM_ID   = si.ID
            JOIN SAMPLE s          ON si.SAMP_ID      = s.ID
            JOIN TEST t            ON a.TEST_ID        = t.ID
            -- Correlated subquery equivalent: link patient via SampleHuman plain strings
            JOIN SAMPLE_HUMAN sh   ON sh.SAMP_ID = CAST(s.ID AS TEXT)
            JOIN PATIENT p         ON CAST(p.ID AS TEXT) = sh.PATIENT_ID
            JOIN PERSON per        ON p.PERSON_ID = per.ID
        """
    else:  # ANALYSIS_TEST
        base_query = """
            SELECT
                a.STATUS              as "analysis.status",
                a.REVISION            as "analysis.revision",
                s.ACCESSION_NUMBER    as "sample.accessionNumber",
                s.RECEIVED_DATE       as "sample.receivedTimestamp",
                s.STATUS              as "sample.status",
                t.DESCRIPTION         as "test.description",
                t.IS_ACTIVE           as "test.isActive",
                p.NATIONAL_ID         as "patient.nationalId",
                p.GENDER              as "patient.gender",
                p.EXTERNAL_ID         as "patient.externalId",
                per.FIRST_NAME        as "person.firstName",
                per.LAST_NAME         as "person.lastName",
                per.EMAIL             as "person.email",
                per.CITY              as "person.city"
            FROM ANALYSIS a
            JOIN SAMPLE_ITEM si    ON a.SAMPITEM_ID   = si.ID
            JOIN SAMPLE s          ON si.SAMP_ID      = s.ID
            JOIN TEST t            ON a.TEST_ID        = t.ID
            JOIN SAMPLE_HUMAN sh   ON sh.SAMP_ID = CAST(s.ID AS TEXT)
            JOIN PATIENT p         ON CAST(p.ID AS TEXT) = sh.PATIENT_ID
            JOIN PERSON per        ON p.PERSON_ID = per.ID
        """

    where_clauses = []
    params = []

    for f in filters:
        field_path = f["field_path"]
        operator   = f["operator"]
        data_type  = f["data_type"]
        filter_type = f["filter_type"]

        if operator not in ALLOWED_OPERATORS:
            raise ReportValidationException(f"Operator not allowed: {operator}")
        if field_path not in whitelist:
            raise ReportValidationException(f"Filter field not in whitelist: {field_path}")

        col_expr = f'"{field_path}"'

        if filter_type == "FIXED":
            values = [f["fixed_value"]]
        else:  # PROMPT_USER
            values = user_filter_values.get(field_path)
            if not values:
                raise ReportValidationException(f"Missing required filter: {field_path}")

        if operator == "EQUAL":
            where_clauses.append(f"{col_expr} = ?")
            params.append(values[0])
        elif operator == "BETWEEN":
            if len(values) < 2:
                raise ReportValidationException("BETWEEN requires 2 values")
            where_clauses.append(f"{col_expr} BETWEEN ? AND ?")
            params.extend(values[:2])
        elif operator == "IN":
            placeholders = ",".join("?" * len(values))
            where_clauses.append(f"{col_expr} IN ({placeholders})")
            params.extend(values)
        elif operator == "LIKE":
            escaped = values[0].replace("%", "\\%").replace("_", "\\_")
            where_clauses.append(f"{col_expr} LIKE ?")
            params.append(f"%{escaped}%")

    full_query = f"SELECT * FROM ({base_query}) WHERE " + \
                 (" AND ".join(where_clauses) if where_clauses else "1=1") + \
                 " LIMIT 5000"

    rows = c.execute(full_query, params).fetchall()

    # Project only the selected columns in order
    result = []
    for row in rows:
        result.append({col["field_path"]: row[col["field_path"]] for col in columns})

    return result, [col["column_label"] for col in columns], [col["field_path"] for col in columns]

# ─────────────────────────────────────────────
# REST ENDPOINTS
# ─────────────────────────────────────────────

@app.get("/api/reports")
def get_reports():
    conn = get_conn()
    rows = conn.execute("""
        SELECT r.id, r.name, r.description, r.report_type, d.name as dataset
        FROM reports r JOIN datasets d ON r.dataset_id = d.id
    """).fetchall()
    return jsonify([dict(r) for r in rows])

@app.get("/api/reports/<int:report_id>/filters")
def get_filters(report_id):
    conn = get_conn()
    rows = conn.execute("""
        SELECT df.field_path, df.label, df.data_type, rf.operator
        FROM report_filters rf
        JOIN dataset_fields df ON rf.field_id = df.id
        WHERE rf.report_id = ? AND rf.filter_type = 'PROMPT_USER'
    """, (report_id,)).fetchall()
    return jsonify([dict(r) for r in rows])

@app.post("/api/reports/preview")
def preview_report():
    body = request.json
    report_id = body.get("reportId")
    user_filters = body.get("filters", {})
    try:
        rows, headers, field_paths = execute_report(report_id, user_filters, get_conn())
        return jsonify({
            "headers": headers,
            "fieldPaths": field_paths,
            "rows": rows,
            "count": len(rows)
        })
    except ReportValidationException as e:
        return jsonify({"error": str(e)}), 400

@app.post("/api/reports/run")
def run_report():
    body = request.json
    report_id = body.get("reportId")
    user_filters = body.get("filters", {})
    try:
        rows, headers, field_paths = execute_report(report_id, user_filters, get_conn())
        output = io.StringIO()
        writer = csv.writer(output)
        writer.writerow(headers)
        for row in rows:
            writer.writerow([row.get(fp, "") or "" for fp in field_paths])
        csv_content = output.getvalue()
        return Response(
            csv_content,
            mimetype="text/csv",
            headers={"Content-Disposition": f"attachment; filename=report-{report_id}.csv"}
        )
    except ReportValidationException as e:
        return jsonify({"error": str(e)}), 400

# ─────────────────────────────────────────────
# INIT
# ─────────────────────────────────────────────
def init():
    conn = get_conn()
    for stmt in SCHEMA.strip().split(";"):
        s = stmt.strip()
        if s:
            conn.execute(s)
    conn.commit()
    seed(conn)

if __name__ == "__main__":
    init()
    print("\n OpenELIS Dynamic Report Engine running at http://localhost:5000\n")
    app.run(debug=False, port=5000)
