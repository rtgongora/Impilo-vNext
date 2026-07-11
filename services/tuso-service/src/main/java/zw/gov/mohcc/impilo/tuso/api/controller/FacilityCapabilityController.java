package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityResponse;
import zw.gov.mohcc.impilo.tuso.core.FacilityCapabilityReadinessService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCapabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityReadinessEntity;

import java.util.List;

/**
 * Curated capability ("services offered") + readiness writes for a facility.
 * Registry truth curation for the configuration console and setup wizard.
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityId}")
public class FacilityCapabilityController {

    private static final Logger log = LoggerFactory.getLogger(FacilityCapabilityController.class);

    private final FacilityCapabilityReadinessService service;

    public FacilityCapabilityController(FacilityCapabilityReadinessService service) {
        this.service = service;
    }

    @GetMapping("/capabilities")
    public ResponseEntity<ApiResponse<List<FacilityResponse.CapabilityDetail>>> listCapabilities(
            @PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        List<FacilityResponse.CapabilityDetail> out = service.listCapabilities(ctx, facilityId).stream()
                .map(FacilityCapabilityController::toDetail).toList();
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @PostMapping("/capabilities")
    public ResponseEntity<ApiResponse<FacilityResponse.CapabilityDetail>> createCapability(
            @PathVariable Long facilityId,
            @RequestBody FacilityCapabilityReadinessService.CapabilityRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating capability [facilityId={}, code={}] correlationId={}",
                facilityId, request.capabilityCode(), ctx.correlationId());
        FacilityCapabilityEntity entity = service.createCapability(ctx, facilityId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toDetail(entity), ctx.correlationId().toString()));
    }

    @PatchMapping("/capabilities/{capabilityId}")
    public ResponseEntity<ApiResponse<FacilityResponse.CapabilityDetail>> updateCapability(
            @PathVariable Long facilityId,
            @PathVariable Long capabilityId,
            @RequestBody FacilityCapabilityReadinessService.CapabilityRequest request) {
        return doUpdateCapability(facilityId, capabilityId, request);
    }

    /** POST alias for partial update — for composition layers whose HTTP clients can't PATCH. */
    @PostMapping("/capabilities/{capabilityId}")
    public ResponseEntity<ApiResponse<FacilityResponse.CapabilityDetail>> updateCapabilityPost(
            @PathVariable Long facilityId,
            @PathVariable Long capabilityId,
            @RequestBody FacilityCapabilityReadinessService.CapabilityRequest request) {
        return doUpdateCapability(facilityId, capabilityId, request);
    }

    @PostMapping("/capabilities/{capabilityId}/retire")
    public ResponseEntity<ApiResponse<FacilityResponse.CapabilityDetail>> retireCapability(
            @PathVariable Long facilityId,
            @PathVariable Long capabilityId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Retiring capability [facilityId={}, capabilityId={}] correlationId={}",
                facilityId, capabilityId, ctx.correlationId());
        FacilityCapabilityEntity entity = service.retireCapability(ctx, facilityId, capabilityId);
        return ResponseEntity.ok(ApiResponse.ok(toDetail(entity), ctx.correlationId().toString()));
    }

    @GetMapping("/readiness")
    public ResponseEntity<ApiResponse<FacilityResponse.ReadinessDetail>> getReadiness(
            @PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityReadinessEntity entity = service.getReadiness(ctx, facilityId);
        return ResponseEntity.ok(ApiResponse.ok(
                entity != null ? toReadiness(entity) : null, ctx.correlationId().toString()));
    }

    @PutMapping("/readiness")
    public ResponseEntity<ApiResponse<FacilityResponse.ReadinessDetail>> upsertReadiness(
            @PathVariable Long facilityId,
            @RequestBody FacilityCapabilityReadinessService.ReadinessRequest request) {
        return doUpsertReadiness(facilityId, request);
    }

    /** POST alias for upsert — for composition layers whose HTTP clients can't PUT. */
    @PostMapping("/readiness")
    public ResponseEntity<ApiResponse<FacilityResponse.ReadinessDetail>> upsertReadinessPost(
            @PathVariable Long facilityId,
            @RequestBody FacilityCapabilityReadinessService.ReadinessRequest request) {
        return doUpsertReadiness(facilityId, request);
    }

    private ResponseEntity<ApiResponse<FacilityResponse.CapabilityDetail>> doUpdateCapability(
            Long facilityId, Long capabilityId, FacilityCapabilityReadinessService.CapabilityRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating capability [facilityId={}, capabilityId={}] correlationId={}",
                facilityId, capabilityId, ctx.correlationId());
        FacilityCapabilityEntity entity = service.updateCapability(ctx, facilityId, capabilityId, request);
        return ResponseEntity.ok(ApiResponse.ok(toDetail(entity), ctx.correlationId().toString()));
    }

    private ResponseEntity<ApiResponse<FacilityResponse.ReadinessDetail>> doUpsertReadiness(
            Long facilityId, FacilityCapabilityReadinessService.ReadinessRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Upserting readiness [facilityId={}] correlationId={}", facilityId, ctx.correlationId());
        FacilityReadinessEntity entity = service.upsertReadiness(ctx, facilityId, request);
        return ResponseEntity.ok(ApiResponse.ok(toReadiness(entity), ctx.correlationId().toString()));
    }

    private static FacilityResponse.CapabilityDetail toDetail(FacilityCapabilityEntity c) {
        return new FacilityResponse.CapabilityDetail(
                c.getId(), c.getCapabilityCode(), c.getCapabilityType(), c.getName(),
                c.getZiboValidated() != null && c.getZiboValidated(),
                c.getActive() != null && c.getActive(),
                c.getOperatingHours(), c.getMetadata());
    }

    private static FacilityResponse.ReadinessDetail toReadiness(FacilityReadinessEntity r) {
        return new FacilityResponse.ReadinessDetail(
                r.getConnectivity(), r.getPowerSource(),
                r.getPowerBackup() != null && r.getPowerBackup(),
                r.getDeviceCount() != null ? r.getDeviceCount() : 0,
                r.getEhrReady() != null && r.getEhrReady(),
                r.getComplianceFlags(), r.getAssessedAt(), r.getAssessedBy());
    }
}
