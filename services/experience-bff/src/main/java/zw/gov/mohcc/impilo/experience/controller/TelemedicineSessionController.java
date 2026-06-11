package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineSessionChatStore;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineSessionService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Web + shared telemedicine session API (persists to experience_bff.telemedicine_sessions).
 */
@RestController
@RequestMapping("/internal/v1/telemedicine")
public class TelemedicineSessionController {

    private final TelemedicineSessionService sessionService;
    private final TelemedicineSessionChatStore chatStore;

    public TelemedicineSessionController(
            TelemedicineSessionService sessionService,
            TelemedicineSessionChatStore chatStore) {
        this.sessionService = sessionService;
        this.chatStore = chatStore;
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "provider_id") String providerId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "referral_id") String referralId,
            @RequestParam(required = false) String status) {

        List<Map<String, Object>> sessions = sessionService.listSessions(
                tenantId, providerId, patientId, facilityId, referralId, status);

        return ResponseEntity.ok(Map.of(
                "data", sessions,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        if (body.get("patient_id") == null || body.get("patient_id").toString().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "INVALID_REQUEST", "message", "patient_id is required")
            ));
        }
        if (body.get("provider_id") == null && actorId != null) {
            body.put("provider_id", actorId);
        }
        if (body.get("provider_user_id") == null && actorId != null) {
            body.put("provider_user_id", actorId);
        }

        Map<String, Object> session = sessionService.createSession(tenantId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", session,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/sessions/{id}/join")
    public ResponseEntity<Map<String, Object>> joinSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        sessionService.markJoined(id, tenantId);
        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "IN_PROGRESS"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of(
                "data", chatStore.listMessages(id),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> msg = chatStore.addMessage(
                id,
                actorId,
                body.get("senderName") != null ? body.get("senderName").toString() : null,
                body.get("content") != null ? body.get("content").toString() : "",
                body.get("type") != null ? body.get("type").toString() : "TEXT");
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", msg,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/sessions/{id}/end")
    public ResponseEntity<Map<String, Object>> endSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        String notes = body != null && body.get("notes") != null ? body.get("notes").toString() : null;
        sessionService.markEnded(id, tenantId, notes);
        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "COMPLETED"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
