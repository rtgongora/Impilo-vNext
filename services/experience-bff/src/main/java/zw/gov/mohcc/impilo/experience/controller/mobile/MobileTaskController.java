package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.*;

/**
 * Mobile task management endpoints.
 * GET   /internal/v1/mobile/provider/tasks/mine               - my tasks
 * GET   /internal/v1/mobile/provider/tasks?facility_id=       - facility tasks
 * GET   /internal/v1/mobile/provider/tasks/{id}               - get single
 * PATCH /internal/v1/mobile/provider/tasks/{id}               - update status
 * POST  /internal/v1/mobile/provider/tasks/{id}/escalate      - escalate
 * POST  /internal/v1/mobile/provider/tasks/{id}/complete      - complete
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/tasks")
public class MobileTaskController {

    private final PctServiceClient pctClient;

    public MobileTaskController(PctServiceClient pctClient) {
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

    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> myTasks(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "assigned_to") String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of("data", List.of()));
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
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTask(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTask(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @PostMapping("/{id}/escalate")
    public ResponseEntity<Map<String, Object>> escalateTask(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody EscalateTaskRequest request) {
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeTask(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CompleteTaskRequest request) {
        return ResponseEntity.ok(Map.of("data", List.of()));
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
