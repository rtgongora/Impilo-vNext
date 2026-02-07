package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.ClearBlockerRequest;
import zw.gov.mohcc.impilo.pct.api.dto.StartDischargeRequest;
import zw.gov.mohcc.impilo.pct.core.DischargeWorkflow;
import zw.gov.mohcc.impilo.pct.persistence.entity.DischargeCaseEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

/**
 * REST controller for the patient discharge workflow.
 *
 * <p>Provides endpoints for initiating discharge, querying discharge
 * status, clearing individual blockers, and completing the discharge
 * process. Each blocker represents a clearance gate that must be
 * satisfied before the patient can leave the facility.</p>
 */
@RestController
@RequestMapping("/v1")
public class DischargeController {

    private static final Logger log = LoggerFactory.getLogger(DischargeController.class);

    private final DischargeWorkflow dischargeWorkflow;

    public DischargeController(DischargeWorkflow dischargeWorkflow) {
        this.dischargeWorkflow = dischargeWorkflow;
    }

    /**
     * Start the discharge process for a patient journey.
     *
     * @param id      the journey identifier
     * @param request the discharge start request containing the discharge type
     * @return the created discharge case entity with active blockers
     */
    @PostMapping("/journeys/{id}/discharge/start")
    public ResponseEntity<ApiResponse<DischargeCaseEntity>> startDischarge(
            @PathVariable String id,
            @Valid @RequestBody StartDischargeRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DischargeCaseEntity dischargeCase = dischargeWorkflow.startDischarge(
                id, request.dischargeType());

        return ResponseEntity.ok(ApiResponse.ok(dischargeCase, correlationId));
    }

    /**
     * Retrieve the current status of a discharge case.
     *
     * @param caseId the discharge case identifier
     * @return the discharge case entity with current blocker state
     */
    @GetMapping("/discharge/{caseId}/status")
    public ResponseEntity<ApiResponse<DischargeCaseEntity>> getDischargeStatus(
            @PathVariable UUID caseId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DischargeCaseEntity dischargeCase = dischargeWorkflow.getDischargeStatus(caseId);

        return ResponseEntity.ok(ApiResponse.ok(dischargeCase, correlationId));
    }

    /**
     * Clear a specific discharge blocker.
     *
     * @param caseId  the discharge case identifier
     * @param request the clear blocker request containing the blocker name
     * @return the updated discharge case entity
     */
    @PostMapping("/discharge/{caseId}/clear-blocker")
    public ResponseEntity<ApiResponse<DischargeCaseEntity>> clearBlocker(
            @PathVariable UUID caseId,
            @Valid @RequestBody ClearBlockerRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DischargeCaseEntity dischargeCase = dischargeWorkflow.clearBlocker(
                caseId, request.blockerName());

        return ResponseEntity.ok(ApiResponse.ok(dischargeCase, correlationId));
    }

    /**
     * Complete the discharge process.
     *
     * @param caseId the discharge case identifier
     * @return the completed discharge case entity
     */
    @PostMapping("/discharge/{caseId}/complete")
    public ResponseEntity<ApiResponse<DischargeCaseEntity>> completeDischarge(
            @PathVariable UUID caseId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DischargeCaseEntity dischargeCase = dischargeWorkflow.completeDischarge(caseId);

        return ResponseEntity.ok(ApiResponse.ok(dischargeCase, correlationId));
    }
}
