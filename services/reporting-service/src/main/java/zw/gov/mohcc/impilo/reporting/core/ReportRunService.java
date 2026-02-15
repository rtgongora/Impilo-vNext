package zw.gov.mohcc.impilo.reporting.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.reporting.dto.ReportRunResponse;
import zw.gov.mohcc.impilo.reporting.dto.RunReportRequest;
import zw.gov.mohcc.impilo.reporting.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportRunEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportRunRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportRunService {

    private static final Logger log = LoggerFactory.getLogger(ReportRunService.class);

    private final ReportRunRepository runRepository;
    private final ReportDefinitionService definitionService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReportRunService(ReportRunRepository runRepository,
                            ReportDefinitionService definitionService,
                            EventOutboxRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.definitionService = definitionService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs a report synchronously (stub execution).
     * Produces stub output based on the query template and requested format.
     */
    @Transactional
    public ReportRunEntity runReport(UUID tenantId, String reportKey, String actorId,
                                     RunReportRequest request) {
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
            String result = executeStub(definition, runtimeParams, format);
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
     * Stub execution engine. Generates sample data based on format.
     * In production, this would query NDR or other data sources.
     */
    String executeStub(ReportDefinitionEntity definition, String parameters,
                       ExportFormat format) {
        String reportKey = definition.getReportKey();

        if (format == ExportFormat.CSV) {
            return "report_key,name,generated_at,row_count\n"
                    + reportKey + ","
                    + definition.getName() + ","
                    + OffsetDateTime.now() + ","
                    + "0\n";
        }

        Map<String, Object> stubData = new LinkedHashMap<>();
        stubData.put("reportKey", reportKey);
        stubData.put("name", definition.getName());
        stubData.put("generatedAt", OffsetDateTime.now().toString());
        stubData.put("parameters", parameters);
        stubData.put("rowCount", 0);
        stubData.put("rows", java.util.List.of());
        return toJson(stubData);
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
