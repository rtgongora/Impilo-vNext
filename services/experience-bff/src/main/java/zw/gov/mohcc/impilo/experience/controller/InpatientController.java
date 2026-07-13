package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.service.CoreTransactionCompositionService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inpatient admissions proxy for the web shell ({@code /internal/v1/inpatient/**}).
 * Delegates to inpatient-service sovereign admissions API.
 */
@RestController
@RequestMapping("/internal/v1/inpatient")
public class InpatientController {

    private static final Logger log = LoggerFactory.getLogger(InpatientController.class);

    private final InpatientServiceClient inpatientClient;
    private final TshepoAuditServiceClient auditClient;

    public InpatientController(InpatientServiceClient inpatientClient,
                              TshepoAuditServiceClient auditClient) {
        this.inpatientClient = inpatientClient;
        this.auditClient = auditClient;
    }

    private static JsonNode requirePayload(JsonNode node, String operation) {
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + ": upstream returned no payload");
        }
        return node;
    }

    private static String extractAdmissionRef(JsonNode created) {
        if (created == null || created.isNull()) {
            return null;
        }
        String direct = created.path("admissionRef").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        direct = created.path("id").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return created.path("admissionId").asText(null);
    }

    private static ResponseStatusException upstreamFailure(String operation, Exception cause) {
        log.warn("{} failed: {}", operation, cause.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + " failed", cause);
    }

    /**
     * Emit governance audit for a created admission (and the bed assignment when a bed was chosen).
     * Best-effort — an audit failure must not fail the admission the clinician already completed.
     */
    private void emitAdmissionAudit(String tenantId, String correlationId, String actorId, String actorType,
                                    String purposeOfUse, String facilityId, String admissionRef,
                                    Object bedId, Object subjectCpid) {
        try {
            String subjectRef = admissionRef != null ? "admission:" + admissionRef
                    : (subjectCpid != null ? "patient:" + subjectCpid : "admission:unknown");
            auditClient.ingestAuditEvent(auditBody(tenantId, correlationId, actorId, actorType, purposeOfUse,
                    facilityId, "INPATIENT_ADMISSION_CREATED", subjectRef, "ADMISSION",
                    admissionRef != null ? admissionRef : "unknown"));
            if (bedId != null && !String.valueOf(bedId).isBlank()) {
                auditClient.ingestAuditEvent(auditBody(tenantId, correlationId, actorId, actorType, purposeOfUse,
                        facilityId, "INPATIENT_BED_ASSIGNED", subjectRef, "BED", String.valueOf(bedId)));
            }
        } catch (Exception e) {
            log.warn("Inpatient admission audit emit failed (non-fatal): {}", e.getMessage());
        }
    }

    private static Map<String, Object> auditBody(String tenantId, String correlationId, String actorId,
                                                 String actorType, String purposeOfUse, String facilityId,
                                                 String eventType, String subjectRef, String resourceType,
                                                 String resourceId) {
        Map<String, Object> b = new LinkedHashMap<>();
        if (tenantId != null && !tenantId.isBlank()) {
            try { b.put("tenantId", java.util.UUID.fromString(tenantId.trim())); } catch (IllegalArgumentException ignored) { }
        }
        b.put("eventType", eventType);
        b.put("actorId", actorId != null && !actorId.isBlank() ? actorId : "SYSTEM");
        b.put("actorType", actorType != null && !actorType.isBlank() ? actorType : "PROVIDER");
        b.put("subjectRef", subjectRef);
        b.put("resourceType", resourceType);
        b.put("resourceId", resourceId != null && resourceId.length() > 255 ? resourceId.substring(0, 255) : resourceId);
        b.put("action", eventType);
        b.put("outcome", "SUCCESS");
        b.put("purposeOfUse", purposeOfUse != null && !purposeOfUse.isBlank() ? purposeOfUse : "INPATIENT_CARE");
        b.put("correlationId", correlationId);
        if (facilityId != null && !facilityId.isBlank()) {
            try { b.put("facilityId", java.util.UUID.fromString(facilityId.trim())); } catch (IllegalArgumentException ignored) { }
        }
        return b;
    }

    @GetMapping("/admissions")
    public ResponseEntity<Map<String, Object>> listAdmissions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_cpid") String patientCpid,
            @RequestParam(required = false, name = "patientId") String patientId) {
        String cpid = patientCpid != null && !patientCpid.isBlank() ? patientCpid : patientId;
        try {
            JsonNode data = requirePayload(inpatientClient.listAdmissions(cpid), "Inpatient listAdmissions");
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listAdmissions", e);
        }
    }

    @GetMapping("/handovers")
    public ResponseEntity<Map<String, Object>> listHandovers(
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        if (facilityId == null || facilityId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MISSING_FACILITY_ID", "message", "facility_id is required"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode data = inpatientClient.listHandovers(
                    facilityId.trim(), status != null && !status.isBlank() ? status : "PENDING");
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : java.util.List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listHandovers", e);
        }
    }

    @GetMapping("/admissions/active")
    public ResponseEntity<Map<String, Object>> getActiveAdmissions(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        if (subjectCpid == null || subjectCpid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MISSING_SUBJECT_CPID", "message", "subject_cpid is required"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        if (facilityId == null || facilityId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MISSING_FACILITY_ID", "message", "facility_id is required"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode data = inpatientClient.getActiveAdmissions(subjectCpid.trim(), facilityId.trim());
            if (data == null || data.isNull()) {
                return ResponseEntity.ok(Map.of(
                        "data", null,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            JsonNode primary = data.isArray() && !data.isEmpty() ? data.get(0) : data;
            String admissionRef = extractAdmissionRef(primary);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            if (admissionRef != null) {
                meta.put("admission_ref", admissionRef);
                meta.put("core_transaction_id", CoreTransactionCompositionService.admissionTransactionId(admissionRef));
            }
            return ResponseEntity.ok(Map.of("data", primary, "meta", meta));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getActiveAdmissions", e);
        }
    }

    /**
     * Resolved current inpatient location for a patient — the active admission's ward + bed
     * with human-readable labels, or {@code data: null} when the patient is not admitted.
     * Backs the experience-shell patient-location badge (G053). {@code facility_id} is optional.
     */
    @GetMapping("/admissions/current-location")
    public ResponseEntity<Map<String, Object>> getCurrentLocation(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        if (subjectCpid == null || subjectCpid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MISSING_SUBJECT_CPID", "message", "subject_cpid is required"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        Map<String, Object> meta = Map.of("request_id", requestId, "correlation_id", correlationId);
        try {
            JsonNode data = inpatientClient.getCurrentLocation(
                    subjectCpid.trim(), facilityId != null ? facilityId.trim() : null);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("data", data == null || data.isNull() ? null : data);
            body.put("meta", meta);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getCurrentLocation", e);
        }
    }

    @GetMapping("/admissions/{admissionRef}")
    public ResponseEntity<Map<String, Object>> getAdmission(
            @PathVariable String admissionRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = requirePayload(inpatientClient.getAdmission(admissionRef), "Inpatient getAdmission");
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getAdmission", e);
        }
    }

    @PostMapping("/admissions")
    public ResponseEntity<Map<String, Object>> createAdmission(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestBody Map<String, Object> body) {
        try {
            // Inject the tenant from the trust header so the UI doesn't have to carry it
            // in the body (inpatient-service requires tenantId on the admission request).
            // Copy first — the inbound body may be immutable.
            Map<String, Object> admissionBody = new LinkedHashMap<>(body);
            if (tenantId != null && !tenantId.isBlank()) {
                admissionBody.putIfAbsent("tenantId", tenantId);
            }
            JsonNode created = requirePayload(inpatientClient.createAdmission(admissionBody), "Inpatient createAdmission");
            String admissionRef = extractAdmissionRef(created);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            if (admissionRef != null) {
                meta.put("admission_ref", admissionRef);
                meta.put("core_transaction_id", CoreTransactionCompositionService.admissionTransactionId(admissionRef));
            }
            emitAdmissionAudit(tenantId, correlationId, actorId, actorType, purposeOfUse, facilityId,
                    admissionRef, admissionBody.get("bedId"), admissionBody.get("subjectCpid"));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created,
                    "meta", meta));
        } catch (HttpStatusCodeException e) {
            // A bed-safety violation (409) or invalid request (400) is meaningful to the
            // clinician placing the admission — pass it through with the upstream message
            // instead of masking it as a 502. Nothing was persisted (the create is atomic).
            if (e.getStatusCode().is4xxClientError()) {
                return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                        "error", Map.of(
                                "code", "ADMISSION_" + e.getStatusCode().value(),
                                "message", extractUpstreamMessage(e)),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            throw upstreamFailure("Inpatient createAdmission", e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient createAdmission", e);
        }
    }

    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    /** Pull a human-readable message out of an upstream error body ({error:{message}} or {message}). */
    private static String extractUpstreamMessage(HttpStatusCodeException ce) {
        String body = ce.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = ERROR_MAPPER.readTree(body);
                JsonNode err = node.get("error");
                if (err != null && err.isObject() && err.hasNonNull("message")) {
                    return err.get("message").asText();
                }
                if (node.hasNonNull("message") && !node.get("message").asText().isBlank()) {
                    return node.get("message").asText();
                }
            } catch (Exception ignored) {
                // fall through to status reason
            }
        }
        return ce.getStatusText();
    }

    @PostMapping("/admissions/{admissionRef}/discharge")
    public ResponseEntity<Map<String, Object>> dischargeAdmission(
            @PathVariable String admissionRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(
                    inpatientClient.dischargeAdmission(admissionRef, body),
                    "Inpatient dischargeAdmission");
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            meta.put("admission_ref", admissionRef);
            meta.put("core_transaction_id", CoreTransactionCompositionService.admissionTransactionId(admissionRef));
            return ResponseEntity.ok(Map.of("data", data, "meta", meta));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient dischargeAdmission", e);
        }
    }

    @PostMapping("/admissions/{admissionRef}/transfer")
    public ResponseEntity<Map<String, Object>> transferAdmission(
            @PathVariable String admissionRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(
                    inpatientClient.transferPatient(admissionRef, normalizeTransferBody(body)),
                    "Inpatient transferPatient");
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            meta.put("admission_ref", admissionRef);
            meta.put("core_transaction_id", CoreTransactionCompositionService.admissionTransactionId(admissionRef));
            return ResponseEntity.ok(Map.of("data", data, "meta", meta));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient transferPatient", e);
        }
    }

    @GetMapping("/admissions/{admissionRef}/ward-rounds")
    public ResponseEntity<Map<String, Object>> listWardRounds(
            @PathVariable String admissionRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = requirePayload(
                    inpatientClient.listWardRounds(admissionRef),
                    "Inpatient listWardRounds");
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listWardRounds", e);
        }
    }

    // ── Deterioration escalations (WS#6) ─────────────────────────────

    @GetMapping("/escalations")
    public ResponseEntity<Map<String, Object>> listEscalations(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String wardId) {
        try {
            JsonNode data = requirePayload(inpatientClient.listEscalations(patientId, wardId),
                    "Inpatient listEscalations");
            return ResponseEntity.ok(Map.of("data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listEscalations", e);
        }
    }

    @PostMapping("/escalations/{escalationId}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeEscalation(
            @PathVariable String escalationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = requirePayload(inpatientClient.acknowledgeEscalation(escalationId),
                    "Inpatient acknowledgeEscalation");
            return ResponseEntity.ok(Map.of("data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient acknowledgeEscalation", e);
        }
    }

    @PostMapping("/escalations/{escalationId}/respond")
    public ResponseEntity<Map<String, Object>> respondEscalation(
            @PathVariable String escalationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(
                    inpatientClient.respondEscalation(escalationId, body == null ? Map.of() : body),
                    "Inpatient respondEscalation");
            return ResponseEntity.ok(Map.of("data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient respondEscalation", e);
        }
    }

    // ── Discharge summary (WS#6) ─────────────────────────────────────

    @PostMapping("/discharge-summary")
    public ResponseEntity<Map<String, Object>> saveDischargeSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.saveDischargeSummary(body),
                    "Inpatient saveDischargeSummary");
            return ResponseEntity.ok(Map.of("data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient saveDischargeSummary", e);
        }
    }

    @GetMapping("/discharge-summary")
    public ResponseEntity<Map<String, Object>> getDischargeSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String encounterId) {
        try {
            JsonNode data = requirePayload(inpatientClient.getDischargeSummary(encounterId),
                    "Inpatient getDischargeSummary");
            return ResponseEntity.ok(Map.of("data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getDischargeSummary", e);
        }
    }

    @PostMapping("/discharge-summary/{encounterId}/finalise")
    public ResponseEntity<Map<String, Object>> finaliseDischargeSummary(
            @PathVariable String encounterId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = requirePayload(inpatientClient.finaliseDischargeSummary(encounterId),
                    "Inpatient finaliseDischargeSummary");
            return ResponseEntity.ok(Map.of("data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient finaliseDischargeSummary", e);
        }
    }

    @PostMapping("/discharge-summary/{encounterId}/countersign")
    public ResponseEntity<Map<String, Object>> countersignDischargeSummary(
            @PathVariable String encounterId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.countersignDischargeSummary(encounterId, body),
                    "Inpatient countersignDischargeSummary");
            return ResponseEntity.ok(Map.of("data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient countersignDischargeSummary", e);
        }
    }

    private static Map<String, Object> normalizeTransferBody(Map<String, Object> body) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (body == null) {
            return normalized;
        }
        copyTransferField(body, normalized, "toWardId", "to_ward_id");
        copyTransferField(body, normalized, "toBedId", "to_bed_id");
        if (body.containsKey("reason")) {
            normalized.put("reason", body.get("reason"));
        }
        return normalized;
    }

    private static void copyTransferField(Map<String, Object> source,
                                          Map<String, Object> target,
                                          String camelKey,
                                          String snakeKey) {
        Object value = source.get(camelKey);
        if (value == null) {
            value = source.get(snakeKey);
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(camelKey, value);
        }
    }
}
