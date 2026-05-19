package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.*;

/**
 * Mobile provider triage endpoints.
 *
 * <p>POST /internal/v1/mobile/provider/triage — record triage from mobile</p>
 * <p>GET  /internal/v1/mobile/provider/triage?encounter_id= or patient_id= — list triage records</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/triage")
public class MobileTriageController {

    private static final Logger log = LoggerFactory.getLogger(MobileTriageController.class);

    private final PctServiceClient pctClient;

    public MobileTriageController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    public record RecordTriageRequest(
            @NotBlank String patient_id,
            String encounter_id,
            String queue_entry_id,
            @Min(1) @Max(5) int acuity,
            String chief_complaint,
            List<Map<String, Object>> danger_signs,
            Map<String, Object> vitals,
            String notes,
            @NotBlank String triaged_by,
            String triaged_by_name
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> recordTriage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordTriageRequest request) {

        // Delegate to PCT
        String journeyId = request.encounter_id();
        if (journeyId != null && !journeyId.isBlank()) {
            try {
                JsonNode result = pctClient.recordTriage(
                        journeyId,
                        String.valueOf(request.acuity()),
                        request.vitals(),
                        request.notes());
                if (result != null) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("data", result);
                    response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                }
                return upstreamFailure("PCT_UNAVAILABLE", "No triage payload returned", requestId, correlationId);
            } catch (Exception e) {
                log.warn("PCT triage delegation from mobile failed: {}", e.getMessage());
                return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
            }
        }

        return ResponseEntity.badRequest().body(Map.of(
                "error", Map.of(
                        "code", "MISSING_ENCOUNTER_ID",
                        "message", "encounter_id (PCT journey id) is required"),
                "meta", Map.of(
                        "request_id", requestId,
                        "correlation_id", correlationId)
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTriage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "encounter_id") String encounterId,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        try {
            if (encounterId != null && !encounterId.isBlank()) {
                JsonNode result = pctClient.getJourney(encounterId);
                if (result != null && result.has("triage")) {
                    return ResponseEntity.ok(Map.of(
                            "data", result.get("triage"),
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                    ));
                }
                return upstreamFailure("PCT_UNAVAILABLE", "No triage payload returned for journey", requestId, correlationId);
            }
            if (patientId != null && !patientId.isBlank()) {
                JsonNode result = pctClient.listJourneys(patientId, 0, 10);
                if (result != null && result.isArray()) {
                    List<Object> triages = new ArrayList<>();
                    for (JsonNode journey : result) {
                        if (journey.has("triage")) {
                            triages.add(journey.get("triage"));
                        }
                    }
                    return ResponseEntity.ok(Map.of(
                            "data", triages,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                    ));
                }
                return upstreamFailure("PCT_UNAVAILABLE", "No journey list payload returned", requestId, correlationId);
            }
        } catch (Exception e) {
            log.warn("PCT triage list failed: {}", e.getMessage());
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", Map.of("code", "MISSING_QUERY_FILTER", "message", "Provide encounter_id or patient_id"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Triage upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
