package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderEligibilityRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderEligibilityResponse;
import zw.gov.mohcc.impilo.varapi.core.EligibilityService;

import java.util.List;

/**
 * REST controller for TUSO interoperability.
 * Provides endpoints for provider eligibility verification that TUSO and other
 * services can call to check provider credentials before granting access.
 */
@RestController
@RequestMapping("/v1/internal/interop/eligibility")
public class TusoInteropController {

    private final EligibilityService eligibilityService;
    private final zw.gov.mohcc.impilo.varapi.core.PicEligibilityAssessmentService assessmentService;

    public TusoInteropController(EligibilityService eligibilityService,
                                 zw.gov.mohcc.impilo.varapi.core.PicEligibilityAssessmentService assessmentService) {
        this.eligibilityService = eligibilityService;
        this.assessmentService = assessmentService;
    }

    @PostMapping
    public ResponseEntity<ProviderEligibilityResponse> checkEligibility(
            @RequestBody ProviderEligibilityRequest request) {
        return ResponseEntity.ok(eligibilityService.checkEligibility(request));
    }

    @PostMapping("/provider/{providerId}/facility/{facilityId}")
    public ResponseEntity<ProviderEligibilityResponse> checkEligibilityForFacility(
            @PathVariable Long providerId,
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(eligibilityService.checkEligibilityForFacility(providerId, facilityId));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<ProviderEligibilityResponse> getEligibilitySummary(@PathVariable Long providerId) {
        return ResponseEntity.ok(eligibilityService.getEligibilitySummary(providerId));
    }

    @GetMapping("/provider/{providerId}/facility/{facilityId}/pic-eligible")
    public ResponseEntity<ProviderEligibilityResponse> checkPicEligibility(
            @PathVariable Long providerId,
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(eligibilityService.checkPicEligibility(providerId, facilityId));
    }

    @GetMapping("/facility/{facilityId}/eligible-providers")
    public ResponseEntity<List<ProviderEligibilityResponse>> getEligibleProvidersForFacility(
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(eligibilityService.getEligibleProvidersForFacility(facilityId));
    }

    @PostMapping("/facility/{facilityId}/has-active-provider")
    public ResponseEntity<Boolean> facilityHasActiveProvider(
            @PathVariable Long facilityId,
            @RequestBody(required = false) Long excludeProviderId) {
        boolean hasActive = eligibilityService.facilityHasActiveProvider(facilityId, excludeProviderId);
        return ResponseEntity.ok(hasActive);
    }

    /**
     * Time-specific PIC eligibility assessment: unified per-axis credential
     * truth (identity, council, registration, practising certificate, licence,
     * restrictions) with source evidence, coded reasons and a persisted
     * snapshot. TUSO snapshots this verbatim on the nomination.
     */
    @PostMapping("/assessments")
    public ResponseEntity<zw.gov.mohcc.impilo.varapi.core.PicEligibilityAssessmentService.AssessmentResponse> assess(
            @RequestBody zw.gov.mohcc.impilo.varapi.core.PicEligibilityAssessmentService.AssessmentRequest request) {
        return ResponseEntity.ok(assessmentService.assess(request));
    }

    @GetMapping("/assessments/{snapshotId}")
    public ResponseEntity<zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEligibilitySnapshotEntity> snapshot(
            @PathVariable java.util.UUID snapshotId) {
        return ResponseEntity.ok(assessmentService.getSnapshot(snapshotId));
    }
}