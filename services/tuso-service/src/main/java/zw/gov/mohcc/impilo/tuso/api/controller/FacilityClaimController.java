package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityClaimDtos;
import zw.gov.mohcc.impilo.tuso.core.FacilityClaimService;

import java.util.List;
import java.util.UUID;

/**
 * Facility administrator claim/appointment lane (IATG Wave 2, WS-E) — internal service plane,
 * consumed by the experience-BFF facility-claim orchestration. {@code facilityId} in every route
 * is the <b>canonical facility UUID</b> ({@code tuso.facility.facility_uuid}), never the numeric
 * surrogate id and never a facility code.
 */
@RestController
public class FacilityClaimController {

    private final FacilityClaimService claimService;

    public FacilityClaimController(FacilityClaimService claimService) {
        this.claimService = claimService;
    }

    /** Is the facility claimable (allowed on platform) and does it already have administrators? */
    @GetMapping("/v1/internal/facilities/{facilityId}/admin-claim/eligibility")
    public ResponseEntity<ApiResponse<FacilityClaimDtos.EligibilityView>> eligibility(
            @PathVariable UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.checkEligibility(facilityId), ctx.correlationId().toString()));
    }

    /** Non-sensitive facility summary + claimability verdict (confirm-before-commit). */
    @GetMapping("/v1/internal/facilities/{facilityId}/admin-claim/preview")
    public ResponseEntity<ApiResponse<FacilityClaimDtos.PreviewView>> preview(
            @PathVariable UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.preview(facilityId), ctx.correlationId().toString()));
    }

    /** All appointments for a facility. */
    @GetMapping("/v1/internal/facilities/{facilityId}/admin-claim/appointments")
    public ResponseEntity<ApiResponse<List<FacilityClaimDtos.AppointmentView>>> appointments(
            @PathVariable UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.list(facilityId), ctx.correlationId().toString()));
    }

    /** Submit a facility-administrator claim → creates a PENDING appointment. */
    @PostMapping("/v1/internal/facilities/{facilityId}/admin-claim")
    public ResponseEntity<ApiResponse<FacilityClaimDtos.AppointmentView>> submitClaim(
            @PathVariable UUID facilityId,
            @Valid @RequestBody FacilityClaimDtos.SubmitClaimRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                claimService.submitClaim(facilityId, request), ctx.correlationId().toString()));
    }

    /**
     * Cross-facility review queue (IATG Trust Console): appointments in a given approval
     * state across every facility of the caller's tenant. Defaults to PENDING.
     */
    @GetMapping("/v1/internal/facility-admin-appointments")
    public ResponseEntity<ApiResponse<List<FacilityClaimDtos.AppointmentView>>> appointmentsByState(
            @RequestParam(name = "state", defaultValue = "PENDING") String state) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.listByState(state), ctx.correlationId().toString()));
    }

    /** Approve a PENDING appointment → ACTIVE. */
    @PostMapping("/v1/internal/facility-admin-appointments/{appointmentId}/approve")
    public ResponseEntity<ApiResponse<FacilityClaimDtos.AppointmentView>> approve(
            @PathVariable Long appointmentId,
            @RequestBody(required = false) FacilityClaimDtos.ApproveAppointmentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.approve(appointmentId, request), ctx.correlationId().toString()));
    }
}
