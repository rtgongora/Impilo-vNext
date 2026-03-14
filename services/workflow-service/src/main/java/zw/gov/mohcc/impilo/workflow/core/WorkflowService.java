package zw.gov.mohcc.impilo.workflow.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.workflow.api.dto.*;
import zw.gov.mohcc.impilo.workflow.domain.*;
import zw.gov.mohcc.impilo.workflow.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowTaskRepository taskRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public WorkflowService(WorkflowDefinitionRepository definitionRepository,
                            WorkflowInstanceRepository instanceRepository,
                            WorkflowTaskRepository taskRepository,
                            OutboxEventRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowDefinitionEntity createDefinition(UUID tenantId, String podId, String correlationId,
                                                       String idempotencyKey, CreateDefinitionRequest request) {
        // Check for existing version
        int nextVersion = definitionRepository.findByTenantIdAndCodeAndVersion(tenantId, request.code(), 1)
                .map(d -> d.getVersion() + 1).orElse(1);

        UUID defId = UUID.randomUUID();
        String stepsJson = toJson(request.steps());
        WorkflowDefinitionEntity def = new WorkflowDefinitionEntity(defId, tenantId, request.code(),
                request.name(), request.description(), request.category(), nextVersion, stepsJson);
        if (request.schema() != null) def.setSchemaJson(toJson(request.schema()));
        definitionRepository.save(def);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("definition_id", defId.toString());
        payload.put("code", request.code());
        payload.put("name", request.name());
        payload.put("version", nextVersion);
        payload.put("status", "DRAFT");
        appendOutboxEvent("impilo.workflow.definition.created.v1", defId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, defId.toString());

        log.info("Created workflow definition [id={}, code={}, version={}]", defId, request.code(), nextVersion);
        return def;
    }

    @Transactional
    public WorkflowDefinitionEntity publishDefinition(UUID definitionId, UUID tenantId, String podId,
                                                        String correlationId, String idempotencyKey) {
        WorkflowDefinitionEntity def = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new NotFoundException("Definition not found: " + definitionId));

        def.setStatus("PUBLISHED");
        def.setPublishedAt(OffsetDateTime.now());
        def.setUpdatedAt(OffsetDateTime.now());
        definitionRepository.save(def);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("definition_id", definitionId.toString());
        payload.put("code", def.getCode());
        payload.put("version", def.getVersion());
        payload.put("status", "PUBLISHED");
        appendOutboxEvent("impilo.workflow.definition.published.v1", definitionId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, definitionId.toString());

        log.info("Published workflow definition [id={}, code={}]", definitionId, def.getCode());
        return def;
    }

    @Transactional
    public WorkflowInstanceEntity startInstance(UUID tenantId, String podId, String correlationId,
                                                  String idempotencyKey, StartInstanceRequest request) {
        WorkflowDefinitionEntity def = definitionRepository.findById(request.definitionId())
                .orElseThrow(() -> new NotFoundException("Definition not found: " + request.definitionId()));

        if (!"PUBLISHED".equals(def.getStatus())) {
            throw new InvalidTransitionException("Definition must be PUBLISHED to start instances, current: " + def.getStatus());
        }

        UUID instanceId = UUID.randomUUID();
        String contextJson = request.context() != null ? toJson(request.context()) : "{}";
        String initiator = request.initiatorRef() != null ? request.initiatorRef() : "system";
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity(instanceId, request.definitionId(),
                tenantId, initiator, request.subjectRef(), contextJson);
        instance.setStatus("RUNNING");
        instance.setStartedAt(OffsetDateTime.now());

        // Create first task from definition steps
        try {
            List<?> steps = objectMapper.readValue(def.getStepsJson(), List.class);
            if (!steps.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> firstStep = (Map<String, Object>) steps.get(0);
                String stepName = (String) firstStep.getOrDefault("name", "step-1");
                instance.setCurrentStep(stepName);

                UUID taskId = UUID.randomUUID();
                WorkflowTaskEntity task = new WorkflowTaskEntity(taskId, instanceId, tenantId,
                        stepName, null, contextJson);
                task.setStatus("IN_PROGRESS");
                task.setStartedAt(OffsetDateTime.now());
                taskRepository.save(task);
            }
        } catch (Exception e) {
            log.warn("Could not parse steps for definition {}", def.getDefinitionId(), e);
        }

        instanceRepository.save(instance);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instance_id", instanceId.toString());
        payload.put("definition_id", request.definitionId().toString());
        payload.put("status", "RUNNING");
        payload.put("initiator_ref", initiator);
        appendOutboxEvent("impilo.workflow.instance.started.v1", instanceId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, instanceId.toString());

        log.info("Started workflow instance [id={}, definition={}]", instanceId, request.definitionId());
        return instance;
    }

    @Transactional
    public WorkflowInstanceEntity transitionInstance(UUID instanceId, UUID tenantId, String podId,
                                                       String correlationId, String idempotencyKey,
                                                       TransitionRequest request) {
        WorkflowInstanceEntity instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new NotFoundException("Instance not found: " + instanceId));

        String action = request.action().toUpperCase();
        String previousStatus = instance.getStatus();

        switch (action) {
            case "COMPLETE" -> {
                if (!"RUNNING".equals(instance.getStatus()))
                    throw new InvalidTransitionException("Cannot COMPLETE instance in status: " + instance.getStatus());
                instance.setStatus("COMPLETED");
                instance.setCompletedAt(OffsetDateTime.now());
                // Complete current task
                completeCurrentTask(instance, request.output());
            }
            case "FAIL" -> {
                if (!"RUNNING".equals(instance.getStatus()))
                    throw new InvalidTransitionException("Cannot FAIL instance in status: " + instance.getStatus());
                instance.setStatus("FAILED");
                instance.setCompletedAt(OffsetDateTime.now());
                completeCurrentTask(instance, request.output());
            }
            case "CANCEL" -> {
                if ("COMPLETED".equals(instance.getStatus()) || "CANCELLED".equals(instance.getStatus()))
                    throw new InvalidTransitionException("Cannot CANCEL instance in status: " + instance.getStatus());
                instance.setStatus("CANCELLED");
                instance.setCompletedAt(OffsetDateTime.now());
            }
            case "ADVANCE" -> {
                if (!"RUNNING".equals(instance.getStatus()))
                    throw new InvalidTransitionException("Cannot ADVANCE instance in status: " + instance.getStatus());
                completeCurrentTask(instance, request.output());
                // Advance to next step based on definition
                advanceToNextStep(instance);
            }
            default -> throw new InvalidTransitionException("Unknown action: " + action);
        }

        instance.setUpdatedAt(OffsetDateTime.now());
        instanceRepository.save(instance);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instance_id", instanceId.toString());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", instance.getStatus());
        payload.put("action", action);
        payload.put("current_step", instance.getCurrentStep());
        appendOutboxEvent("impilo.workflow.instance.transitioned.v1", instanceId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, instanceId.toString());

        log.info("Transitioned workflow instance [id={}, {} -> {}]", instanceId, previousStatus, instance.getStatus());
        return instance;
    }

    private void completeCurrentTask(WorkflowInstanceEntity instance, Map<String, Object> output) {
        List<WorkflowTaskEntity> tasks = taskRepository.findByInstanceIdOrderByCreatedAtAsc(instance.getInstanceId());
        tasks.stream()
                .filter(t -> "IN_PROGRESS".equals(t.getStatus()))
                .findFirst()
                .ifPresent(task -> {
                    task.setStatus("COMPLETED");
                    task.setCompletedAt(OffsetDateTime.now());
                    task.setUpdatedAt(OffsetDateTime.now());
                    if (output != null) task.setOutputJson(toJson(output));
                    taskRepository.save(task);
                });
    }

    private void advanceToNextStep(WorkflowInstanceEntity instance) {
        WorkflowDefinitionEntity def = definitionRepository.findById(instance.getDefinitionId()).orElse(null);
        if (def == null) return;

        try {
            List<?> steps = objectMapper.readValue(def.getStepsJson(), List.class);
            String currentStep = instance.getCurrentStep();
            boolean foundCurrent = false;
            for (Object step : steps) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stepMap = (Map<String, Object>) step;
                String stepName = (String) stepMap.getOrDefault("name", "");
                if (foundCurrent) {
                    // Create next task
                    instance.setCurrentStep(stepName);
                    UUID taskId = UUID.randomUUID();
                    WorkflowTaskEntity task = new WorkflowTaskEntity(taskId, instance.getInstanceId(),
                            instance.getTenantId(), stepName, null, instance.getContextJson());
                    task.setStatus("IN_PROGRESS");
                    task.setStartedAt(OffsetDateTime.now());
                    taskRepository.save(task);
                    return;
                }
                if (stepName.equals(currentStep)) foundCurrent = true;
            }
            // No more steps — auto-complete
            instance.setStatus("COMPLETED");
            instance.setCompletedAt(OffsetDateTime.now());
        } catch (Exception e) {
            log.warn("Could not advance steps for instance {}", instance.getInstanceId(), e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowDefinitionEntity> getDefinition(UUID definitionId) {
        return definitionRepository.findById(definitionId);
    }

    @Transactional(readOnly = true)
    public Page<WorkflowDefinitionEntity> listDefinitions(UUID tenantId, String status, String category, int page, int size) {
        return definitionRepository.findFiltered(tenantId, status, category, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowInstanceEntity> getInstance(UUID instanceId) {
        return instanceRepository.findById(instanceId);
    }

    @Transactional(readOnly = true)
    public Page<WorkflowInstanceEntity> listInstances(UUID tenantId, String status, int page, int size) {
        return instanceRepository.findFiltered(tenantId, status, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<WorkflowTaskEntity> getInstanceTasks(UUID instanceId) {
        return taskRepository.findByInstanceIdOrderByCreatedAtAsc(instanceId);
    }

    @Transactional(readOnly = true)
    public Page<WorkflowDefinitionEntity> getDefinitionSnapshot(UUID tenantId, OffsetDateTime asOf, int page, int size) {
        return definitionRepository.findSnapshotAsOf(tenantId, asOf, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<WorkflowInstanceEntity> getInstanceSnapshot(UUID tenantId, OffsetDateTime asOf, int page, int size) {
        return instanceRepository.findSnapshotAsOf(tenantId, asOf, PageRequest.of(page, size));
    }

    private void appendOutboxEvent(String eventType, String aggregateId, UUID tenantId,
                                    String podId, String correlationId, String idempotencyKey,
                                    Map<String, Object> payload, String partitionKey) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("Workflow");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType("Workflow");
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON serialization failed", e); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }

    public static class InvalidTransitionException extends RuntimeException {
        public InvalidTransitionException(String msg) { super(msg); }
    }
}
