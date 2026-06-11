package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.RtcIceCredentialService;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineSessionService;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mobile telemedicine session endpoints.
 * GET  /internal/v1/mobile/provider/telemedicine/sessions             - list sessions
 * POST /internal/v1/mobile/provider/telemedicine/sessions             - create session
 * POST /internal/v1/mobile/provider/telemedicine/sessions/{id}/join   - join session
 * POST /internal/v1/mobile/provider/telemedicine/sessions/{id}/end    - end session
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/telemedicine")
public class MobileTelemedicineController {

    private final PctServiceClient pctClient;
    private final RtcIceCredentialService iceCredentialService;
    private final TelemedicineSessionService sessionService;
    private static final Map<String, List<Map<String, Object>>> RTC_SIGNALS = new ConcurrentHashMap<>();
    private static final AtomicLong RTC_SEQUENCE = new AtomicLong(0);

    public MobileTelemedicineController(
            PctServiceClient pctClient,
            RtcIceCredentialService iceCredentialService,
            TelemedicineSessionService sessionService) {
        this.pctClient = pctClient;
        this.iceCredentialService = iceCredentialService;
        this.sessionService = sessionService;
    }

    /**
     * ICE server list (STUN + time-limited TURN credentials for coturn).
     * GET /internal/v1/mobile/provider/telemedicine/rtc/ice-servers
     */
    @GetMapping("/rtc/ice-servers")
    public ResponseEntity<Map<String, Object>> iceServers(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        Map<String, Object> ice = iceCredentialService.buildIceServers(actorId);
        return ResponseEntity.ok(Map.of(
                "data", ice,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
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
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (patientId != null && !patientId.isBlank()) {
            try {
                JsonNode data = pctClient.getPatientTelehealthSessions(patientId, status, page, size);
                if (data != null && data.isArray() && !data.isEmpty()) {
                    return ResponseEntity.ok(Map.of("data", data));
                }
            } catch (Exception ignored) {}
        }
        List<Map<String, Object>> sessions = sessionService.listSessions(
                tenantId, providerId, patientId, facilityId, referralId, status);
        return ResponseEntity.ok(Map.of(
                "data", sessions,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    /**
     * Create a new telemedicine session, optionally linked to a referral.
     * POST /internal/v1/mobile/provider/telemedicine/sessions
     */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        if (body.get("provider_id") == null && actorId != null) {
            body.put("provider_id", actorId);
        }
        if (body.get("provider_user_id") == null && actorId != null) {
            body.put("provider_user_id", actorId);
        }

        // Best-effort sovereign handoff
        try {
            Map<String, Object> pctBody = new LinkedHashMap<>();
            String sessionType = body.getOrDefault("session_type", "VIDEO").toString();
            pctBody.put("sessionType", sessionType);
            if (body.get("patient_id") != null) pctBody.put("patientId", body.get("patient_id"));
            if (body.get("provider_id") != null) pctBody.put("providerId", body.get("provider_id"));
            if (body.get("facility_id") != null) pctBody.put("facilityId", body.get("facility_id"));
            if (body.get("encounter_id") != null) pctBody.put("encounterId", body.get("encounter_id"));
            if (body.get("referral_id") != null) pctBody.put("referralId", body.get("referral_id"));
            if (body.get("scheduled_at") != null) pctBody.put("scheduledAt", body.get("scheduled_at"));
            if (body.get("notes") != null) pctBody.put("notes", body.get("notes"));
            pctClient.requestTelehealthSession(pctBody);
        } catch (Exception ignored) {}

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
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = pctClient.joinTelehealthSession(id.toString());
            if (data != null) {
                sessionService.markJoined(id, tenantId);
                return ResponseEntity.ok(Map.of("data", data));
            }
        } catch (Exception ignored) {}
        sessionService.markJoined(id, tenantId);
        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "IN_PROGRESS"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/sessions/{id}/end")
    public ResponseEntity<Map<String, Object>> endSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = pctClient.endTelehealthSession(id.toString(), body);
            if (data != null) {
                String notes = body != null && body.get("notes") != null ? body.get("notes").toString() : null;
                sessionService.markEnded(id, tenantId, notes);
                return ResponseEntity.ok(Map.of("data", data));
            }
        } catch (Exception ignored) {}
        String notes = body != null && body.get("notes") != null ? body.get("notes").toString() : null;
        sessionService.markEnded(id, tenantId, notes);
        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "COMPLETED"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/sessions/{id}/rtc/signal")
    public ResponseEntity<Map<String, Object>> pushRtcSignal(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        String key = id.toString();
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("sequence", RTC_SEQUENCE.incrementAndGet());
        signal.put("session_id", key);
        signal.put("type", body.get("type"));
        signal.put("from", body.get("from"));
        signal.put("to", body.get("to"));
        signal.put("payload", body.get("payload"));
        signal.put("created_at", OffsetDateTime.now().toString());
        RTC_SIGNALS.computeIfAbsent(key, __ -> new ArrayList<>()).add(signal);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", signal,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @GetMapping("/sessions/{id}/rtc/signals")
    public ResponseEntity<Map<String, Object>> pollRtcSignals(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "after", defaultValue = "0") long after) {
        List<Map<String, Object>> signals = RTC_SIGNALS.getOrDefault(id.toString(), List.of())
                .stream()
                .filter(s -> Long.parseLong(String.valueOf(s.get("sequence"))) > after)
                .toList();
        return ResponseEntity.ok(Map.of(
                "data", signals,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
