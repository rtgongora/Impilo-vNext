package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.BodyReceiveRequest;
import zw.gov.mohcc.impilo.pct.api.dto.BodyReleaseRequest;
import zw.gov.mohcc.impilo.pct.api.dto.CertifyCauseOfDeathRequest;
import zw.gov.mohcc.impilo.pct.api.dto.CommunityDeathReportRequest;
import zw.gov.mohcc.impilo.pct.api.dto.ConfirmDeathRequest;
import zw.gov.mohcc.impilo.pct.api.dto.FieldBodyManagementRequest;
import zw.gov.mohcc.impilo.pct.api.dto.PublicHealthScreenRequest;
import zw.gov.mohcc.impilo.pct.api.dto.RecordDeathRequest;
import zw.gov.mohcc.impilo.pct.api.dto.VerbalAutopsyRequest;
import zw.gov.mohcc.impilo.pct.core.DeathWorkflow;
import zw.gov.mohcc.impilo.pct.persistence.entity.BodyCustodyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathAuditEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathCaseEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FieldBodyManagementEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.VerbalAutopsyEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
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

    // ── WS#8 — Death & Post-Death Pathway spine ──

    private String cid() { return TrustContextHolder.require().correlationId().toString(); }

    /** Confirm a death by an authorised provider and open/update the death case (runs medico-legal screen). */
    @PostMapping("/death/confirm")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> confirmDeath(
            @Valid @RequestBody ConfirmDeathRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.confirmDeath(request), cid()));
    }

    /** Confirm a body brought in dead to the facility (facility receipt + mortuary + medico-legal apply). */
    @PostMapping("/death/brought-in-dead")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> broughtInDead(
            @Valid @RequestBody ConfirmDeathRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.confirmBroughtInDead(request), cid()));
    }

    /**
     * Report a community / field death (unverified) — by a CHW, family, surveillance team, etc. A
     * report is NOT a confirmation: the case opens in status REPORTED with no asserted cause.
     */
    @PostMapping("/death/community-report")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> communityReport(
            @Valid @RequestBody CommunityDeathReportRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.reportCommunityDeath(request), cid()));
    }

    /** Record a verbal-autopsy interview for a community/field death (WHO VA — probable cause only). */
    @PostMapping("/death/{caseId}/verbal-autopsy")
    public ResponseEntity<ApiResponse<VerbalAutopsyEntity>> verbalAutopsy(
            @PathVariable UUID caseId, @Valid @RequestBody VerbalAutopsyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.recordVerbalAutopsy(caseId, request), cid()));
    }

    /** List verbal-autopsy interviews recorded against a death case. */
    @GetMapping("/death/{caseId}/verbal-autopsy")
    public ResponseEntity<ApiResponse<List<VerbalAutopsyEntity>>> listVerbalAutopsies(
            @PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.listVerbalAutopsies(caseId), cid()));
    }

    /** Record non-facility (field) body management — safe burial / release / handover (no mortuary custody). */
    @PostMapping("/death/{caseId}/field-body-management")
    public ResponseEntity<ApiResponse<FieldBodyManagementEntity>> fieldBodyManagement(
            @PathVariable UUID caseId, @Valid @RequestBody FieldBodyManagementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.recordFieldBodyManagement(caseId, request), cid()));
    }

    /** List field body-management records for a death case. */
    @GetMapping("/death/{caseId}/field-body-management")
    public ResponseEntity<ApiResponse<List<FieldBodyManagementEntity>>> listFieldBodyManagement(
            @PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.listFieldBodyManagement(caseId), cid()));
    }

    /** List death cases for the active facility (death dashboard). */
    @GetMapping("/death/cases")
    public ResponseEntity<ApiResponse<List<DeathCaseEntity>>> listCases() {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.listFacilityCases(), cid()));
    }

    /** Retrieve a single death case. */
    @GetMapping("/death/{caseId}")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> getCase(@PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.getCase(caseId), cid()));
    }

    /** Append-only audit / amendment trail for a death case. */
    @GetMapping("/death/{caseId}/audit")
    public ResponseEntity<ApiResponse<List<DeathAuditEntity>>> auditTrail(@PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.auditTrail(caseId), cid()));
    }

    /** Run the public-health / mortality-review screen. */
    @PostMapping("/death/{caseId}/public-health-screen")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> screenPublicHealth(
            @PathVariable UUID caseId, @RequestBody PublicHealthScreenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.screenPublicHealth(caseId, request), cid()));
    }

    /** Draft or sign the WHO cause-of-death certificate. */
    @PostMapping("/death/{caseId}/certify")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> certify(
            @PathVariable UUID caseId, @Valid @RequestBody CertifyCauseOfDeathRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.certifyCauseOfDeath(caseId, request), cid()));
    }

    /** Update the coroner referral status (owner-routed from the competent authority). */
    @PostMapping("/death/{caseId}/coroner-status")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> coronerStatus(
            @PathVariable UUID caseId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                deathWorkflow.updateCoronerStatus(caseId, body.get("status"), body.get("reason")), cid()));
    }

    /** Stage the civil-registration package for Ubomi (validates required fields). */
    @PostMapping("/death/{caseId}/crvs-package")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> stageCrvs(@PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.stageCrvsPackage(caseId), cid()));
    }

    // ── body / mortuary custody ──

    @GetMapping("/death/{caseId}/body")
    public ResponseEntity<ApiResponse<BodyCustodyEntity>> getCustody(@PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.getCustody(caseId), cid()));
    }

    @PostMapping("/death/{caseId}/body/receive")
    public ResponseEntity<ApiResponse<BodyCustodyEntity>> receiveBody(
            @PathVariable UUID caseId, @Valid @RequestBody BodyReceiveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.receiveBody(caseId, request), cid()));
    }

    @PostMapping("/death/{caseId}/body/postmortem-hold")
    public ResponseEntity<ApiResponse<BodyCustodyEntity>> postmortemHold(
            @PathVariable UUID caseId, @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                deathWorkflow.setPostmortemHold(caseId, Boolean.TRUE.equals(body.get("hold"))), cid()));
    }

    @PostMapping("/death/{caseId}/body/release")
    public ResponseEntity<ApiResponse<BodyCustodyEntity>> releaseBody(
            @PathVariable UUID caseId, @Valid @RequestBody BodyReleaseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deathWorkflow.releaseBody(caseId, request), cid()));
    }

    /** Link a supporting document reference (file stored by the document service) to the case. */
    @PostMapping("/death/{caseId}/documents")
    public ResponseEntity<ApiResponse<DeathCaseEntity>> attachDocument(
            @PathVariable UUID caseId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                deathWorkflow.attachSupportingDocument(caseId, body.get("documentRef"), body.get("documentType")), cid()));
    }
}
