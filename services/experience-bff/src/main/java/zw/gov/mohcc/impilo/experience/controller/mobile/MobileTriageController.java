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
            } catch (Exception e) {
                log.warn("PCT triage delegation from mobile failed (non-blocking): {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTriage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "encounter_id") String encounterId,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        return ResponseEntity.ok(Map.of("data", List.of()));
    }
}
