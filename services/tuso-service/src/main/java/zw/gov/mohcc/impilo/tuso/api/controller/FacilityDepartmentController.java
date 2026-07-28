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
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityDepartmentDto;
import zw.gov.mohcc.impilo.tuso.core.FacilityDepartmentService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityDepartmentEntity;

import java.util.List;
import java.util.UUID;

/**
 * Canonical facility department administration API (TUSO SoR). Consumed by
 * the facility-admin UI, Vashandi assignment validation and the
 * experience-bff work-context resolver (department-management contexts).
 * Authz via TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityId}/departments")
public class FacilityDepartmentController {

    private static final Logger log = LoggerFactory.getLogger(FacilityDepartmentController.class);

    private final FacilityDepartmentService departmentService;

    public FacilityDepartmentController(FacilityDepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FacilityDepartmentDto.Response>>> list(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing facility departments [facilityId={}] correlationId={}", facilityId, ctx.correlationId());
        List<FacilityDepartmentDto.Response> out = departmentService.list(facilityId).stream()
                .map(FacilityDepartmentController::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @GetMapping("/{departmentId}")
    public ResponseEntity<ApiResponse<FacilityDepartmentDto.Response>> get(
            @PathVariable Long facilityId, @PathVariable UUID departmentId) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityDepartmentEntity entity = departmentService.get(departmentId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FacilityDepartmentDto.Response>> create(
            @PathVariable Long facilityId,
            @Valid @RequestBody FacilityDepartmentDto.CreateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating facility department [facilityId={}, code={}] correlationId={}",
                facilityId, request.departmentCode(), ctx.correlationId());
        FacilityDepartmentEntity entity = departmentService.create(ctx, facilityId,
                new FacilityDepartmentService.CreateDepartmentRequest(
                        request.departmentCode(), request.officialName(), request.displayName(),
                        request.departmentType(), request.parentDepartmentId(), request.openedAt(),
                        request.headPersonHealthId(), request.locationRef(),
                        request.contact(), request.escalation(), request.metadata()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    @PatchMapping("/{departmentId}")
    public ResponseEntity<ApiResponse<FacilityDepartmentDto.Response>> update(
            @PathVariable Long facilityId,
            @PathVariable UUID departmentId,
            @RequestBody FacilityDepartmentDto.UpdateRequest request) {
        return doUpdate(facilityId, departmentId, request);
    }

    /**
     * POST alias for partial update, mirroring the service-point controller's
     * pattern for composition layers whose HTTP client cannot issue PATCH.
     */
    @PostMapping("/{departmentId}/update")
    public ResponseEntity<ApiResponse<FacilityDepartmentDto.Response>> updatePost(
            @PathVariable Long facilityId,
            @PathVariable UUID departmentId,
            @RequestBody FacilityDepartmentDto.UpdateRequest request) {
        return doUpdate(facilityId, departmentId, request);
    }

    private ResponseEntity<ApiResponse<FacilityDepartmentDto.Response>> doUpdate(
            Long facilityId, UUID departmentId, FacilityDepartmentDto.UpdateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating facility department [facilityId={}, id={}] correlationId={}",
                facilityId, departmentId, ctx.correlationId());
        FacilityDepartmentEntity entity = departmentService.update(ctx, departmentId,
                new FacilityDepartmentService.UpdateDepartmentRequest(
                        request.officialName(), request.displayName(), request.departmentType(),
                        request.parentDepartmentId(), request.operatingStatus(), request.headPersonHealthId(),
                        request.locationRef(), request.contact(), request.escalation(), request.metadata()));
        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    @PostMapping("/{departmentId}/close")
    public ResponseEntity<ApiResponse<FacilityDepartmentDto.Response>> close(
            @PathVariable Long facilityId, @PathVariable UUID departmentId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Closing facility department [facilityId={}, id={}] correlationId={}",
                facilityId, departmentId, ctx.correlationId());
        FacilityDepartmentEntity entity = departmentService.close(ctx, departmentId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    @PostMapping("/{departmentId}/reactivate")
    public ResponseEntity<ApiResponse<FacilityDepartmentDto.Response>> reactivate(
            @PathVariable Long facilityId, @PathVariable UUID departmentId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Reactivating facility department [facilityId={}, id={}] correlationId={}",
                facilityId, departmentId, ctx.correlationId());
        FacilityDepartmentEntity entity = departmentService.reactivate(ctx, departmentId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    private static FacilityDepartmentDto.Response toResponse(FacilityDepartmentEntity e) {
        return new FacilityDepartmentDto.Response(
                e.getId().toString(), e.getFacilityId(), e.getDepartmentCode(), e.getOfficialName(),
                e.getDisplayName(), e.getDepartmentType(),
                e.getParentDepartmentId() != null ? e.getParentDepartmentId().toString() : null,
                e.getOperatingStatus(), e.getOpenedAt(), e.getClosedAt(),
                e.getHeadPersonHealthId() != null ? e.getHeadPersonHealthId().toString() : null,
                e.getLocationRef(), e.getContact(), e.getEscalation(), e.getProvenance(), e.getVersion(),
                e.isActive());
    }
}
