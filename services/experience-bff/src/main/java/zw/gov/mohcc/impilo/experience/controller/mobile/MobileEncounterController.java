package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobile provider encounter endpoints — PCT proxy aligned with {@link zw.gov.mohcc.impilo.experience.controller.EncounterController}.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/encounters")
public class MobileEncounterController {

    private static final Logger log = LoggerFactory.getLogger(MobileEncounterController.class);

    private final PctServiceClient pctClient;
    private final CostaServiceClient costaClient;

    public MobileEncounterController(PctServiceClient pctClient, CostaServiceClient costaClient) {
        this.pctClient = pctClient;
        this.costaClient = costaClient;
    }

    public record CreateEncounterRequest(
            @NotBlank String patient_id,
            @NotBlank String facility_id,
            @NotBlank String encounter_type,
            String chief_complaint,
            @NotBlank String pct_journey_id,
            String patient_cpid
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listEncounters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode pctData = pctClient.getPatientTimeline(patientId);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getPatientTimeline (mobile) failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateEncounterRequest request) {

        JsonNode pctEncounter = pctClient.startEncounter(
                request.pct_journey_id(), request.encounter_type());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", pctEncounter != null ? pctEncounter : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> closeEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        long encId = parsePctEncounterId(id);
        JsonNode pct = pctClient.completeEncounter(encId);
        String costaBillId = null;
        try {
            JsonNode billData = costaClient.createBillDraft(String.valueOf(encId), "ENCOUNTER");
            if (billData != null && billData.has("billId")) {
                costaBillId = billData.get("billId").asText();
            }
        } catch (Exception e) {
            log.warn("COSTA bill draft from mobile close failed (non-blocking): {}", e.getMessage());
        }
        Map<String, Object> meta = new LinkedHashMap<>(Map.of(
                "request_id", requestId,
                "correlation_id", correlationId));
        if (costaBillId != null) {
            meta.put("costa_bill_id", costaBillId);
        }
        return ResponseEntity.ok(Map.of(
                "data", pct != null ? pct : Map.of(),
                "meta", meta));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<Map<String, Object>> getEncounterSummary(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        long encId = parsePctEncounterId(id);
        JsonNode enc = pctClient.getEncounter(encId);
        String cpid = enc != null && enc.has("subjectCpid") ? enc.get("subjectCpid").asText() : null;
        JsonNode summary = cpid != null && !cpid.isBlank() ? pctClient.getPatientHealthSummary(cpid) : null;

        Map<String, Object> data = new LinkedHashMap<>();
        if (enc != null) {
            data.put("encounter", enc);
        }
        if (summary != null) {
            data.put("patient_health_summary", summary);
        }
        return ResponseEntity.ok(Map.of(
                "data", data.isEmpty() ? Map.of() : data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static long parsePctEncounterId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Encounter id must be the numeric PCT encounter id");
        }
    }
}
