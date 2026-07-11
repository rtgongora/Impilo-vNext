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
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityUnitDto;
import zw.gov.mohcc.impilo.tuso.core.FacilityUnitService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitEntity;

import java.util.List;

/**
 * Facility unit (department) API (TUSO SoR). Exposes the existing
 * {@code FacilityUnitEntity} for the facility-mode setup wizard and admin UI.
 * Authz via TSHEPO ext_authz; policy {@code FACILITY-SETUP} (spec-only, track P).
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityId}/units")
public class FacilityUnitController {

    private static final Logger log = LoggerFactory.getLogger(FacilityUnitController.class);

    private final FacilityUnitService unitService;

    public FacilityUnitController(FacilityUnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FacilityUnitDto.Response>>> list(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing facility units [facilityId={}] correlationId={}", facilityId, ctx.correlationId());
        List<FacilityUnitDto.Response> out = unitService.list(facilityId).stream()
                .map(FacilityUnitController::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FacilityUnitDto.Response>> create(
            @PathVariable Long facilityId,
            @Valid @RequestBody FacilityUnitDto.CreateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating facility unit [facilityId={}, name={}] correlationId={}",
                facilityId, request.name(), ctx.correlationId());
        FacilityUnitEntity entity = unitService.create(ctx, facilityId,
                new FacilityUnitService.CreateUnitRequest(
                        request.name(), request.unitType(), request.serviceLine(),
                        request.registrationRequired(), request.metadata()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    @PatchMapping("/{unitId}")
    public ResponseEntity<ApiResponse<FacilityUnitDto.Response>> update(
            @PathVariable Long facilityId,
            @PathVariable Long unitId,
            @RequestBody FacilityUnitDto.UpdateRequest request) {
        return doUpdate(facilityId, unitId, request);
    }

    /** POST alias for partial update — for composition layers whose HTTP clients can't PATCH. */
    @PostMapping("/{unitId}")
    public ResponseEntity<ApiResponse<FacilityUnitDto.Response>> updatePost(
            @PathVariable Long facilityId,
            @PathVariable Long unitId,
            @RequestBody FacilityUnitDto.UpdateRequest request) {
        return doUpdate(facilityId, unitId, request);
    }

    @PostMapping("/{unitId}/retire")
    public ResponseEntity<ApiResponse<FacilityUnitDto.Response>> retire(
            @PathVariable Long facilityId,
            @PathVariable Long unitId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Retiring facility unit [facilityId={}, unitId={}] correlationId={}",
                facilityId, unitId, ctx.correlationId());
        FacilityUnitEntity entity = unitService.retire(ctx, facilityId, unitId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    private ResponseEntity<ApiResponse<FacilityUnitDto.Response>> doUpdate(
            Long facilityId, Long unitId, FacilityUnitDto.UpdateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating facility unit [facilityId={}, unitId={}] correlationId={}",
                facilityId, unitId, ctx.correlationId());
        FacilityUnitEntity entity = unitService.update(ctx, facilityId, unitId,
                new FacilityUnitService.UpdateUnitRequest(
                        request.name(), request.unitType(), request.serviceLine(), request.metadata()));
        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    private static FacilityUnitDto.Response toResponse(FacilityUnitEntity e) {
        return new FacilityUnitDto.Response(
                e.getId(), e.getName(), e.getUnitType(), e.getServiceLine(),
                e.isRegistrationRequired(),
                e.getRegulatoryStatus() != null ? e.getRegulatoryStatus().name() : null,
                e.getCertificateStatus());
    }
}
