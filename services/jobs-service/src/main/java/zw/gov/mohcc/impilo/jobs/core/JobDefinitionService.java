package zw.gov.mohcc.impilo.jobs.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.jobs.api.dto.CreateJobDefinitionRequest;
import zw.gov.mohcc.impilo.jobs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.jobs.persistence.entity.JobDefinitionEntity;
import zw.gov.mohcc.impilo.jobs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.jobs.persistence.repository.JobDefinitionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for job definition CRUD operations.
 * Each mutation appends an event to the outbox for reliable Kafka publishing.
 */
@Service
public class JobDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(JobDefinitionService.class);

    private final JobDefinitionRepository jobDefinitionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public JobDefinitionService(JobDefinitionRepository jobDefinitionRepository,
                                EventOutboxRepository outboxRepository,
                                ObjectMapper objectMapper) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all job definitions for a given tenant.
     */
    public List<JobDefinitionEntity> listByTenant(UUID tenantId) {
        return jobDefinitionRepository.findByTenantId(tenantId);
    }

    /**
     * Retrieves a single job definition by its ID.
     */
    public JobDefinitionEntity getById(Long id) {
        return jobDefinitionRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job definition not found: " + id));
    }

    /**
     * Creates a new job definition and writes a JOB_DEFINITION_CREATED outbox event.
     */
    @Transactional
    public JobDefinitionEntity create(CreateJobDefinitionRequest request) {
        JobDefinitionEntity entity = new JobDefinitionEntity();
        entity.setTenantId(request.getTenantId());
        entity.setName(request.getName());
        entity.setCronExpression(request.getCronExpression());
        entity.setJobType(request.getJobType());
        entity.setConfig(request.getConfig());
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        entity = jobDefinitionRepository.save(entity);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entity.getId());
        payload.put("tenantId", entity.getTenantId().toString());
        payload.put("name", entity.getName());
        payload.put("jobType", entity.getJobType());
        payload.put("cronExpression", entity.getCronExpression());
        payload.put("enabled", entity.getEnabled());

        appendOutboxEvent(entity.getTenantId(), "JOB_DEFINITION_CREATED", payload);

        log.info("Job definition created: id={}, name={}, type={}",
                entity.getId(), entity.getName(), entity.getJobType());

        return entity;
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
