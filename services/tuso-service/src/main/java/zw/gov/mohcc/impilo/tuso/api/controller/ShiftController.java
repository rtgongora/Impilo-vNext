package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.ShiftResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.StartShiftOptionsResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.StartShiftRequest;
import zw.gov.mohcc.impilo.tuso.core.WorkspaceService;

import java.util.Set;

/**
 * Internal REST API for shift management within facilities.
 *
 * A shift represents a provider's active working session within a specific
 * workspace. Before starting a shift, the provider can query eligible
 * workspaces based on their role and cadre.
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityId}")
public class ShiftController {

    private static final Logger log = LoggerFactory.getLogger(ShiftController.class);

    private final WorkspaceService workspaceService;

    public ShiftController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/start-shift/options")
    public ResponseEntity<ApiResponse<StartShiftOptionsResponse>> getStartShiftOptions(
            @PathVariable Long facilityId,
            @RequestParam(required = false) Set<String> roles,
            @RequestParam(required = false) Set<String> privileges) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching start-shift options [facilityId={}, actorType={}] correlationId={}",
                facilityId, ctx.actorType(), ctx.correlationId());

        var providerInfo = new WorkspaceService.ProviderEligibilityInfo(
                ctx.actorType(),
                roles != null ? roles : Set.of(),
                privileges != null ? privileges : Set.of());

        var response = workspaceService.getStartShiftOptions(facilityId, providerInfo);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/start-shift")
    public ResponseEntity<ApiResponse<ShiftResponse>> startShift(
            @PathVariable Long facilityId,
            @Valid @RequestBody StartShiftRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Starting shift [facilityId={}, workspaceId={}, providerId={}, providerType={}] correlationId={}",
                facilityId, request.workspaceId(), request.providerId(),
                request.providerType(), ctx.correlationId());

        var response = workspaceService.startShift(
                facilityId, request.workspaceId(), request.providerId(), request.providerType());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }
}
