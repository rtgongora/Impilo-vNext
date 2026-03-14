package zw.gov.mohcc.impilo.jobs.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.jobs.api.dto.TriggerJobRequest;
import zw.gov.mohcc.impilo.jobs.domain.JobStatus;
import zw.gov.mohcc.impilo.jobs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.jobs.persistence.entity.JobDefinitionEntity;
import zw.gov.mohcc.impilo.jobs.persistence.entity.JobExecutionEntity;
import zw.gov.mohcc.impilo.jobs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.jobs.persistence.repository.JobExecutionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for triggering and managing job executions.
 * Each trigger appends an event to the outbox for reliable Kafka publishing.
 */
@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobExecutionRepository executionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public JobExecutionService(JobExecutionRepository executionRepository,
                               EventOutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.executionRepository = executionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Triggers a new execution for the given job definition.
     */
    @Transactional
    public JobExecutionEntity trigger(JobDefinitionEntity jobDefinition, TriggerJobRequest request) {
        JobExecutionEntity execution = new JobExecutionEntity();
        execution.setTenantId(jobDefinition.getTenantId());
        execution.setJobDefinitionId(jobDefinition.getId());
        execution.setStatus(JobStatus.PENDING.name());
        execution.setStartedAt(OffsetDateTime.now());
        execution.setCreatedAt(OffsetDateTime.now());

        if (request != null && request.getIdempotencyKey() != null) {
            execution.setIdempotencyKey(request.getIdempotencyKey());
        } else {
            execution.setIdempotencyKey(UUID.randomUUID().toString());
        }

        execution = executionRepository.save(execution);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionId", execution.getId());
        payload.put("jobDefinitionId", jobDefinition.getId());
        payload.put("tenantId", jobDefinition.getTenantId().toString());
        payload.put("jobType", jobDefinition.getJobType());
        payload.put("status", execution.getStatus());
        payload.put("idempotencyKey", execution.getIdempotencyKey());

        appendOutboxEvent(jobDefinition.getTenantId(), "JOB_TRIGGERED", payload);

        log.info("Job triggered: executionId={}, definitionId={}, type={}",
                execution.getId(), jobDefinition.getId(), jobDefinition.getJobType());

        return execution;
    }

    private void appendOutboxEvent(UUID tenantId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setTenantId(tenantId);
        outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        outbox.setRequestId(UUID.randomUUID().toString());
        outbox.setCorrelationId(UUID.randomUUID().toString());
        outbox.setEventType(eventType);
        outbox.setPublished(false);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
        outboxRepository.save(outbox);
    }
}
