package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.WellnessServiceClient;

import java.util.Map;

/**
 * BFF proxy for the wellness sovereign service.
 * Consumer-grade wellness: programs, enrollments, challenges, goals.
 */
@RestController
@RequestMapping("/internal/v1/wellness")
public class WellnessController {

    private static final Logger log = LoggerFactory.getLogger(WellnessController.class);

    private final WellnessServiceClient client;

    public WellnessController(WellnessServiceClient client) {
        this.client = client;
    }

    // ── Programs ─────────────────────────────────────────────────

    @GetMapping("/programs")
    public ResponseEntity<Map<String, Object>> listPrograms(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.listPrograms();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Wellness programs list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/programs/{id}")
    public ResponseEntity<Map<String, Object>> getProgram(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.getProgram(id);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Wellness program get failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    // ── Enrollments ──────────────────────────────────────────────

    @PostMapping("/enrollments")
    public ResponseEntity<Map<String, Object>> enroll(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.enrollInProgram(body);
            return ResponseEntity.status(201).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Wellness enrollment failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "ENROLL_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/enrollments")
    public ResponseEntity<Map<String, Object>> listEnrollments(
            @RequestParam(required = false) String participantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.listEnrollments(participantId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Wellness enrollments list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    // ── Challenges ───────────────────────────────────────────────

    @GetMapping("/challenges")
    public ResponseEntity<Map<String, Object>> listChallenges(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.listChallenges();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Wellness challenges list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/challenges/{id}/join")
    public ResponseEntity<Map<String, Object>> joinChallenge(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.joinChallenge(id, body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Wellness challenge join failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "JOIN_FAILED", "message", e.getMessage())));
        }
    }

    // ── Goals ────────────────────────────────────────────────────

    @GetMapping("/goals")
    public ResponseEntity<Map<String, Object>> listGoals(
            @RequestParam(required = false) String participantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.listGoals(participantId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Wellness goals list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/goals")
    public ResponseEntity<Map<String, Object>> createGoal(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.createGoal(body);
            return ResponseEntity.status(201).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Wellness goal creation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "CREATE_FAILED", "message", e.getMessage())));
        }
    }

    @PatchMapping("/goals/{id}")
    public ResponseEntity<Map<String, Object>> updateGoal(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.updateGoal(id, body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Wellness goal update failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "UPDATE_FAILED", "message", e.getMessage())));
        }
    }
}
