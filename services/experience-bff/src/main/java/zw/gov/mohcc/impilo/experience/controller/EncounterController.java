package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Encounter management — proxies PCT; falls back to local state when PCT is unavailable.
 */
@RestController
@RequestMapping("/internal/v1/encounters")
public class EncounterController {

    private static final Logger log = LoggerFactory.getLogger(EncounterController.class);

    private final PctServiceClient pctClient;
    private final CostaServiceClient costaClient;
    private static final List<Map<String, Object>> LOCAL_ENCOUNTERS = new CopyOnWriteArrayList<>();

    public EncounterController(PctServiceClient pctClient, CostaServiceClient costaClient) {
        this.pctClient = pctClient;
        this.costaClient = costaClient;
    }

    public record CreateEncounterRequest(
            @JsonProperty("patient_id") String patientId,
            @JsonProperty("facility_id") String facilityId,
            @JsonProperty("encounter_type") String encounterType,
            @JsonProperty("chief_complaint") String chiefComplaint,
            @JsonProperty("journey_id") String journeyId,
            @JsonProperty("cpid") String cpid) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listEncounters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        // Try PCT
        if (patientId != null && !patientId.isBlank()) {
            try {
                JsonNode pctData = pctClient.getPatientTimeline(patientId);
                if (pctData != null) {
                    return ResponseEntity.ok(Map.of(
                            "data", pctData,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            } catch (Exception e) {
                log.debug("PCT unavailable for encounter list: {}", e.getMessage());
            }
        }

        // Fallback
        List<Map<String, Object>> filtered = LOCAL_ENCOUNTERS;
        if (patientId != null) {
            filtered = filtered.stream()
                    .filter(e -> patientId.equals(((Map<?, ?>) e.get("attributes")).get("patientId")))
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(Map.of(
                "data", filtered,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // Try PCT (numeric IDs)
        try {
            long encId = Long.parseLong(id.trim());
            JsonNode data = pctClient.getEncounter(encId);
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (NumberFormatException ignored) {
            // Non-numeric ID — look up in local store
        } catch (Exception e) {
            log.debug("PCT getEncounter failed: {}", e.getMessage());
        }

        // Fallback: local
        return LOCAL_ENCOUNTERS.stream()
                .filter(e -> e.get("id").equals(id))
                .findFirst()
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("data", e);
                    r.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                    return ResponseEntity.ok(r);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", Map.of("code", "NOT_FOUND", "message", "Encounter not found"))));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody CreateEncounterRequest body) {

        String patientId = body.patientId();
        String facilityId = body.facilityId();
        String encounterType = body.encounterType();
        String chiefComplaint = body.chiefComplaint();
        String journeyId = body.journeyId();
        String cpid = body.cpid();

        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "patient_id is required")));
        }

        // Try PCT
        if (journeyId != null && !journeyId.isBlank()) {
            try {
                JsonNode pctEnc = pctClient.startEncounter(journeyId, encounterType);
                if (pctEnc != null) {
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                            "data", pctEnc,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            } catch (Exception e) {
                log.info("PCT startEncounter failed — using local fallback: {}", e.getMessage());
            }
        }

        // Fallback: create local encounter
        String id = "enc-" + UUID.randomUUID().toString().substring(0, 8);
        String now = OffsetDateTime.now().toString();

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("patientId", patientId);
        attrs.put("facilityId", facilityId);
        attrs.put("encounterType", encounterType != null ? encounterType : "OUTPATIENT");
        attrs.put("chiefComplaint", chiefComplaint);
        attrs.put("status", "IN_PROGRESS");
        attrs.put("startedAt", now);
        attrs.put("cpid", cpid);

        Map<String, Object> encounter = Map.of("id", id, "type", "Encounter", "attributes", attrs);
        LOCAL_ENCOUNTERS.add(encounter);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", encounter);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> closeEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("status", "COMPLETED");
        attrs.put("closedAt", OffsetDateTime.now().toString());
        if (body != null && body.get("diagnosis") != null) {
            attrs.put("diagnosis", body.get("diagnosis"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id, "type", "Encounter", "attributes", attrs));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/discharge")
    public ResponseEntity<Map<String, Object>> dischargeEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("status", "DISCHARGED");
        attrs.put("dischargedAt", OffsetDateTime.now().toString());
        if (body != null) {
            attrs.put("dischargeType", body.getOrDefault("discharge_type", "ROUTINE"));
            attrs.put("treatmentSummary", body.get("treatment_summary"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id, "type", "Encounter", "attributes", attrs));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static String strVal(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v.toString();
        }
        return null;
    }
}
