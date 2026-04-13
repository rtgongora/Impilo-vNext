package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen telehealth session endpoints.
 * GET  /internal/v1/mobile/citizen/telehealth/sessions
 * GET  /internal/v1/mobile/citizen/telehealth/sessions/{id}
 * POST /internal/v1/mobile/citizen/telehealth/sessions
 * POST /internal/v1/mobile/citizen/telehealth/sessions/{id}/join
 * POST /internal/v1/mobile/citizen/telehealth/sessions/{id}/end
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/telehealth")
public class CitizenTelehealthController {

    private final PctServiceClient pctClient;

    public CitizenTelehealthController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    public record RequestTeleconsultBody(
            @NotBlank String reason,
            String preferredDate,
            String sessionType,
            String providerId,
            String referralId
    ) {}

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);

        JsonNode sessions = pctClient.getPatientTelehealthSessions(actorId, status, page, limit);

        // UUID patientId = resolvePatientId(tenantId, actorId);
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", sessions);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode session = pctClient.getTelehealthSession(id.toString());

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", session);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> requestTeleconsult(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody RequestTeleconsultBody body) {

        String sessionType = body.sessionType() != null ? body.sessionType() : "VIDEO";

        Map<String, Object> sessionRequest = new LinkedHashMap<>();
        sessionRequest.put("patientCpid", actorId);
        sessionRequest.put("reason", body.reason());
        sessionRequest.put("sessionType", sessionType);
        sessionRequest.put("preferredDate", body.preferredDate());
        if (body.providerId() != null) sessionRequest.put("providerId", body.providerId());
        if (body.referralId() != null) sessionRequest.put("referralId", body.referralId());

        JsonNode result = pctClient.requestTelehealthSession(sessionRequest);

        // jdbcTemplate.update("""
        //     INSERT INTO citizen_telehealth_sessions (...) VALUES (...)
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sessions/{id}/join")
    public ResponseEntity<Map<String, Object>> joinSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode result = pctClient.joinTelehealthSession(id.toString());

        // jdbcTemplate.update("""
        //     UPDATE citizen_telehealth_sessions
        //     SET status = 'IN_PROGRESS', started_at = COALESCE(started_at, ?), updated_at = ?
        //     WHERE id = ? AND tenant_id = ? AND status IN ('SCHEDULED', 'REQUESTED', 'IN_PROGRESS')
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{id}/end")
    public ResponseEntity<Map<String, Object>> endSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        JsonNode result = pctClient.endTelehealthSession(id.toString(), body != null ? body : Map.of());

        // jdbcTemplate.update("""
        //     UPDATE citizen_telehealth_sessions
        //     SET status = 'COMPLETED', ended_at = ?, notes = COALESCE(?, notes), updated_at = ?
        //     WHERE id = ? AND tenant_id = ? AND status = 'IN_PROGRESS'
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "COMPLETED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
