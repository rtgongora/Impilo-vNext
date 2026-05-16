package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

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

    public MobileTelemedicineController(PctServiceClient pctClient) {
        this(pctClient, new ObjectMapper());
    }

    public MobileTelemedicineController(PctServiceClient pctClient, ObjectMapper objectMapper) {
        this.pctClient = pctClient;
        this.objectMapper = objectMapper;
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
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
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
        String sessionProvider = body.get("session_provider") != null
                ? body.get("session_provider").toString()
                : (body.get("provider_type") != null ? body.get("provider_type").toString() : null);

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
            Map<String, Object> pctBody = new LinkedHashMap<>();
            pctBody.put("sessionType", sessionType);
            if (patientId != null) pctBody.put("patientId", patientId);
            if (providerId != null) pctBody.put("providerId", providerId);
            if (facilityId != null) pctBody.put("facilityId", facilityId);
            if (encounterId != null) pctBody.put("encounterId", encounterId);
            if (referralId != null) pctBody.put("referralId", referralId);
            pctBody.put("scheduledAt", scheduled.toString());
            if (notes != null) pctBody.put("notes", notes);
            if (sessionProvider != null && !sessionProvider.isBlank()) {
                pctBody.put("sessionProvider", sessionProvider);
            }
            JsonNode result = pctClient.requestTelehealthSession(pctBody);
            if (result != null) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "data", result,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PCT_UNAVAILABLE", "No telemedicine create payload returned", requestId, correlationId);
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
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = pctClient.joinTelehealthSession(id.toString());
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PCT_UNAVAILABLE", "No telemedicine join payload returned", requestId, correlationId);
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
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = pctClient.endTelehealthSession(id.toString(), body);
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PCT_UNAVAILABLE", "No telemedicine end payload returned", requestId, correlationId);
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
}
