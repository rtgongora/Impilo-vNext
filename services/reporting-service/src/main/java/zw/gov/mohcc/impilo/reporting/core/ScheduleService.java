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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ReportScheduleRepository scheduleRepository;
    private final ReportDefinitionService definitionService;
    private final ReportRunService reportRunService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ScheduleService(ReportScheduleRepository scheduleRepository,
                           ReportDefinitionService definitionService,
                           ReportRunService reportRunService,
                           EventOutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.scheduleRepository = scheduleRepository;
        this.definitionService = definitionService;
        this.reportRunService = reportRunService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a schedule entry for a report. The schedule is persisted and
     * the next run time is computed from the cron expression. The scheduler
     * polls active schedules every 60 seconds via {@link #pollSchedules()}.
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
        schedule.setNextRunAt(computeNextRun(request.cronExpression()));
        schedule.setCreatedBy(actorId);
        if (schedule.getScheduleId() == null) {
            schedule.setScheduleId(UUID.randomUUID());
        }
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

    /**
     * Polls active schedules every 60 seconds and triggers report runs
     * for any schedule whose next_run_at is in the past.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void pollSchedules() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ReportScheduleEntity> due = scheduleRepository.findDueSchedules(now);

        if (due.isEmpty()) {
            return;
        }

        log.info("Scheduler poll found {} due schedules", due.size());

        for (ReportScheduleEntity schedule : due) {
            try {
                String reportKey = schedule.getDefinition().getReportKey();
                UUID tenantId = schedule.getTenantId();

                reportRunService.runReport(tenantId, reportKey, "SCHEDULER",
                        new zw.gov.mohcc.impilo.reporting.dto.RunReportRequest(
                                schedule.getParameters(),
                                schedule.getOutputFormat().name()));

                schedule.setLastRunAt(now);
                schedule.setNextRunAt(computeNextRun(schedule.getCronExpression()));
                scheduleRepository.save(schedule);

                log.info("Scheduled report executed: key={}, scheduleId={}, nextRun={}",
                        reportKey, schedule.getScheduleId(), schedule.getNextRunAt());
            } catch (Exception e) {
                log.error("Failed to execute scheduled report: scheduleId={}, error={}",
                        schedule.getScheduleId(), e.getMessage());
            }
        }
    }

    private OffsetDateTime computeNextRun(String cronExpression) {
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            LocalDateTime next = cron.next(LocalDateTime.now(ZoneOffset.UTC));
            return next != null ? next.atOffset(ZoneOffset.UTC) : null;
        } catch (Exception e) {
            log.warn("Invalid cron expression '{}': {}", cronExpression, e.getMessage());
            return null;
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
