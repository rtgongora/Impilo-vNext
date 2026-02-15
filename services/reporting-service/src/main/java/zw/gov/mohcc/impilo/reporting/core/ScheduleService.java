package zw.gov.mohcc.impilo.reporting.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.reporting.dto.CreateScheduleRequest;
import zw.gov.mohcc.impilo.reporting.dto.ScheduleResponse;
import zw.gov.mohcc.impilo.reporting.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportScheduleEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportScheduleRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ReportScheduleRepository scheduleRepository;
    private final ReportDefinitionService definitionService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ScheduleService(ReportScheduleRepository scheduleRepository,
                           ReportDefinitionService definitionService,
                           EventOutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.scheduleRepository = scheduleRepository;
        this.definitionService = definitionService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a schedule entry for a report. This is a stub — no real
     * scheduler is wired; the entry is persisted for future implementation.
     */
    @Transactional
    public ReportScheduleEntity createSchedule(UUID tenantId, String reportKey,
                                                String actorId,
                                                CreateScheduleRequest request) {
        ReportDefinitionEntity definition = definitionService.findByKey(tenantId, reportKey);

        ReportScheduleEntity schedule = new ReportScheduleEntity();
        schedule.setTenantId(tenantId);
        schedule.setDefinition(definition);
        schedule.setCronExpression(request.cronExpression());
        schedule.setParameters(request.parameters() != null ? request.parameters() : "{}");
        schedule.setOutputFormat(ReportDefinitionService.parseExportFormat(request.outputFormat()));
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setCreatedBy(actorId);
        schedule = scheduleRepository.save(schedule);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scheduleId", schedule.getScheduleId().toString());
        payload.put("reportKey", reportKey);
        payload.put("cronExpression", schedule.getCronExpression());
        payload.put("tenantId", tenantId.toString());
        writeOutbox("SCHEDULE", schedule.getScheduleId().toString(),
                "SCHEDULE_CREATED", tenantId, toJson(payload));

        log.info("Report schedule created: key={}, scheduleId={}, cron={}",
                reportKey, schedule.getScheduleId(), schedule.getCronExpression());
        return schedule;
    }

    @Transactional(readOnly = true)
    public List<ReportScheduleEntity> listSchedules(UUID tenantId, String reportKey) {
        ReportDefinitionEntity definition = definitionService.findByKey(tenantId, reportKey);
        return scheduleRepository.findByTenantIdAndDefinitionId(tenantId, definition.getId());
    }

    public ScheduleResponse toResponse(ReportScheduleEntity entity) {
        return new ScheduleResponse(
                entity.getScheduleId(),
                entity.getDefinition().getReportKey(),
                entity.getCronExpression(),
                entity.getParameters(),
                entity.getOutputFormat().name(),
                entity.getStatus().name(),
                entity.getNextRunAt(),
                entity.getLastRunAt(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
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
