
## Supported Field Values (Important for Testing)

Several fields in this system use short codes or magic strings that are **not self-explanatory**. This section documents every such field so you can test the API without guessing.

---

### `result.resultType` — Result Type Code

**Where used:** Filter value when filtering on the `result.resultType` field (RESULT_VALUE dataset only)

| Value | Meaning | Example result values in seed data |
|-------|---------|-----------------------------------|
| `N` | Numeric result | `1200`, `450`, `312`, `3400`, `620`, `560`, `2100` |
| `A` | Alpha/text result | `Positive`, `Reactive`, `MTB Detected`, `MTB Not Detected` |

**Example — FIXED filter that restricts a report to numeric results only:**
```json
{ "fieldId": 9, "operator": "EQUAL", "filterType": "FIXED", "fixedValue": "N" }
```

> **Common mistake:** Using full words like `"Numeric"` or `"Alpha"` — the engine expects only the single-character codes `N` or `A`.

---

### `analysis.status` — Analysis Status Code

**Where used:** Filter value or column output for the `analysis.status` field (both datasets)

| Value | Meaning |
|-------|---------|
| `C` | Completed |

In the real OpenELIS system, other statuses exist (e.g., `Y` = Not Started, `N` = Cancelled), but **all seed data in this prototype uses `C` only**. Filtering for any other value will return zero results.

**Example — FIXED filter to only show completed analyses:**
```json
{ "fieldId": 12, "operator": "EQUAL", "filterType": "FIXED", "fixedValue": "C" }
```

---

### `test.isActive` — Test Active Flag

**Where used:** Column output for `test.isActive` field (both datasets — present in the query engine path map but not in the seeded whitelist)

| Value | Meaning |
|-------|---------|
| `Y` | Active test |
| `N` | Inactive test |

All seeded tests use `Y`. This field is currently mapped in the query engine but **not whitelisted in `dataset_fields`**, so it cannot be selected as a column or filter via the admin UI unless a whitelist row is added.

---

### `patient.gender` — Patient Gender Code

**Where used:** Filter value or column output for `patient.gender` field (RESULT_VALUE dataset)

| Value | Meaning |
|-------|---------|
| `M` | Male |
| `F` | Female |

**Example — filter for female patients only:**
```json
{ "fieldId": 6, "operator": "EQUAL", "filterType": "FIXED", "fixedValue": "F" }
```

---

### `sample.status` / `sampleItem.status` — Sample Status Code

**Where used:** Column output for `sample.status` (present in query engine path map but not whitelisted)

| Value | Meaning |
|-------|---------|
| `C` | Completed |

All seeded samples and sample items use `C`. Like `test.isActive`, this field is mapped but not in the `dataset_fields` whitelist.

---

### `analysis.revision` — Analysis Revision Number

**Where used:** Column output for `analysis.revision` (present in query engine path map but not whitelisted)

| Value | Meaning |
|-------|---------|
| `0` | Original analysis (no revision) |

All seeded analyses use `0`. Stored as a String, not an integer.

---

### `filterType` — Filter Behavior Type

**Where used:** `POST /api/reports` request body → `filters[].filterType`

| Value | Meaning |
|-------|---------|
| `FIXED` | Value baked into the report definition. Applied silently at query time. Never shown to the lab tech. Requires `fixedValue` to be set. |
| `PROMPT_USER` | No value stored. The UI renders an input field and the lab tech provides the value at runtime. `fixedValue` should be `null`. |

**Example:**
```json
{
  "filters": [
    { "fieldId": 7, "operator": "EQUAL",   "filterType": "FIXED",       "fixedValue": "HIV Viral Load" },
    { "fieldId": 2, "operator": "BETWEEN", "filterType": "PROMPT_USER", "fixedValue": null }
  ]
}
```

> **Common mistake:** Using lowercase (`"fixed"`, `"prompt_user"`) — these are case-sensitive and must be uppercase.

---

### `operator` — Filter Operator

**Where used:** `POST /api/reports` request body → `filters[].operator`, and at query execution time in `POST /api/reports/run`

| Value | Meaning | Expected number of runtime values |
|-------|---------|----------------------------------|
| `EQUAL` | Exact match (`WHERE field = value`) | 1 value |
| `BETWEEN` | Range match (`WHERE field BETWEEN a AND b`) | 2 values (from, to) |
| `LIKE` | Substring match (`WHERE field LIKE '%value%'`) | 1 value |
| `IN` | Set membership (`WHERE field IN (a, b, c)`) | 1 or more values |

**Example — providing runtime filter values for a BETWEEN date filter:**
```json
POST /api/reports/preview
{
  "reportId": 1,
  "filters": {
    "sample.receivedTimestamp": ["2024-01-01", "2024-12-31"]
  }
}
```

> **Important:** `BETWEEN` with `dataType: DATE` requires exactly 2 values in `yyyy-MM-dd` format. The engine expands `from` to start-of-day and `to` to end-of-day automatically.

---

### `dataType` — Field Data Type

**Where used:** `dataset_fields.data_type` column, returned by `GET /api/datasets/{id}/fields`

| Value | Meaning | How filter values are parsed |
|-------|---------|------------------------------|
| `STRING` | Text field | Used as-is (no conversion) |
| `DATE` | Date/timestamp field | Must be `yyyy-MM-dd` format (e.g., `2024-01-15`) |
| `NUMBER` | Integer field | Parsed as `Long` — decimals will cause a validation error |

> **Common mistake:** Providing dates as `01/15/2024` or `Jan 15, 2024` — the engine only accepts ISO format `yyyy-MM-dd`.

---

### `reportType` — Report Routing Type

**Where used:** `reports.report_type` column (set automatically to `STANDARD` when creating via the admin API)

| Value | Meaning |
|-------|---------|
| `STANDARD` | Routed to the dynamic query engine — metadata-driven |
| `LEGACY_PIVOT` | Delegated to existing hardcoded Java report classes (not implemented in this prototype — will throw an error if you try to run one) |

You do not need to set this manually — the admin API always sets `report_type = STANDARD`.

---

### `test.description` — Test Name

**Where used:** Filter value when filtering on `test.description`

These are **exact match strings** — case and spacing matter:

| Value |
|-------|
| `HIV Viral Load` |
| `CD4 Count` |
| `Malaria RDT` |
| `Hepatitis B Surface Antigen` |
| `TB GeneXpert` |

**Example — FIXED filter for a specific test:**
```json
{ "fieldId": 7, "operator": "EQUAL", "filterType": "FIXED", "fixedValue": "HIV Viral Load" }
```

> **Common mistake:** Using `"HIV viral load"` (wrong case) or `"HIV VL"` (abbreviation) — the EQUAL operator requires an exact match.

---

### Quick Reference: Seeded Field IDs

When testing the API directly (e.g., via curl or Postman), you need field IDs. These are auto-generated; the IDs below apply to a **fresh startup** with no prior data:

**RESULT_VALUE dataset (id: 2):**

| Field ID | Field Path | Label | Data Type | Filterable |
|----------|-----------|-------|-----------|------------|
| 1 | `sample.accessionNumber` | Accession Number | STRING | ✅ |
| 2 | `sample.receivedTimestamp` | Received Date | DATE | ✅ |
| 3 | `patient.nationalId` | National ID | STRING | ✅ |
| 4 | `person.firstName` | First Name | STRING | ❌ |
| 5 | `person.lastName` | Last Name | STRING | ❌ |
| 6 | `patient.gender` | Gender | STRING | ✅ |
| 7 | `test.description` | Test Name | STRING | ✅ |
| 8 | `result.value` | Result Value | STRING | ❌ |
| 9 | `result.resultType` | Result Type | STRING | ✅ |

**ANALYSIS_TEST dataset (id: 1):**

| Field ID | Field Path | Label | Data Type | Filterable |
|----------|-----------|-------|-----------|------------|
| 10 | `sample.accessionNumber` | Accession Number | STRING | ✅ |
| 11 | `sample.receivedTimestamp` | Received Date | DATE | ✅ |
| 12 | `analysis.status` | Analysis Status | STRING | ✅ |
| 13 | `test.description` | Test Name | STRING | ✅ |
| 14 | `patient.nationalId` | National ID | STRING | ✅ |
| 15 | `person.firstName` | First Name | STRING | ❌ |
| 16 | `person.lastName` | Last Name | STRING | ❌ |

> **Note:** Field IDs are auto-incremented by H2 on each startup. If you modify `DataSeeder.java` or change the insertion order, these IDs will shift. Always verify via `GET /api/datasets/{id}/fields`.
