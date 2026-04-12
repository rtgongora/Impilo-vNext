package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile task management endpoints.
 * GET   /internal/v1/mobile/provider/tasks/mine               - my tasks
 * GET   /internal/v1/mobile/provider/tasks?facility_id=       - facility tasks
 * GET   /internal/v1/mobile/provider/tasks/{id}               - get single
 * PATCH /internal/v1/mobile/provider/tasks/{id}               - update status
 * POST  /internal/v1/mobile/provider/tasks/{id}/escalate      - escalate
 * POST  /internal/v1/mobile/provider/tasks/{id}/complete      - complete
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to PctServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/tasks")
public class MobileTaskController {

    // STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to PctServiceClient
    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;
    private final PctServiceClient pctClient;

    public MobileTaskController(JdbcTemplate jdbcTemplate, OutboxService outboxService,
                                PctServiceClient pctClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.pctClient = pctClient;
    }

    public record UpdateTaskRequest(
            String status,
            String notes
    ) {}

    public record EscalateTaskRequest(
            @NotBlank String escalation_reason,
            String escalated_to
    ) {}

    public record CompleteTaskRequest(
            String outcome,
            String notes
    ) {}

    private static final String TASK_SELECT = """
        SELECT id, facility_id, assigned_to, task_type, title, description,
               patient_id, encounter_id, priority, status, due_at, notes,
               escalation_reason, escalated_to, escalated_at, outcome,
               completed_at, created_at, updated_at
        FROM tasks
        """;

    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> myTasks(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "assigned_to") String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                TASK_SELECT + """
                    WHERE tenant_id = ? AND assigned_to = ?
                    AND status NOT IN ('COMPLETED', 'CANCELLED')
                    ORDER BY
                        CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END,
                        due_at ASC NULLS LAST
                    LIMIT ? OFFSET ?
                    """, tenantId, assignedTo, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM tasks
            WHERE tenant_id = ? AND assigned_to = ? AND status NOT IN ('COMPLETED', 'CANCELLED')
            """, Long.class, tenantId, assignedTo);

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> facilityTasks(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows;
        Long total;

        if (status != null) {
            rows = jdbcTemplate.queryForList(
                    TASK_SELECT + """
                        WHERE tenant_id = ? AND facility_id = ?::uuid AND status = ?
                        ORDER BY created_at DESC
                        LIMIT ? OFFSET ?
                        """, tenantId, facilityId, status, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM tasks
                WHERE tenant_id = ? AND facility_id = ?::uuid AND status = ?
                """, Long.class, tenantId, facilityId, status);
        } else {
            rows = jdbcTemplate.queryForList(
                    TASK_SELECT + """
                        WHERE tenant_id = ? AND facility_id = ?::uuid
                        ORDER BY created_at DESC
                        LIMIT ? OFFSET ?
                        """, tenantId, facilityId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM tasks WHERE tenant_id = ? AND facility_id = ?::uuid
                """, Long.class, tenantId, facilityId);
        }

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTask(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                TASK_SELECT + "WHERE id = ? AND tenant_id = ?", id, tenantId);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Task not found: " + id);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateTask(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody UpdateTaskRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

        int updated = jdbcTemplate.update("""
            UPDATE tasks
            SET status = COALESCE(?, status),
                notes = COALESCE(?, notes),
                updated_at = ?
            WHERE id = ? AND tenant_id = ?
            """, request.status(), request.notes(), now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Task not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.task.updated.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Task",
                id.toString(),
                Map.of(
                        "task_id", id.toString(),
                        "status", request.status() != null ? request.status() : "unchanged"
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                TASK_SELECT + "WHERE id = ? AND tenant_id = ?", id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", rows.isEmpty() ? Map.of() : toResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/escalate")
    @Transactional
    public ResponseEntity<Map<String, Object>> escalateTask(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody EscalateTaskRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

        int updated = jdbcTemplate.update("""
            UPDATE tasks
            SET status = 'ESCALATED', escalation_reason = ?, escalated_to = ?,
                escalated_at = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status NOT IN ('COMPLETED', 'CANCELLED')
            """, request.escalation_reason(), request.escalated_to(), now, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Escalatable task not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.task.escalated.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Task",
                id.toString(),
                Map.of(
                        "task_id", id.toString(),
                        "status", "ESCALATED",
                        "escalation_reason", request.escalation_reason()
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                TASK_SELECT + "WHERE id = ? AND tenant_id = ?", id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", rows.isEmpty() ? Map.of() : toResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/complete")
    @Transactional
    public ResponseEntity<Map<String, Object>> completeTask(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CompleteTaskRequest request) {

        OffsetDateTime now = OffsetDateTime.now();
        String outcome = request != null && request.outcome() != null ? request.outcome() : "DONE";
        String notes = request != null ? request.notes() : null;

        int updated = jdbcTemplate.update("""
            UPDATE tasks
            SET status = 'COMPLETED', outcome = ?, notes = COALESCE(?, notes),
                completed_at = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status NOT IN ('COMPLETED', 'CANCELLED')
            """, outcome, notes, now, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Completable task not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.task.completed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Task",
                id.toString(),
                Map.of(
                        "task_id", id.toString(),
                        "status", "COMPLETED",
                        "outcome", outcome
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                TASK_SELECT + "WHERE id = ? AND tenant_id = ?", id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", rows.isEmpty() ? Map.of() : toResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", row.get("facility_id"));
        attributes.put("assigned_to", row.get("assigned_to"));
        attributes.put("task_type", row.get("task_type"));
        attributes.put("title", row.get("title"));
        attributes.put("description", row.get("description"));
        attributes.put("patient_id", row.get("patient_id"));
        attributes.put("encounter_id", row.get("encounter_id"));
        attributes.put("priority", row.get("priority"));
        attributes.put("status", row.get("status"));
        attributes.put("due_at", row.get("due_at"));
        attributes.put("notes", row.get("notes"));
        attributes.put("escalation_reason", row.get("escalation_reason"));
        attributes.put("escalated_to", row.get("escalated_to"));
        attributes.put("escalated_at", row.get("escalated_at"));
        attributes.put("outcome", row.get("outcome"));
        attributes.put("completed_at", row.get("completed_at"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "Task");
        resource.put("attributes", attributes);
        return resource;
    }
}
