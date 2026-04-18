package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mobile task endpoints — proxies PCT {@code /v1/tasks} where available.
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
            String escalatedTo
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
        // PCT resolves assignee from trust context; assigned_to query is ignored until PCT supports impersonation filters.
        JsonNode data = pctClient.getMyTasks();
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
        // Treat facility_id as workspace UUID for PCT task workspace listing (closest sovereign match).
        JsonNode data = pctClient.listWorkspaceTasks(UUID.fromString(facilityId.trim()));
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTask(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = pctClient.getTask(id);
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
        JsonNode data = pctClient.updateTask(id, Map.of(
                "status", request.status(),
                "notes", request.notes() != null ? request.notes() : ""));
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
        JsonNode data = pctClient.escalateTask(id, Map.of(
                "escalation_reason", request.escalation_reason(),
                "escalated_to", request.escalatedTo() != null ? request.escalatedTo() : ""));
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
        JsonNode data = pctClient.completeTask(id);
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
