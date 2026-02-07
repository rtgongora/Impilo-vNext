package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.ConfigVersionResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.EffectiveConfigResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.RollbackConfigRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.UpdateFacilityConfigRequest;
import zw.gov.mohcc.impilo.tuso.core.ConfigService;

import java.util.List;
import java.util.UUID;

/**
 * Internal REST API for facility configuration management.
 *
 * Configuration follows a layered merge strategy:
 * tenant defaults -> facility overrides -> workspace overrides.
 * The effective config endpoint returns the fully merged result.
 * All changes are versioned and auditable with rollback support.
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityId}/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/effective")
    public ResponseEntity<ApiResponse<EffectiveConfigResponse>> getEffectiveConfig(
            @PathVariable Long facilityId,
            @RequestParam(required = false) UUID workspaceId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching effective config [facilityId={}, workspaceId={}] correlationId={}",
                facilityId, workspaceId, ctx.correlationId());

        EffectiveConfigResponse response = configService.getEffectiveConfig(ctx, facilityId, workspaceId);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ConfigVersionResponse>> updateConfig(
            @PathVariable Long facilityId,
            @Valid @RequestBody UpdateFacilityConfigRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating facility config [facilityId={}] correlationId={}", facilityId, ctx.correlationId());

        ConfigVersionResponse response = configService.updateConfig(ctx, facilityId, request);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ConfigVersionResponse>>> getConfigHistory(
            @PathVariable Long facilityId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching config history [facilityId={}] correlationId={}", facilityId, ctx.correlationId());

        List<ConfigVersionResponse> response = configService.getConfigHistory(ctx, facilityId);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/rollback")
    public ResponseEntity<ApiResponse<ConfigVersionResponse>> rollbackConfig(
            @PathVariable Long facilityId,
            @Valid @RequestBody RollbackConfigRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Rolling back config [facilityId={}, targetVersion={}] correlationId={}",
                facilityId, request.targetVersion(), ctx.correlationId());

        ConfigVersionResponse response = configService.rollbackConfig(ctx, facilityId, request);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }
}
