package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DispatchServiceClient;

import java.util.Map;

/**
 * BFF proxy for the dispatch sovereign service.
 * Manages task dispatch, assignment, and completion.
 */
@RestController
@RequestMapping("/internal/v1/dispatch")
public class DispatchController {

    private static final Logger log = LoggerFactory.getLogger(DispatchController.class);

    private final DispatchServiceClient client;

    public DispatchController(DispatchServiceClient client) {
        this.client = client;
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> listTasks(
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.listTasks(assigneeId, status);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Dispatch task list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> getTask(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.getTask(id);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Dispatch task get failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.createTask(body);
            return ResponseEntity.status(201).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Dispatch task creation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "CREATE_FAILED", "message", e.getMessage())));
        }
    }

    @PatchMapping("/tasks/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignTask(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.assignTask(id, body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Dispatch task assign failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "ASSIGN_FAILED", "message", e.getMessage())));
        }
    }

    @PatchMapping("/tasks/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeTask(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = client.completeTask(id, body != null ? body : Map.of());
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Dispatch task complete failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "COMPLETE_FAILED", "message", e.getMessage())));
        }
    }
}
