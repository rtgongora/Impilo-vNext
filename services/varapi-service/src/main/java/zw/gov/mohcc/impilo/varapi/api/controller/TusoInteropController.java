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

    public TusoInteropController(EligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
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
}