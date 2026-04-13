package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;

import java.util.*;

/**
 * Mobile discharge workflow endpoints.
 * POST /internal/v1/mobile/provider/discharge — discharge an encounter from mobile.
 * GET  /internal/v1/mobile/provider/discharge/{encounterId} — get discharge status.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/discharge")
public class MobileDischargeController {

    private static final Logger log = LoggerFactory.getLogger(MobileDischargeController.class);

    private final CostaServiceClient costaClient;
    private final PctServiceClient pctClient;
    private final InpatientServiceClient inpatientClient;

    public MobileDischargeController(CostaServiceClient costaClient,
                                     PctServiceClient pctClient,
                                     InpatientServiceClient inpatientClient) {
        this.costaClient = costaClient;
        this.pctClient = pctClient;
        this.inpatientClient = inpatientClient;
    }

    public record MobileDischargeRequest(
            @NotBlank String encounter_id,
            @NotBlank String discharge_type,
            String discharge_diagnosis,
            String treatment_summary,
            String follow_up_instructions,
            String medications_at_discharge,
            String patient_instructions,
            String discharged_by
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> dischargeEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody MobileDischargeRequest request) {

        UUID encounterId = UUID.fromString(request.encounter_id());

        // Delegate to COSTA: create bill draft for the discharged encounter
        String costaBillId = null;
        try {
            JsonNode billData = costaClient.createBillDraft(encounterId.toString(), "ENCOUNTER");
            if (billData != null && billData.has("billId")) {
                costaBillId = billData.get("billId").asText();
                log.info("COSTA bill draft created from mobile: billId={} for encounter={}", costaBillId, encounterId);
            }
        } catch (Exception e) {
            log.warn("COSTA bill draft creation from mobile failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", encounterId.toString());
        attributes.put("status", "DISCHARGED");
        attributes.put("discharge_type", request.discharge_type());
        if (costaBillId != null) attributes.put("costa_bill_id", costaBillId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", encounterId.toString(),
                "type", "Encounter",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{encounterId}")
    public ResponseEntity<Map<String, Object>> getDischargeStatus(
            @PathVariable UUID encounterId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode clearances = pctClient.getDischargeClearances(encounterId.toString());
            if (clearances != null) {
                return ResponseEntity.ok(Map.of(
                        "data", clearances,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception e) {
            log.warn("PCT getDischargeClearances failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
