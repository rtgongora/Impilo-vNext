package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.domain.Encounter;
import zw.gov.mohcc.impilo.experience.repository.EncounterRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

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

    private final EncounterRepository encounterRepository;
    private final OutboxService outboxService;
    private final CostaServiceClient costaClient;
    private final JdbcTemplate jdbcTemplate;

    public MobileDischargeController(EncounterRepository encounterRepository,
                                      OutboxService outboxService,
                                      CostaServiceClient costaClient,
                                      JdbcTemplate jdbcTemplate) {
        this.encounterRepository = encounterRepository;
        this.outboxService = outboxService;
        this.costaClient = costaClient;
        this.jdbcTemplate = jdbcTemplate;
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
    @Transactional
    public ResponseEntity<Map<String, Object>> dischargeEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody MobileDischargeRequest request) {

        UUID encounterId = UUID.fromString(request.encounter_id());

        Encounter encounter = encounterRepository.findByIdAndTenantId(encounterId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + request.encounter_id()));

        encounter.discharge(
                request.discharge_type(),
                request.discharge_diagnosis(),
                request.treatment_summary(),
                request.follow_up_instructions(),
                request.medications_at_discharge(),
                request.patient_instructions(),
                request.discharged_by()
        );
        encounterRepository.save(encounter);

        // Delegate to COSTA: create bill draft for the discharged encounter
        String costaBillId = null;
        try {
            JsonNode billData = costaClient.createBillDraft(encounterId.toString(), "ENCOUNTER");
            if (billData != null && billData.has("billId")) {
                costaBillId = billData.get("billId").asText();
                jdbcTemplate.update(
                        "UPDATE encounters SET costa_bill_id = ? WHERE id = ? AND tenant_id = ?",
                        costaBillId, encounterId, tenantId);
                log.info("COSTA bill draft created from mobile: billId={} for encounter={}", costaBillId, encounterId);
            }
        } catch (Exception e) {
            log.warn("COSTA bill draft creation from mobile failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("encounter_id", encounterId.toString());
        eventPayload.put("discharge_type", request.discharge_type());
        eventPayload.put("status", encounter.getStatus());
        eventPayload.put("source", "mobile");
        if (costaBillId != null) eventPayload.put("costa_bill_id", costaBillId);

        outboxService.writeOutboxEvent(
                "impilo.experience.encounter.discharged.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Encounter",
                encounterId.toString(),
                eventPayload,
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", encounterId.toString());
        attributes.put("status", encounter.getStatus());
        attributes.put("discharge_type", request.discharge_type());
        attributes.put("discharged_at", encounter.getDischargedAt());
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

        Encounter encounter = encounterRepository.findByIdAndTenantId(encounterId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + encounterId));

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", encounterId.toString());
        attributes.put("status", encounter.getStatus());
        attributes.put("discharge_type", encounter.getDischargeType());
        attributes.put("discharge_diagnosis", encounter.getDischargeDiagnosis());
        attributes.put("treatment_summary", encounter.getTreatmentSummary());
        attributes.put("follow_up_instructions", encounter.getFollowUpInstructions());
        attributes.put("medications_at_discharge", encounter.getMedicationsAtDischarge());
        attributes.put("patient_instructions", encounter.getPatientInstructions());
        attributes.put("discharged_by", encounter.getDischargedBy());
        attributes.put("discharged_at", encounter.getDischargedAt());

        // Include COSTA bill reference if available
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT costa_bill_id FROM encounters WHERE id = ? AND tenant_id = ?",
                    encounterId, tenantId);
            if (!rows.isEmpty() && rows.get(0).get("costa_bill_id") != null) {
                attributes.put("costa_bill_id", rows.get(0).get("costa_bill_id").toString());
            }
        } catch (Exception e) {
            // Non-critical
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", encounterId.toString(),
                "type", "DischargeStatus",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
