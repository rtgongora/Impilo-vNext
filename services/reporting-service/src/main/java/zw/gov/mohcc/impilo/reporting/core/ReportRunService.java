package zw.gov.mohcc.impilo.reporting.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.reporting.dto.ReportRunResponse;
import zw.gov.mohcc.impilo.reporting.dto.RunReportRequest;
import zw.gov.mohcc.impilo.reporting.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportRunEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportRunRepository;
import zw.gov.mohcc.impilo.shared.visibility.ExportVisibilityGuard;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReportRunService {

    private static final Logger log = LoggerFactory.getLogger(ReportRunService.class);
    private static final int MAX_ROWS = 10_000;
    private static final Pattern UNSAFE_SQL_PATTERN = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|EXEC)\\b");

    private final ReportRunRepository runRepository;
    private final ReportDefinitionService definitionService;
    private final EventOutboxRepository outboxRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReportRunService(ReportRunRepository runRepository,
                            ReportDefinitionService definitionService,
                            EventOutboxRepository outboxRepository,
                            NamedParameterJdbcTemplate jdbcTemplate,
                            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.definitionService = definitionService;
        this.outboxRepository = outboxRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs a report synchronously by executing the stored query template
     * against the database with the supplied parameters.
     */
    @Transactional
    public ReportRunEntity runReport(UUID tenantId, String reportKey, String actorId,
                                     RunReportRequest request) {
        return runReport(tenantId, reportKey, actorId, request, null);
    }

    /**
     * Same as {@link #runReport(UUID, String, String, RunReportRequest)} with optional
     * {@link VisibilityProfile} for export-time redaction (JSON shaping / CSV column masking).
     */
    @Transactional
    public ReportRunEntity runReport(UUID tenantId, String reportKey, String actorId,
                                     RunReportRequest request, VisibilityProfile visibility) {
        ReportDefinitionEntity definition = definitionService.findByKey(tenantId, reportKey);

        if (definition.getStatus() != ReportStatus.ACTIVE) {
            throw new IllegalStateException("Report is not active: " + reportKey);
        }

        ExportFormat format = request != null && request.outputFormat() != null
                ? ReportDefinitionService.parseExportFormat(request.outputFormat())
                : definition.getOutputFormat();

        String runtimeParams = request != null && request.parameters() != null
                ? request.parameters()
                : definition.getParameters();

        ReportRunEntity run = new ReportRunEntity();
        run.setTenantId(tenantId);
        run.setDefinition(definition);
        run.setParameters(runtimeParams);
        run.setOutputFormat(format);
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(OffsetDateTime.now());
        run.setCreatedBy(actorId);
        run = runRepository.save(run);

        try {
            String result = executeQuery(definition, runtimeParams, tenantId, format);
            if (ExportVisibilityGuard.requiresRedactedReportOutput(visibility)) {
                result = ExportReportOutputRedactor.apply(result, format, visibility, objectMapper);
            }
            run.setResult(result);
            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(OffsetDateTime.now());
        } catch (Exception e) {
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(OffsetDateTime.now());
            log.error("Report run failed: key={}, error={}", reportKey, e.getMessage());
        }

        run = runRepository.save(run);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.getRunId().toString());
        payload.put("reportKey", reportKey);
        payload.put("status", run.getStatus().name());
        payload.put("tenantId", tenantId.toString());
        writeOutbox("REPORT_RUN", run.getRunId().toString(),
                "REPORT_RAN", tenantId, toJson(payload));

        log.info("Report run completed: key={}, runId={}, status={}",
                reportKey, run.getRunId(), run.getStatus());
        return run;
    }

    @Transactional(readOnly = true)
    public Page<ReportRunEntity> listRuns(UUID tenantId, String reportKey, Pageable pageable) {
        ReportDefinitionEntity definition = definitionService.findByKey(tenantId, reportKey);
        return runRepository.findByTenantIdAndDefinitionId(tenantId, definition.getId(), pageable);
    }

    public ReportRunResponse toResponse(ReportRunEntity entity) {
        return new ReportRunResponse(
                entity.getRunId(),
                entity.getDefinition().getReportKey(),
                entity.getParameters(),
                entity.getOutputFormat().name(),
                entity.getStatus().name(),
                entity.getResult(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }

    /**
     * Executes the query template from the report definition against the database.
     * <p>
     * The query template is treated as a read-only SQL SELECT statement.
     * DML/DDL statements are rejected for safety. Parameters are bound using
     * Spring's NamedParameterJdbcTemplate to prevent SQL injection.
     * <p>
     * The tenant_id is always injected as a mandatory parameter to enforce
     * data isolation. Results are limited to {@value #MAX_ROWS} rows.
     */
    String executeQuery(ReportDefinitionEntity definition, String parametersJson,
                        UUID tenantId, ExportFormat format) {
        String queryTemplate = definition.getQueryTemplate();

        // Safety: reject non-SELECT queries
        validateQuerySafety(queryTemplate);

        // Parse runtime parameters
        Map<String, Object> params = parseParameters(parametersJson);
        params.put("tenant_id", tenantId);

        // Wrap query with row limit if not already limited
        String boundedQuery = applyRowLimit(queryTemplate);

        // Execute query
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(boundedQuery,
                new MapSqlParameterSource(params));

        log.info("Report query executed: key={}, rows={}", definition.getReportKey(), rows.size());

        // Format output
        return formatResult(definition, rows, params, format);
    }

    private void validateQuerySafety(String query) {
        String normalised = query.replaceAll("--[^\n]*", " ")
                .replaceAll("/\\*.*?\\*/", " ");
        Matcher m = UNSAFE_SQL_PATTERN.matcher(normalised);
        if (m.find()) {
            throw new IllegalArgumentException(
                    "Report query template contains disallowed SQL keyword: " + m.group());
        }
    }

    private String applyRowLimit(String query) {
        String upper = query.toUpperCase().trim();
        if (!upper.contains("LIMIT")) {
            return query.trim().replaceAll(";$", "") + " LIMIT " + MAX_ROWS;
        }
        return query;
    }

    private Map<String, Object> parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()
                || "{}".equals(parametersJson.trim())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(parametersJson,
                    new TypeReference<HashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse report parameters: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String formatResult(ReportDefinitionEntity definition,
                                 List<Map<String, Object>> rows,
                                 Map<String, Object> params,
                                 ExportFormat format) {
        if (format == ExportFormat.CSV) {
            return toCsv(rows);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportKey", definition.getReportKey());
        result.put("name", definition.getName());
        result.put("generatedAt", OffsetDateTime.now().toString());
        result.put("parameters", params);
        result.put("rowCount", rows.size());
        result.put("rows", rows);
        return toJson(result);
    }

    private String toCsv(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Set<String> headers = rows.get(0).keySet();
        sb.append(String.join(",", headers)).append("\n");
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                Object val = row.get(header);
                String str = val != null ? val.toString() : "";
                if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                    str = "\"" + str.replace("\"", "\"\"") + "\"";
                }
                values.add(str);
            }
            sb.append(String.join(",", values)).append("\n");
        }
        return sb.toString();
    }

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, UUID tenantId, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(tenantId);
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise outbox payload: {}", e.getMessage());
            return "{}";
        }
    }
}
