package zw.gov.mohcc.impilo.dispatch.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.dispatch.api.dto.AssignJobRequest;
import zw.gov.mohcc.impilo.dispatch.api.dto.CreateJobRequest;
import zw.gov.mohcc.impilo.dispatch.api.dto.UpdateStatusRequest;
import zw.gov.mohcc.impilo.dispatch.domain.DispatchEventEntity;
import zw.gov.mohcc.impilo.dispatch.domain.DispatchJobEntity;
import zw.gov.mohcc.impilo.dispatch.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.dispatch.repository.DispatchEventRepository;
import zw.gov.mohcc.impilo.dispatch.repository.DispatchJobRepository;
import zw.gov.mohcc.impilo.dispatch.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DispatchJobService {

    private static final Logger log = LoggerFactory.getLogger(DispatchJobService.class);
    private static final String AGGREGATE_TYPE = "DispatchJob";
    private static final String PRODUCER = "dispatch-service";
    private static final String SUBJECT_TYPE = "DispatchJob";

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "NEW", Set.of("ASSIGNED", "CANCELLED"),
            "ASSIGNED", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("COMPLETED", "CANCELLED")
    );

    private final DispatchJobRepository jobRepository;
    private final DispatchEventRepository eventRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DispatchJobService(DispatchJobRepository jobRepository,
                               DispatchEventRepository eventRepository,
                               OutboxEventRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.eventRepository = eventRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DispatchJobEntity createJob(UUID tenantId, String podId, String correlationId,
                                        String idempotencyKey, CreateJobRequest request) {
        UUID jobId = UUID.randomUUID();
        DispatchJobEntity job = new DispatchJobEntity(jobId, tenantId, request.facilityRef());
        job.setRequestRef(request.requestRef());
        jobRepository.save(job);

        eventRepository.save(new DispatchEventEntity(jobId, "NEW", null));

        Map<String, Object> payload = buildJobState(job);
        appendOutboxEvent("impilo.dispatch.job.created.v1", jobId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, jobId.toString());

        log.info("Created dispatch job [jobId={}, facilityRef={}]", jobId, request.facilityRef());
        return job;
    }

    @Transactional
    public DispatchJobEntity assignJob(UUID jobId, UUID tenantId, String podId,
                                        String correlationId, String idempotencyKey,
                                        AssignJobRequest request) {
        DispatchJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        validateTransition(job.getStatus(), "ASSIGNED");

        job.setStatus("ASSIGNED");
        job.setAssignedAgentRef(request.agentRef());
        job.setAssignedVehicleRef(request.vehicleRef());
        job.setUpdatedAt(OffsetDateTime.now());
        jobRepository.save(job);

        eventRepository.save(new DispatchEventEntity(jobId, "ASSIGNED", null));

        Map<String, Object> payload = buildJobState(job);
        payload.put("assigned_agent_ref", request.agentRef());
        payload.put("assigned_vehicle_ref", request.vehicleRef());
        appendOutboxEvent("impilo.dispatch.job.assigned.v1", jobId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, jobId.toString());

        log.info("Assigned dispatch job [jobId={}, agent={}, vehicle={}]",
                jobId, request.agentRef(), request.vehicleRef());
        return job;
    }

    @Transactional
    public DispatchJobEntity updateStatus(UUID jobId, UUID tenantId, String podId,
                                           String correlationId, String idempotencyKey,
                                           UpdateStatusRequest request) {
        DispatchJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        validateTransition(job.getStatus(), request.status());

        String previousStatus = job.getStatus();
        job.setStatus(request.status());
        job.setUpdatedAt(OffsetDateTime.now());
        jobRepository.save(job);

        String notesJson = toJson(request.notes());
        eventRepository.save(new DispatchEventEntity(jobId, request.status(), notesJson));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previous_status", previousStatus);
        payload.put("new_status", request.status());
        payload.put("job", buildJobState(job));
        appendOutboxEvent("impilo.dispatch.job.status.updated.v1", jobId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, jobId.toString());

        log.info("Updated dispatch job status [jobId={}, {} -> {}]",
                jobId, previousStatus, request.status());
        return job;
    }

    @Transactional(readOnly = true)
    public Optional<DispatchJobEntity> getJob(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public Page<DispatchJobEntity> listJobs(UUID tenantId, String facilityRef, String status,
                                             int page, int size) {
        return jobRepository.findFiltered(tenantId, facilityRef, status, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<DispatchJobEntity> getSnapshot(UUID tenantId, OffsetDateTime asOf, int page, int size) {
        return jobRepository.findSnapshotAsOf(tenantId, asOf, PageRequest.of(page, size));
    }

    private void validateTransition(String currentStatus, String requestedStatus) {
        Set<String> allowed = VALID_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(requestedStatus)) {
            throw new InvalidStatusTransitionException(currentStatus, requestedStatus);
        }
    }

    private void appendOutboxEvent(String eventType, String aggregateId,
                                    UUID tenantId, String podId, String correlationId,
                                    String idempotencyKey, Map<String, Object> payload,
                                    String partitionKey) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(AGGREGATE_TYPE);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType(SUBJECT_TYPE);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private Map<String, Object> buildJobState(DispatchJobEntity job) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("job_id", job.getJobId().toString());
        state.put("tenant_id", job.getTenantId().toString());
        state.put("facility_ref", job.getFacilityRef());
        state.put("request_ref", job.getRequestRef());
        state.put("status", job.getStatus());
        state.put("assigned_agent_ref", job.getAssignedAgentRef());
        state.put("assigned_vehicle_ref", job.getAssignedVehicleRef());
        return state;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    public static class JobNotFoundException extends RuntimeException {
        public JobNotFoundException(UUID jobId) {
            super("Dispatch job not found: " + jobId);
        }
    }
}
