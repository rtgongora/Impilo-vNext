package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineGovernanceService;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

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
    private final ObjectMapper objectMapper;
    private final TelemedicineGovernanceService telemedicineGovernanceService;

    @Autowired
    public MobileTelemedicineController(PctServiceClient pctClient,
                                        TelemedicineGovernanceService telemedicineGovernanceService) {
        this(pctClient, new ObjectMapper(), telemedicineGovernanceService);
    }

    public MobileTelemedicineController(PctServiceClient pctClient) {
        this(pctClient, new ObjectMapper(), null);
    }

    public MobileTelemedicineController(PctServiceClient pctClient,
                                        ObjectMapper objectMapper,
                                        TelemedicineGovernanceService telemedicineGovernanceService) {
        this.pctClient = pctClient;
        this.objectMapper = objectMapper;
        this.telemedicineGovernanceService = telemedicineGovernanceService;
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
        assertGovernedRead();
        if (patientId != null && !patientId.isBlank()) {
            try {
                JsonNode data = pctClient.getPatientTelehealthSessions(patientId, status, page, size);
                if (data != null) {
                    JsonNode filtered = filterSessions(data, providerId, referralId);
                    return ResponseEntity.ok(Map.of(
                            "data", filtered,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
                JsonNode demo = demoSessionsForPatient(patientId);
                if (demo != null) {
                    return ResponseEntity.ok(Map.of(
                            "data", demo,
                            "meta", Map.of(
                                    "request_id", requestId,
                                    "correlation_id", correlationId,
                                    "source", "demo-fallback")));
                }
                return upstreamFailure("PCT_UNAVAILABLE", "No telemedicine list payload returned", requestId, correlationId);
            } catch (Exception e) {
                JsonNode demo = demoSessionsForPatient(patientId);
                if (demo != null) {
                    return ResponseEntity.ok(Map.of(
                            "data", demo,
                            "meta", Map.of(
                                    "request_id", requestId,
                                    "correlation_id", correlationId,
                                    "source", "demo-fallback")));
                }
                return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
            }
        }
        if (facilityId != null && !facilityId.isBlank()) {
            try {
                JsonNode data = pctClient.listTelehealthSessions(facilityId, status, page, size);
                if (data != null) {
                    JsonNode filtered = filterSessions(data, providerId, referralId);
                    return ResponseEntity.ok(Map.of(
                            "data", filtered,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
                return upstreamFailure("PCT_UNAVAILABLE", "No telemedicine list payload returned", requestId, correlationId);
            } catch (Exception e) {
                return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", Map.of("code", "MISSING_FILTER", "message", "patient_id or facility_id query parameter is required"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        assertGovernedMutate();
        if (body == null || body.isEmpty()) {
            return badRequest("INVALID_PAYLOAD", "Request body is required", requestId, correlationId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String sessionType = body.getOrDefault("session_type", "VIDEO").toString();
        String patientId = body.get("patient_id") != null ? body.get("patient_id").toString() : null;
        String providerId = body.get("provider_id") != null ? body.get("provider_id").toString() : null;
        String facilityId = body.get("facility_id") != null ? body.get("facility_id").toString() : null;
        String encounterId = body.get("encounter_id") != null ? body.get("encounter_id").toString() : null;
        String referralId = body.get("referral_id") != null ? body.get("referral_id").toString() : null;
        String scheduledAt = body.get("scheduled_at") != null ? body.get("scheduled_at").toString() : null;
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        String consentReference = body.get("consentReference") != null
                ? body.get("consentReference").toString()
                : (body.get("consent_reference") != null ? body.get("consent_reference").toString() : null);
        String sessionProvider = body.get("session_provider") != null
                ? body.get("session_provider").toString()
                : (body.get("provider_type") != null ? body.get("provider_type").toString() : null);
        if ((sessionProvider == null || sessionProvider.isBlank())
                && ("VIDEO".equalsIgnoreCase(sessionType) || "AUDIO".equalsIgnoreCase(sessionType))) {
            sessionProvider = "EXTERNAL_MANAGED";
        }

        if (patientId == null || patientId.isBlank()) {
            return badRequest("MISSING_PATIENT_ID", "patient_id is required", requestId, correlationId);
        }
        if (providerId == null || providerId.isBlank()) {
            return badRequest("MISSING_PROVIDER_ID", "provider_id is required", requestId, correlationId);
        }

        OffsetDateTime scheduled;
        try {
            scheduled = scheduledAt != null ? OffsetDateTime.parse(scheduledAt) : now.plusHours(1);
        } catch (DateTimeParseException e) {
            return badRequest("INVALID_SCHEDULED_AT", "scheduled_at must be an ISO-8601 datetime", requestId, correlationId);
        }

        try {
            String normalizedPurpose = normalizePurposeOfUse(
                    body.get("purposeOfUse") != null ? body.get("purposeOfUse").toString()
                            : (body.get("purpose_of_use") != null ? body.get("purpose_of_use").toString() : purposeOfUse)
            );
            assertMediaConsentReference(sessionType, consentReference, normalizedPurpose);
            Map<String, Object> pctBody = new LinkedHashMap<>();
            pctBody.put("sessionType", sessionType);
            if (patientId != null) pctBody.put("patientId", patientId);
            if (providerId != null) pctBody.put("providerId", providerId);
            if (facilityId != null) pctBody.put("facilityId", facilityId);
            if (encounterId != null) pctBody.put("encounterId", encounterId);
            if (referralId != null) pctBody.put("referralId", referralId);
            pctBody.put("scheduledAt", scheduled.toString());
            if (notes != null) pctBody.put("notes", notes);
            if (consentReference != null && !consentReference.isBlank()) {
                pctBody.put("consentReference", consentReference);
            }
            if (sessionProvider != null && !sessionProvider.isBlank()) {
                pctBody.put("sessionProvider", sessionProvider);
            }
            pctBody.put("purposeOfUse", normalizedPurpose);
            JsonNode result = pctClient.requestTelehealthSession(pctBody);
            if (result != null) {
                audit(tenantId, correlationId, "TELEMEDICINE_MOBILE_PROVIDER_SESSION_CREATED",
                        "POST:mobile/provider/telemedicine/sessions", "SUCCESS",
                        providerId, patientId, result.get("id") == null ? null : result.get("id").asText(), normalizedPurpose);
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "data", result,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PCT_UNAVAILABLE", "No telemedicine create payload returned", requestId, correlationId);
        } catch (ResponseStatusException e) {
            return governanceFailure(e, requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/join")
    public ResponseEntity<Map<String, Object>> joinSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            assertGovernedMutate();
            JsonNode data = pctClient.joinTelehealthSession(id.toString());
            if (data != null) {
                audit(tenantId, correlationId, "TELEMEDICINE_MOBILE_PROVIDER_JOINED",
                        "POST:mobile/provider/telemedicine/sessions/join", "SUCCESS",
                        first(data, "providerId", "provider_id"), first(data, "patientCpid", "patient_id"), id.toString(),
                        normalizePurposeOfUse(purposeOfUse));
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PCT_UNAVAILABLE", "No telemedicine join payload returned", requestId, correlationId);
        } catch (ResponseStatusException e) {
            return governanceFailure(e, requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/end")
    public ResponseEntity<Map<String, Object>> endSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            assertGovernedMutate();
            JsonNode data = pctClient.endTelehealthSession(id.toString(), body);
            if (data != null) {
                audit(tenantId, correlationId, "TELEMEDICINE_MOBILE_PROVIDER_ENDED",
                        "POST:mobile/provider/telemedicine/sessions/end", "SUCCESS",
                        first(data, "providerId", "provider_id"), first(data, "patientCpid", "patient_id"), id.toString(),
                        normalizePurposeOfUse(purposeOfUse));
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PCT_UNAVAILABLE", "No telemedicine end payload returned", requestId, correlationId);
        } catch (ResponseStatusException e) {
            return governanceFailure(e, requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Telemedicine upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> governanceFailure(
            ResponseStatusException e, String requestId, String correlationId) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.BAD_REQUEST : status;
        String code = resolved == HttpStatus.FORBIDDEN ? "TELEMEDICINE_GOVERNANCE_DENIED" : "TELEMEDICINE_GOVERNANCE_INVALID";
        return ResponseEntity.status(resolved).body(Map.of(
                "error", Map.of("code", code, "message", e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason()),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private JsonNode filterSessions(JsonNode payload, String providerId, String referralId) {
        boolean filterProvider = providerId != null && !providerId.isBlank();
        boolean filterReferral = referralId != null && !referralId.isBlank();
        if ((!filterProvider && !filterReferral) || payload == null || payload.isNull()) {
            return payload;
        }
        if (payload.isArray()) {
            return filterSessionArray((ArrayNode) payload, providerId, referralId);
        }
        if (payload.isObject()) {
            ObjectNode copy = payload.deepCopy();
            JsonNode items = copy.path("items");
            if (items.isArray()) {
                copy.set("items", filterSessionArray((ArrayNode) items, providerId, referralId));
                return copy;
            }
            JsonNode data = copy.path("data");
            if (data.isArray()) {
                copy.set("data", filterSessionArray((ArrayNode) data, providerId, referralId));
                return copy;
            }
        }
        return payload;
    }

    private ArrayNode filterSessionArray(ArrayNode sessions, String providerId, String referralId) {
        ArrayNode filtered = objectMapper.createArrayNode();
        sessions.forEach(node -> {
            String provider = first(node, "providerId", "provider_id");
            String referral = first(node, "referralId", "referral_id");
            boolean providerMatch = providerId == null || providerId.isBlank() || providerId.equals(provider);
            boolean referralMatch = referralId == null || referralId.isBlank() || referralId.equals(referral);
            if (providerMatch && referralMatch) {
                filtered.add(node);
            }
        });
        return filtered;
    }

    private String first(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.hasNonNull(key)) {
                String v = node.get(key).asText();
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * Wave 22 — demo fallback aligned with BFF Flyway V40 golden patient when PCT is unavailable.
     */
    private JsonNode demoSessionsForPatient(String patientRef) {
        if (patientRef == null || patientRef.isBlank()) {
            return null;
        }
        String normalized = patientRef.trim();
        if (!"CPID-ZW-00001".equalsIgnoreCase(normalized)
                && !"a1000000-0000-0000-0000-000000000001".equalsIgnoreCase(normalized)) {
            return null;
        }
        ArrayNode sessions = objectMapper.createArrayNode();
        ObjectNode session = objectMapper.createObjectNode();
        session.put("id", "f3000000-0000-4000-8000-000000000001");
        session.put("patient_id", "CPID-ZW-00001");
        session.put("session_type", "VIDEO");
        session.put("status", "SCHEDULED");
        session.put("notes", "DEMO-WAVE20 teleconsult for CPID-ZW-00001 (Rx → dispatch handoff demo)");
        sessions.add(session);
        return sessions;
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
                actorId, "PROVIDER", patientId, "TelemedicineSession", sessionId, Map.of());
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
}
