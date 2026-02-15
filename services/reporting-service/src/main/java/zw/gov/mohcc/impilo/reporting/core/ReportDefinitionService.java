package zw.gov.mohcc.impilo.reporting.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.reporting.dto.CreateReportRequest;
import zw.gov.mohcc.impilo.reporting.dto.ReportDefinitionResponse;
import zw.gov.mohcc.impilo.reporting.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportDefinitionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(ReportDefinitionService.class);

    private final ReportDefinitionRepository definitionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReportDefinitionService(ReportDefinitionRepository definitionRepository,
                                    EventOutboxRepository outboxRepository,
                                    ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReportDefinitionEntity createReport(UUID tenantId, String actorId,
                                                CreateReportRequest request) {
        definitionRepository.findByTenantIdAndReportKey(tenantId, request.reportKey())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Report key already exists: " + request.reportKey());
                });

        ReportDefinitionEntity entity = new ReportDefinitionEntity();
        entity.setTenantId(tenantId);
        entity.setReportKey(request.reportKey());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setQueryTemplate(request.queryTemplate());
        entity.setParameters(request.parameters() != null ? request.parameters() : "{}");
        entity.setOutputFormat(parseExportFormat(request.outputFormat()));
        entity.setStatus(ReportStatus.ACTIVE);
        entity.setCreatedBy(actorId);
        entity = definitionRepository.save(entity);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("definitionId", entity.getDefinitionId().toString());
        payload.put("reportKey", entity.getReportKey());
        payload.put("name", entity.getName());
        payload.put("tenantId", tenantId.toString());
        writeOutbox("REPORT", entity.getDefinitionId().toString(),
                "REPORT_CREATED", tenantId, toJson(payload));

        log.info("Report definition created: key={}, id={}",
                entity.getReportKey(), entity.getDefinitionId());
        return entity;
    }

    @Transactional(readOnly = true)
    public ReportDefinitionEntity findByKey(UUID tenantId, String reportKey) {
        return definitionRepository.findByTenantIdAndReportKey(tenantId, reportKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Report not found: " + reportKey));
    }

    @Transactional(readOnly = true)
    public List<ReportDefinitionEntity> listReports(UUID tenantId) {
        return definitionRepository.findByTenantId(tenantId);
    }

    public ReportDefinitionResponse toResponse(ReportDefinitionEntity entity) {
        return new ReportDefinitionResponse(
                entity.getDefinitionId(),
                entity.getReportKey(),
                entity.getName(),
                entity.getDescription(),
                entity.getQueryTemplate(),
                entity.getParameters(),
                entity.getOutputFormat().name(),
                entity.getStatus().name(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static ExportFormat parseExportFormat(String format) {
        if (format == null || format.isBlank()) {
            return ExportFormat.JSON;
        }
        try {
            return ExportFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExportFormat.JSON;
        }
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
