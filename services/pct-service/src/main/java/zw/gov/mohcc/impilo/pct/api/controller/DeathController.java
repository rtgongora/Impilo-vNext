package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.RecordDeathRequest;
import zw.gov.mohcc.impilo.pct.core.DeathWorkflow;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathCaseEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the patient death recording workflow.
 *
 * <p>Provides endpoints for recording a patient death, updating the
 * UBOMI (civil registration) notification status, and completing the
 * death case workflow.</p>
 */
@RestController
@RequestMapping("/v1")
public class DeathController {

    private static final Logger log = LoggerFactory.getLogger(DeathController.class);

    private final DeathWorkflow deathWorkflow;

    public DeathController(DeathWorkflow deathWorkflow) {
        this.deathWorkflow = deathWorkflow;
    }

    /**
     * Record a patient death within a journey.
     *
     * @param id      the journey identifier
     * @param request the death recording request with pronouncement details
     * @return the created death case entity
     */
    @PostMapping("/journeys/{id}/death/record")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> recordDeath(
            @PathVariable String id,
            @Valid @RequestBody RecordDeathRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DeathCaseEntity deathCase = deathWorkflow.recordDeath(
                id, request.pronouncedBy(), request.pronouncedAt());

        return ResponseEntity.ok(ApiResponse.ok(deathCase, correlationId));
    }

    /**
     * Update the UBOMI (civil registration) notification status for a death case.
     *
     * <p>This endpoint is called by the integration layer or external systems
     * to report the UBOMI processing status of a death notification.</p>
     *
     * @param caseId the death case identifier
     * @param body   request body containing {@code ubomiNotificationId} and {@code ubomiStatus}
     * @return the updated death case entity
     */
    @PostMapping("/death/{caseId}/ubomi-status")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> updateUbomiStatus(
            @PathVariable UUID caseId,
            @RequestBody Map<String, String> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String ubomiNotificationId = body.get("ubomiNotificationId");
        String ubomiStatus = body.get("ubomiStatus");

        DeathCaseEntity deathCase = deathWorkflow.updateUbomiStatus(
                caseId, ubomiNotificationId, ubomiStatus);

        return ResponseEntity.ok(ApiResponse.ok(deathCase, correlationId));
    }

    /**
     * Complete the death case workflow.
     *
     * @param caseId the death case identifier
     * @return the completed death case entity
     */
    @PostMapping("/death/{caseId}/complete")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> completeDeath(@PathVariable UUID caseId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DeathCaseEntity deathCase = deathWorkflow.completeDeath(caseId);

        return ResponseEntity.ok(ApiResponse.ok(deathCase, correlationId));
    }
}
