package org.openelisglobal.report.engine;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import org.openelisglobal.report.entity.*;
import org.openelisglobal.report.metadata.*;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Dynamic report query engine.
 *
 * Converts a report definition (rows from 5 metadata tables) into a single
 * JPA Criteria query. No string SQL, no JPQL concatenation.
 *
 * Two root modes:
 * ANALYSIS_TEST -> Root<Analysis>
 * RESULT_VALUE -> Root<Result>
 *
 * Patient scoping via correlated subquery on SampleHuman — because
 * SampleHuman.patientId and SampleHuman.sampleId are plain String
 * properties in the HBM mapping, not mapped JPA associations.
 */
@Component
public class DynamicReportQueryEngine {

    private static final int MAX_RESULTS = 5000;
    private static final Set<String> ALLOWED_OPERATORS = Set.of("EQUAL", "BETWEEN", "IN", "LIKE");

    private final EntityManager em;

    public DynamicReportQueryEngine(EntityManager em) {
        this.em = em;
    }

    public List<Map<String, Object>> execute(
            Report report,
            List<ReportColumn> columns,
            List<ReportFilter> filters,
            Map<String, List<String>> userFilterValues,
            Set<String> whitelistedPaths) {

        String datasetName = report.getDataset().getName();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();

        // Choose root based on dataset
        Join<?, Analysis> analysisJoin = null;
        Join<?, SampleItem> sampleItemJoin;
        Join<SampleItem, Sample> sampleJoin;
        Join<?, Test> testJoin;

        if ("RESULT_VALUE".equals(datasetName)) {
            Root<Result> resultRoot = cq.from(Result.class);
            Join<Result, Analysis> aj = resultRoot.join("analysis", JoinType.LEFT);
            analysisJoin = aj;
            sampleItemJoin = aj.join("sampleItem", JoinType.LEFT);
            testJoin = aj.join("test", JoinType.LEFT);
            sampleJoin = sampleItemJoin.join("sample", JoinType.LEFT);
            return runQuery(cb, cq, resultRoot, analysisJoin, sampleJoin,
                    testJoin, datasetName, columns, filters, userFilterValues, whitelistedPaths);

        } else if ("ANALYSIS_TEST".equals(datasetName)) {
            Root<Analysis> analysisRoot = cq.from(Analysis.class);
            sampleItemJoin = analysisRoot.join("sampleItem", JoinType.LEFT);
            testJoin = analysisRoot.join("test", JoinType.LEFT);
            sampleJoin = sampleItemJoin.join("sample", JoinType.LEFT);
            return runQuery(cb, cq, analysisRoot, null, sampleJoin,
                    testJoin, datasetName, columns, filters, userFilterValues, whitelistedPaths);

        } else {
            throw new ReportValidationException("Unknown dataset: " + datasetName);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> runQuery(
            CriteriaBuilder cb,
            CriteriaQuery<Tuple> cq,
            Root<?> root,
            Join<?, Analysis> analysisJoin,
            Join<SampleItem, Sample> sampleJoin,
            Join<?, Test> testJoin,
            String datasetName,
            List<ReportColumn> columns,
            List<ReportFilter> filters,
            Map<String, List<String>> userFilterValues,
            Set<String> whitelistedPaths) {

        // Patient + Person via additional roots
        Root<Patient> patientRoot = cq.from(Patient.class);
        Join<Patient, Person> personJoin = patientRoot.join("person", JoinType.LEFT);

        // SampleHuman correlated subquery: find patientId for the current sample
        Subquery<String> shSubquery = cq.subquery(String.class);
        Root<SampleHuman> shRoot = shSubquery.from(SampleHuman.class);
        shSubquery.select(shRoot.get("patientId"))
                .where(cb.equal(
                        shRoot.get("sampleId"),
                        sampleJoin.get("id").as(String.class)));

        // Build field path resolver
        Map<String, Path<?>> pathMap = buildPathMap(
                root, analysisJoin, sampleJoin, testJoin, patientRoot, personJoin, datasetName);

        // Validate all column paths
        for (ReportColumn col : columns) {
            String fp = col.getField().getFieldPath();
            if (!whitelistedPaths.contains(fp))
                throw new ReportValidationException("Field not in whitelist: " + fp);
            if (!pathMap.containsKey(fp))
                throw new ReportValidationException("Unknown field path: " + fp);
        }

        // Build selections
        columns.sort(Comparator.comparingInt(ReportColumn::getOrderIndex));
        List<Selection<?>> selections = new ArrayList<>();
        List<String> selectionKeys = new ArrayList<>();

        for (ReportColumn col : columns) {
            String fp = col.getField().getFieldPath();
            selections.add(pathMap.get(fp).alias(fp));
            selectionKeys.add(fp);
        }
        cq.multiselect(selections);

        // Build predicates
        List<Predicate> predicates = new ArrayList<>();

        // Link patient to sample via SampleHuman correlated subquery
        predicates.add(cb.equal(patientRoot.get("id").as(String.class), shSubquery));

        // Process filters
        for (ReportFilter filter : filters) {
            String fieldPath = filter.getField().getFieldPath();

            if (!ALLOWED_OPERATORS.contains(filter.getOperator()))
                throw new ReportValidationException("Operator not allowed: " + filter.getOperator());
            if (!whitelistedPaths.contains(fieldPath))
                throw new ReportValidationException("Filter field not in whitelist: " + fieldPath);

            Path<?> fieldPathObj = pathMap.get(fieldPath);
            if (fieldPathObj == null)
                throw new ReportValidationException("Cannot resolve field: " + fieldPath);

            if ("FIXED".equals(filter.getFilterType())) {
                predicates.add(buildPredicate(cb, fieldPathObj, filter.getOperator(),
                        List.of(filter.getFixedValue()), filter.getField().getDataType()));
            } else if ("PROMPT_USER".equals(filter.getFilterType())) {
                List<String> userValues = userFilterValues.get(fieldPath);
                if (userValues == null || userValues.isEmpty())
                    throw new ReportValidationException("Missing required filter: " + fieldPath);
                predicates.add(buildPredicate(cb, fieldPathObj, filter.getOperator(),
                        userValues, filter.getField().getDataType()));
            }
        }

        cq.where(predicates.toArray(new Predicate[0]));

        List<Tuple> tuples = em.createQuery(cq).setMaxResults(MAX_RESULTS).getResultList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple tuple : tuples) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String key : selectionKeys)
                row.put(key, tuple.get(key));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Path<?>> buildPathMap(
            Root<?> root,
            Join<?, Analysis> analysisJoin,
            Join<SampleItem, Sample> sampleJoin,
            Join<?, Test> testJoin,
            Root<Patient> patientRoot,
            Join<Patient, Person> personJoin,
            String datasetName) {

        Map<String, Path<?>> map = new LinkedHashMap<>();

        if ("RESULT_VALUE".equals(datasetName)) {
            map.put("result.value", root.get("resultValue"));
            map.put("result.resultType", root.get("resultType"));
            map.put("analysis.status", analysisJoin.get("status"));
            map.put("analysis.revision", analysisJoin.get("revision"));
        } else {
            map.put("analysis.status", root.get("status"));
            map.put("analysis.revision", root.get("revision"));
        }

        map.put("sample.accessionNumber", sampleJoin.get("accessionNumber"));
        map.put("sample.receivedTimestamp", sampleJoin.get("receivedTimestamp"));
        map.put("sample.status", sampleJoin.get("status"));
        map.put("test.description", testJoin.get("description"));
        map.put("test.isActive", testJoin.get("isActive"));
        map.put("patient.nationalId", patientRoot.get("nationalId"));
        map.put("patient.gender", patientRoot.get("gender"));
        map.put("patient.externalId", patientRoot.get("externalId"));
        map.put("person.firstName", personJoin.get("firstName"));
        map.put("person.lastName", personJoin.get("lastName"));
        map.put("person.email", personJoin.get("email"));
        map.put("person.city", personJoin.get("city"));

        return map;
    }

    @SuppressWarnings("unchecked")
    private Predicate buildPredicate(CriteriaBuilder cb, Path<?> path, String operator,
            List<String> values, String dataType) {
        return switch (operator) {
            case "EQUAL" -> cb.equal(path, parseValue(values.get(0), dataType));
            case "LIKE" -> {
                String escaped = values.get(0).replace("%", "\\%").replace("_", "\\_");
                yield cb.like((Path<String>) path, "%" + escaped + "%");
            }
            case "BETWEEN" -> {
                if (values.size() < 2)
                    throw new ReportValidationException("BETWEEN needs 2 values");
                if ("DATE".equals(dataType)) {
                    Timestamp from = toTimestamp(values.get(0));
                    Timestamp to = Timestamp.valueOf(LocalDate.parse(values.get(1)).atTime(LocalTime.MAX));
                    yield cb.between((Path<Timestamp>) path, from, to);
                }
                yield cb.between((Path<String>) path, values.get(0), values.get(1));
            }
            case "IN" -> path.in(values.stream().map(v -> parseValue(v, dataType)).toList());
            default -> throw new ReportValidationException("Unknown operator: " + operator);
        };
    }

    private Object parseValue(String value, String dataType) {
        return switch (dataType) {
            case "DATE" -> toTimestamp(value);
            case "NUMBER" -> {
                try {
                    yield Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new ReportValidationException("Invalid number: " + value);
                }
            }
            default -> value;
        };
    }

    private Timestamp toTimestamp(String dateStr) {
        try {
            return Timestamp.valueOf(LocalDate.parse(dateStr).atStartOfDay());
        } catch (DateTimeParseException e) {
            throw new ReportValidationException("Invalid date (yyyy-MM-dd expected): " + dateStr);
        }
    }
}
