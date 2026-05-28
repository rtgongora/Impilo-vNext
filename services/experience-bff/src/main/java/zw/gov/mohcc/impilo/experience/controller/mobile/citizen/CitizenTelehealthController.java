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
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineGovernanceService;

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
    private final TelemedicineGovernanceService telemedicineGovernanceService;

    public CitizenTelehealthController(PctServiceClient pctClient,
                                       TelemedicineGovernanceService telemedicineGovernanceService) {
        this.pctClient = pctClient;
        this.telemedicineGovernanceService = telemedicineGovernanceService;
    }

    public record RequestTeleconsultBody(
            @NotBlank String reason,
            String preferredDate,
            String sessionType,
            String providerId,
            String referralId,
            String sessionProvider,
            String consentReference
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

        assertGovernedRead();
        int limit = Math.min(size, 100);

        JsonNode sessions = pctClient.getPatientTelehealthSessions(actorId, status, page, limit);
        if (sessions == null) {
            throw new ResourceNotFoundException("Telehealth sessions not available");
        }

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
        assertGovernedRead();
        JsonNode session = pctClient.getTelehealthSession(id.toString());
        if (session == null) {
            throw new ResourceNotFoundException("Telehealth session not found");
        }

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
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody RequestTeleconsultBody body) {

        assertGovernedMutate();
        String sessionType = body.sessionType() != null ? body.sessionType() : "VIDEO";
        String normalizedPurpose = normalizePurposeOfUse(purposeOfUse);
        String consentReference = body.consentReference();
        assertMediaConsentReference(sessionType, consentReference, normalizedPurpose);

        Map<String, Object> sessionRequest = new LinkedHashMap<>();
        sessionRequest.put("patientCpid", actorId);
        sessionRequest.put("reason", body.reason());
        sessionRequest.put("sessionType", sessionType);
        sessionRequest.put("preferredDate", body.preferredDate());
        if (body.providerId() != null) sessionRequest.put("providerId", body.providerId());
        if (body.referralId() != null) sessionRequest.put("referralId", body.referralId());
        if (body.sessionProvider() != null) {
            sessionRequest.put("sessionProvider", body.sessionProvider());
        } else if ("VIDEO".equalsIgnoreCase(sessionType) || "AUDIO".equalsIgnoreCase(sessionType)) {
            sessionRequest.put("sessionProvider", "EXTERNAL_MANAGED");
        }
        if (consentReference != null && !consentReference.isBlank()) {
            sessionRequest.put("consentReference", consentReference);
        }
        sessionRequest.put("purposeOfUse", normalizedPurpose);

        JsonNode result = pctClient.requestTelehealthSession(sessionRequest);
        if (result == null) {
            throw new ResourceNotFoundException("Telehealth session could not be created");
        }
        audit(tenantId, correlationId, "TELEMEDICINE_CITIZEN_SESSION_REQUESTED",
                "POST:mobile/citizen/telehealth/sessions", "SUCCESS", actorId, actorId,
                result.get("id") == null ? null : result.get("id").asText(), normalizedPurpose);

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
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse) {

        assertGovernedMutate();
        JsonNode result = pctClient.joinTelehealthSession(id.toString());
        if (result == null) {
            throw new ResourceNotFoundException("Telehealth session join failed");
        }
        audit(tenantId, correlationId, "TELEMEDICINE_CITIZEN_JOINED",
                "POST:mobile/citizen/telehealth/sessions/join", "SUCCESS", null,
                first(result, "patientCpid", "patient_id"), id.toString(), normalizePurposeOfUse(purposeOfUse));

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
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody(required = false) Map<String, Object> body) {

        assertGovernedMutate();
        JsonNode result = pctClient.endTelehealthSession(id.toString(), body != null ? body : Map.of());
        if (result == null) {
            throw new ResourceNotFoundException("Telehealth session end failed");
        }
        audit(tenantId, correlationId, "TELEMEDICINE_CITIZEN_ENDED",
                "POST:mobile/citizen/telehealth/sessions/end", "SUCCESS", null,
                first(result, "patientCpid", "patient_id"), id.toString(), normalizePurposeOfUse(purposeOfUse));

        // jdbcTemplate.update("""
        //     UPDATE citizen_telehealth_sessions
        //     SET status = 'COMPLETED', ended_at = ?, notes = COALESCE(?, notes), updated_at = ?
        //     WHERE id = ? AND tenant_id = ? AND status = 'IN_PROGRESS'
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private void assertGovernedRead() {
        if (telemedicineGovernanceService != null) {
            telemedicineGovernanceService.assertGovernedRead();
        }
    }

    private void assertGovernedMutate() {
        if (telemedicineGovernanceService != null) {
            telemedicineGovernanceService.assertGovernedMutate();
        }
    }

    private void audit(String tenantId,
                       String correlationId,
                       String eventType,
                       String action,
                       String outcome,
                       String actorId,
                       String patientId,
                       String sessionId,
                       String purposeOfUse) {
        if (telemedicineGovernanceService == null) {
            return;
        }
        telemedicineGovernanceService.audit(
                tenantId, correlationId, purposeOfUse, null,
                eventType, action, outcome,
                actorId == null ? "citizen-app" : actorId, "PATIENT",
                patientId, "TelemedicineSession", sessionId, Map.of());
    }

    private String normalizePurposeOfUse(String purposeOfUse) {
        if (telemedicineGovernanceService == null) {
            return purposeOfUse == null || purposeOfUse.isBlank() ? "TREATMENT" : purposeOfUse.trim().toUpperCase(Locale.ROOT);
        }
        return telemedicineGovernanceService.normalizePurposeOfUse(purposeOfUse);
    }

    private void assertMediaConsentReference(String sessionType, String consentReference, String purposeOfUse) {
        if (telemedicineGovernanceService != null) {
            telemedicineGovernanceService.assertMediaConsentReference(sessionType, consentReference, purposeOfUse);
        }
    }

    private String first(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node != null && node.hasNonNull(key)) {
                String value = node.get(key).asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
