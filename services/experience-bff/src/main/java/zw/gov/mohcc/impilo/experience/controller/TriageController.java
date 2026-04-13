package zw.gov.mohcc.impilo.experience.controller;

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
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;

/**
 * Triage — delegates assessments to PCT {@code /v1/journeys/{id}/triage}.
 */
@RestController
@RequestMapping("/internal/v1/triage")
public class TriageController {

    private static final Logger log = LoggerFactory.getLogger(TriageController.class);

    private final PctServiceClient pctClient;

    public TriageController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    public record RecordTriageRequest(
            @NotBlank String patient_id,
            /** PCT journey id (ULID). When absent, {@code encounter_id} is used if it holds the journey id. */
            String pct_journey_id,
            String encounter_id,
            String queue_entry_id,
            @Min(1) @Max(5) int acuity,
            String chief_complaint,
            List<DangerSign> danger_signs,
            Map<String, Object> vitals,
            String notes,
            @NotBlank String triaged_by,
            String triaged_by_name
    ) {}

    public record DangerSign(
            String name,
            boolean present
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> recordTriage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordTriageRequest request) {

        String journeyId = firstNonBlank(request.pct_journey_id(), request.encounter_id());
        if (journeyId == null || journeyId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "pct_journey_id or encounter_id (journey id) is required for PCT triage");
        }

        Map<String, Object> vitals = request.vitals();
        String acuityStr = String.valueOf(request.acuity());

        JsonNode triage = pctClient.recordTriage(journeyId, acuityStr, vitals, request.notes());
        log.info("PCT triage recorded for patient={}, journey={}", request.patient_id(), journeyId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", triage != null ? triage : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTriageRecords(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "encounter_id") String encounterId) {
        if (patientId != null && !patientId.isBlank()) {
            try {
                JsonNode timeline = pctClient.getPatientTimeline(patientId);
                return ResponseEntity.ok(Map.of(
                        "data", timeline != null ? timeline : List.of(),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            } catch (Exception e) {
                log.warn("PCT getPatientTimeline for triage list failed: {}", e.getMessage());
            }
        }
        // TODO: wire to PctServiceClient when PCT exposes dedicated triage history by encounter
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
