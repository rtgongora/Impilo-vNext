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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.*;

/**
 * Triage assessment endpoints.
 *
 * <p>Records triage assessments with acuity (1-5), danger signs, vitals,
 * and clinical notes. Persists locally in the BFF for encounter workspace
 * access and delegates to PCT TriageService for sovereign journey lifecycle.</p>
 *
 * <p>Acuity scale (South African Triage Scale aligned):</p>
 * <ul>
 *   <li>1 = Red / Resuscitation — immediate</li>
 *   <li>2 = Orange / Emergency — very urgent (≤10 min)</li>
 *   <li>3 = Yellow / Urgent — urgent (≤60 min)</li>
 *   <li>4 = Green / Standard — less urgent (≤240 min)</li>
 *   <li>5 = Blue / Non-urgent — routine</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/triage")
public class TriageController {

    private static final Logger log = LoggerFactory.getLogger(TriageController.class);

    private final PctServiceClient pctClient;

        this.pctClient = pctClient;
    }

    public record RecordTriageRequest(
            @NotBlank String patient_id,
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

    /**
     * Record a triage assessment.
     * POST /internal/v1/triage
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> recordTriage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordTriageRequest request) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    /**
     * Get triage records for a patient or encounter.
     * GET /internal/v1/triage?patient_id= or encounter_id=
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listTriageRecords(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "encounter_id") String encounterId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }
}
