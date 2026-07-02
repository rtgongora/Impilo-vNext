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
import zw.gov.mohcc.impilo.tuso.api.dto.ServicePointDto;
import zw.gov.mohcc.impilo.tuso.core.ServicePointService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;

import java.util.List;
import java.util.UUID;

/**
 * Facility service-point configuration API (TUSO SoR). Used by the facility-mode
 * setup wizard and facility-admin UI. Authz via TSHEPO ext_authz; policy
 * {@code FACILITY-SETUP} (spec-only, track P).
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityId}/service-points")
public class ServicePointController {

    private static final Logger log = LoggerFactory.getLogger(ServicePointController.class);

    private final ServicePointService servicePointService;

    public ServicePointController(ServicePointService servicePointService) {
        this.servicePointService = servicePointService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServicePointDto.Response>>> list(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing service points [facilityId={}] correlationId={}", facilityId, ctx.correlationId());
        List<ServicePointDto.Response> out = servicePointService.list(facilityId).stream()
                .map(ServicePointController::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServicePointDto.Response>> create(
            @PathVariable Long facilityId,
            @Valid @RequestBody ServicePointDto.CreateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating service point [facilityId={}, name={}] correlationId={}",
                facilityId, request.name(), ctx.correlationId());
        ServicePointEntity entity = servicePointService.create(ctx, facilityId,
                new ServicePointService.CreateServicePointRequest(
                        request.name(), request.code(), request.servicePointType(),
                        request.facilityUnitId(), request.queueId(),
                        request.workflowArchetype(), request.metadata()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    @PatchMapping("/{servicePointId}")
    public ResponseEntity<ApiResponse<ServicePointDto.Response>> update(
            @PathVariable Long facilityId,
            @PathVariable UUID servicePointId,
            @RequestBody ServicePointDto.UpdateRequest request) {
        return doUpdate(facilityId, servicePointId, request);
    }

    /**
     * POST alias for partial update. Mirrors {@link #retirePost} so composition layers whose HTTP
     * client cannot issue PATCH (e.g. the BFF's {@code SimpleClientHttpRequestFactory}) can update a
     * service point over POST. Same partial-merge semantics as the PATCH route.
     */
    @PostMapping("/{servicePointId}/update")
    public ResponseEntity<ApiResponse<ServicePointDto.Response>> updatePost(
            @PathVariable Long facilityId,
            @PathVariable UUID servicePointId,
            @RequestBody ServicePointDto.UpdateRequest request) {
        return doUpdate(facilityId, servicePointId, request);
    }

    private ResponseEntity<ApiResponse<ServicePointDto.Response>> doUpdate(
            Long facilityId, UUID servicePointId, ServicePointDto.UpdateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating service point [facilityId={}, id={}] correlationId={}",
                facilityId, servicePointId, ctx.correlationId());
        ServicePointEntity entity = servicePointService.update(ctx, servicePointId,
                new ServicePointService.UpdateServicePointRequest(
                        request.name(), request.code(), request.servicePointType(),
                        request.facilityUnitId(), request.queueId(), request.workflowArchetype(),
                        request.status(), request.active(), request.metadata()));
        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    @DeleteMapping("/{servicePointId}")
    public ResponseEntity<ApiResponse<ServicePointDto.Response>> retire(
            @PathVariable Long facilityId,
            @PathVariable UUID servicePointId) {
        return doRetire(facilityId, servicePointId);
    }

    @PostMapping("/{servicePointId}/retire")
    public ResponseEntity<ApiResponse<ServicePointDto.Response>> retirePost(
            @PathVariable Long facilityId,
            @PathVariable UUID servicePointId) {
        return doRetire(facilityId, servicePointId);
    }

    private ResponseEntity<ApiResponse<ServicePointDto.Response>> doRetire(Long facilityId, UUID servicePointId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Retiring service point [facilityId={}, id={}] correlationId={}",
                facilityId, servicePointId, ctx.correlationId());
        ServicePointEntity entity = servicePointService.deactivate(ctx, servicePointId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    private static ServicePointDto.Response toResponse(ServicePointEntity e) {
        return new ServicePointDto.Response(
                e.getId().toString(), e.getFacilityId(), e.getFacilityUnitId(),
                e.getName(), e.getCode(), e.getServicePointType(), e.getQueueId(),
                e.getWorkflowArchetype(), e.getStatus(), e.isActive());
    }
}
