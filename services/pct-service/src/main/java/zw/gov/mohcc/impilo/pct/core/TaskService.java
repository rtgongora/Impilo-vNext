package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TaskEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TaskRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Manages clinical and operational tasks within patient journeys.
 *
 * <p>A task is a discrete unit of work assigned to a specific healthcare
 * worker or role. Tasks are linked to a journey and optionally to a
 * specific encounter. Common task types include:</p>
 * <ul>
 *   <li>{@code LAB_ORDER} — laboratory test request</li>
 *   <li>{@code IMAGING} — radiology/imaging request</li>
 *   <li>{@code PHARMACY} — medication dispensing</li>
 *   <li>{@code REFERRAL} — specialist referral</li>
 *   <li>{@code FOLLOW_UP} — post-discharge follow-up</li>
 *   <li>{@code DOCUMENTATION} — clinical documentation</li>
 * </ul>
 *
 * <p>Tasks can be queried by assignee (individual provider), workspace
 * (service point), or journey. Completed tasks are timestamped and retain
 * the identity of the completing actor.</p>
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    /** Status value for completed tasks, used in query exclusions. */
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final TaskRepository taskRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TaskService(TaskRepository taskRepository,
                       EventOutboxRepository outboxRepository,
                       ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new task within a patient journey.
     *
     * <p>The task is created with status {@code OPEN} and persisted. An
     * outbox event is published for downstream consumers (e.g. notification
     * service, task boards).</p>
     *
     * @param journeyId    the patient journey this task belongs to
     * @param encounterId  the encounter this task is linked to; may be {@code null}
     * @param taskType     the type of task (e.g. LAB_ORDER, IMAGING, PHARMACY)
     * @param assigneeId   the identifier of the assigned healthcare worker
     * @param assigneeRole the role of the assignee (e.g. NURSE, DOCTOR, PHARMACIST)
     * @param workspaceId  the workspace where the task should be performed; may be {@code null}
     * @param dueAt        the deadline for task completion; may be {@code null}
     * @param notes        free-text notes or instructions; may be {@code null}
     * @return the created task entity
     */
    @Transactional
    public TaskEntity createTask(String journeyId, Long encounterId, String taskType,
                                 String assigneeId, String assigneeRole, UUID workspaceId,
                                 OffsetDateTime dueAt, String notes) {
        TrustContext ctx = TrustContextHolder.require();

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setJourneyId(journeyId);
        task.setEncounterId(encounterId);
        task.setTenantId(ctx.tenantId());
        task.setTaskType(taskType);
        task.setAssigneeId(assigneeId);
        task.setAssigneeRole(assigneeRole);
        task.setWorkspaceId(workspaceId);
        task.setStatus("OPEN");
        task.setDueAt(dueAt);
        task.setNotes(notes);
        task.setCreatedBy(ctx.actorId());
        task.setCreatedAt(OffsetDateTime.now());

        task = taskRepository.save(task);

        // Outbox event for notification delivery
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId().toString());
        payload.put("journeyId", journeyId);
        payload.put("taskType", taskType);
        payload.put("assigneeId", assigneeId);
        payload.put("assigneeRole", assigneeRole);
        payload.put("workspaceId", workspaceId != null ? workspaceId.toString() : null);
        payload.put("dueAt", dueAt != null ? dueAt.toString() : null);
        writeOutbox("TASK", task.getId().toString(), "TASK_CREATED", toJson(payload));

        log.info("Task created: id={}, type={}, assignee={}, journey={}",
                task.getId(), taskType, assigneeId, journeyId);

        return task;
    }

    /**
     * Complete a task.
     *
     * <p>Sets the task status to {@code COMPLETED}, records the completing
     * actor and timestamp.</p>
     *
     * @param taskId the task to complete
     * @return the completed task entity
     * @throws IllegalArgumentException if the task is not found
     * @throws IllegalStateException    if the task is already completed
     */
    @Transactional
    public TaskEntity completeTask(UUID taskId) {
        TrustContext ctx = TrustContextHolder.require();

        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (STATUS_COMPLETED.equals(task.getStatus())) {
            throw new IllegalStateException("Task is already completed: " + taskId);
        }

        task.setStatus(STATUS_COMPLETED);
        task.setCompletedBy(ctx.actorId());
        task.setCompletedAt(OffsetDateTime.now());

        task = taskRepository.save(task);

        writeOutbox("TASK", taskId.toString(), "TASK_COMPLETED", toJson(Map.of(
                "taskId", taskId.toString(),
                "journeyId", task.getJourneyId(),
                "taskType", task.getTaskType(),
                "completedBy", ctx.actorId(),
                "completedAt", task.getCompletedAt().toString())));

        log.info("Task completed: id={}, type={}, completedBy={}",
                taskId, task.getTaskType(), ctx.actorId());

        return task;
    }

    /**
     * Retrieve all non-completed tasks assigned to a specific healthcare worker.
     *
     * @param assigneeId the identifier of the assignee
     * @return a list of open tasks for the assignee (may be empty)
     */
    @Transactional(readOnly = true)
    public List<TaskEntity> getTasksForAssignee(String assigneeId) {
        return taskRepository.findByAssigneeIdAndStatusNot(assigneeId, STATUS_COMPLETED);
    }

    /**
     * Retrieve all non-completed tasks for a specific workspace (service point).
     *
     * @param workspaceId the workspace identifier
     * @return a list of open tasks for the workspace (may be empty)
     */
    @Transactional(readOnly = true)
    public List<TaskEntity> getTasksForWorkspace(UUID workspaceId) {
        return taskRepository.findByWorkspaceIdAndStatusNot(workspaceId, STATUS_COMPLETED);
    }

    /**
     * Retrieve all tasks (including completed) for a given journey.
     *
     * @param journeyId the patient journey identifier
     * @return a list of all tasks for the journey (may be empty)
     */
    @Transactional(readOnly = true)
    public List<TaskEntity> getTasksForJourney(String journeyId) {
        return taskRepository.findByJourneyId(journeyId);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(TrustContextHolder.require().tenantId());
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
