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
     * Triggering is <b>not implemented</b> and therefore refuses.
     *
     * <p>There is no executor: no scheduler, no worker, no Kafka producer, and no subscriber
     * anywhere for {@code JOB_TRIGGERED}. The previous implementation saved a PENDING execution
     * row, appended the outbox event and returned {@code 201 Created} — a row that could never
     * leave PENDING because nothing existed to advance it.</p>
     *
     * <p>No execution row is created, so {@code job_execution} does not accumulate runs that
     * never ran. See {@link JobExecutionNotImplementedException}.</p>
     */
    public JobExecutionEntity trigger(JobDefinitionEntity jobDefinition, TriggerJobRequest request) {
        log.warn("Job trigger REFUSED (no executor): definitionId={}, type={}",
                jobDefinition.getId(), jobDefinition.getJobType());
        throw new JobExecutionNotImplementedException(jobDefinition.getId());
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
