package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilitySetupDto;
import zw.gov.mohcc.impilo.tuso.core.FacilitySetupService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySetupStateEntity;

/**
 * Facility-mode setup-wizard API (TUSO SoR). Drives the
 * dept->service-point->queue->workflow->workforce->OROS-routing->Khuluma-channel->
 * Fundo-readiness->go-live progression. Authz via TSHEPO ext_authz; policy
 * {@code FACILITY-SETUP} (spec-only, track P). Go-live fails-loud if prerequisites
 * are incomplete (no fake completions).
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityId}/setup")
public class FacilitySetupController {

    private static final Logger log = LoggerFactory.getLogger(FacilitySetupController.class);

    private final FacilitySetupService setupService;

    public FacilitySetupController(FacilitySetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FacilitySetupDto.StateResponse>> getState(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching setup state [facilityId={}] correlationId={}", facilityId, ctx.correlationId());
        FacilitySetupStateEntity state = setupService.getOrCreateState(ctx, facilityId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(state), ctx.correlationId().toString()));
    }

    @PostMapping("/steps")
    public ResponseEntity<ApiResponse<FacilitySetupDto.StateResponse>> updateStep(
            @PathVariable Long facilityId,
            @Valid @RequestBody FacilitySetupDto.StepUpdateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating setup step [facilityId={}, step={}, complete={}] correlationId={}",
                facilityId, request.step(), request.complete(), ctx.correlationId());

        FacilitySetupService.SetupStep step;
        try {
            step = FacilitySetupService.SetupStep.valueOf(request.step());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "INVALID_SETUP_STEP", "Unknown setup step: " + request.step(),
                    HttpStatus.BAD_REQUEST.value(), ctx.correlationId().toString()));
        }

        try {
            FacilitySetupStateEntity state = setupService.advanceStep(ctx, facilityId, step, request.complete());
            return ResponseEntity.ok(ApiResponse.ok(toResponse(state), ctx.correlationId().toString()));
        } catch (IllegalStateException e) {
            // go-live blocked because prerequisites incomplete — honest fail, not a fake go-live
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                    "SETUP_PREREQUISITES_INCOMPLETE", e.getMessage(),
                    HttpStatus.CONFLICT.value(), ctx.correlationId().toString()));
        }
    }

    private FacilitySetupDto.StateResponse toResponse(FacilitySetupStateEntity s) {
        FacilitySetupService.SetupStep next = setupService.nextStep(s);
        return new FacilitySetupDto.StateResponse(
                s.getFacilityId(),
                s.isDepartmentsConfigured(),
                s.isServicePointsConfigured(),
                s.isQueuesConfigured(),
                s.isWorkflowsConfigured(),
                s.isWorkforceLinked(),
                s.isOrosRoutingConfigured(),
                s.isKhulumaChannelsConfigured(),
                s.isFundoReady(),
                s.isGoLive(),
                next != null ? next.name() : null,
                setupService.allPrerequisitesMet(s));
    }
}
