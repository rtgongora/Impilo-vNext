package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.clinical.VitalsObservationBridge;

import java.util.List;
import java.util.Map;

/**
 * Vitals — the experience view over pct's discrete observations.
 *
 * <p>The wire contract stays "vitals" (rows of one contact's readings); the system of record
 * speaks coded observations. {@link VitalsObservationBridge} owns the translation in both
 * directions. Bodies are accepted in either dialect (snake_case and camelCase) because the two
 * existing writers disagree, and rejecting one of them would break a live clinical surface to
 * win a style argument.</p>
 */
@RestController
@RequestMapping("/internal/v1/vitals")
public class VitalsController {

    private static final Logger log = LoggerFactory.getLogger(VitalsController.class);

    private final PctServiceClient pctClient;
    private final VitalsObservationBridge bridge;

    public VitalsController(PctServiceClient pctClient, VitalsObservationBridge bridge) {
        this.pctClient = pctClient;
        this.bridge = bridge;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "encounter_id") String encounterId) {

        String cpid = patientId;
        if ((cpid == null || cpid.isBlank()) && encounterId != null && !encounterId.isBlank()) {
            try {
                long enc = Long.parseLong(encounterId.trim());
                JsonNode encNode = pctClient.getEncounter(enc);
                if (encNode != null && encNode.has("subjectCpid")) {
                    cpid = encNode.get("subjectCpid").asText();
                }
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "encounter_id must be a numeric PCT encounter id when patient_id is omitted");
            } catch (Exception e) {
                // Falling through would blame the caller's encounter_id (400) for what is
                // actually an upstream outage, and the 400 handler cannot tell the difference.
                log.error("PCT getEncounter for vitals list failed for encounter={}: {}",
                        encounterId, e.getMessage());
                return vitalsUnavailable(requestId, correlationId);
            }
        }

        if (cpid == null || cpid.isBlank()) {
            // Vitals are per-patient — without a resolvable patient there is no list to return.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "patient_id or a resolvable encounter_id is required to list vitals");
        }

        try {
            JsonNode observations = pctClient.listObservationsForSubject(cpid, null);
            List<Map<String, Object>> sets = bridge.composeSets(observations);
            int from = Math.min(Math.max(page, 0) * Math.max(size, 1), sets.size());
            int to = Math.min(from + Math.max(size, 1), sets.size());
            return ResponseEntity.ok(Map.of(
                    "data", sets.subList(from, to),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", Map.of("number", page, "size", size,
                                    "total_elements", sets.size(),
                                    "total_pages", (sets.size() + Math.max(size, 1) - 1) / Math.max(size, 1)))));
        } catch (Exception e) {
            // An empty 200 here reads as "this patient has no recorded observations" — the same
            // reassuring shape a stable patient produces, said exactly when the system knows
            // least. Fail loudly rather than fabricate the absence.
            log.error("PCT observation list failed for patient={}: {}", cpid, e.getMessage());
            return vitalsUnavailable(requestId, correlationId);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> request) {

        Object patientId = request.get("patient_id") != null ? request.get("patient_id") : request.get("patientId");
        if (patientId == null || String.valueOf(patientId).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }

        List<Map<String, Object>> observations;
        try {
            observations = bridge.toObservationBodies(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (observations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "at least one vital sign value is required");
        }

        try {
            JsonNode created = pctClient.recordObservationsBatch(observations);
            List<JsonNode> group = new java.util.ArrayList<>();
            if (created != null && created.isArray()) {
                created.forEach(group::add);
            }
            Map<String, Object> row = group.isEmpty() ? Map.of() : bridge.composeSetRow(group);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", row,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // pct rejected the write (validation, care-relationship). Its status is the truth.
            throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            // 201-shaped optimism here would claim a clinical record exists when nothing was
            // written. The readings are not in the record; say so.
            log.error("PCT observation batch failed for patient={}: {}", patientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "vitals_not_recorded",
                    "message", "The vital signs could not be recorded. They are not in the record.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private static ResponseEntity<Map<String, Object>> vitalsUnavailable(String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "vitals_unavailable",
                "message", "Vital signs could not be retrieved. Do not treat this as an "
                           + "absence of observations.",
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
