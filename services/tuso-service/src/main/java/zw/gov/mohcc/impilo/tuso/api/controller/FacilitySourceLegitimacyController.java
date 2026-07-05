package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilitySourceLegitimacyDtos;
import zw.gov.mohcc.impilo.tuso.core.FacilitySourceLegitimacyService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;

import java.util.UUID;

/**
 * Honest per-source facility legitimacy (WS-E). {@code facilityId} in every route is the
 * <b>canonical facility UUID</b> ({@code tuso.facility.facility_uuid}), never the numeric
 * surrogate id and never a facility code (codes are matching handles, not identity).
 */
@RestController
public class FacilitySourceLegitimacyController {

    private final FacilitySourceLegitimacyService legitimacyService;

    public FacilitySourceLegitimacyController(FacilitySourceLegitimacyService legitimacyService) {
        this.legitimacyService = legitimacyService;
    }

    /** All current per-source legitimacy verdicts for a facility. */
    @GetMapping("/v1/facilities/{facilityId}/source-legitimacy")
    public ResponseEntity<ApiResponse<FacilitySourceLegitimacyDtos.SourceLegitimacyListResponse>> getSourceLegitimacy(
            @PathVariable UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                legitimacyService.getSourceLegitimacy(facilityId), ctx.correlationId().toString()));
    }

    /**
     * Upsert one source's verdict (internal/governance plane). One current row per
     * (facility, source); overwrites carry previous values on the emitted outbox event.
     */
    @PutMapping("/v1/internal/facilities/{facilityId}/source-legitimacy/{source}")
    public ResponseEntity<ApiResponse<FacilitySourceLegitimacyDtos.SourceLegitimacyView>> upsertSourceLegitimacy(
            @PathVariable UUID facilityId,
            @PathVariable FacilityLegitimacySource source,
            @Valid @RequestBody FacilitySourceLegitimacyDtos.UpsertSourceLegitimacyRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                legitimacyService.upsert(facilityId, source, request), ctx.correlationId().toString()));
    }

    /** Composite: regulatory status + certificates + per-source verdicts + platform-access verdict. */
    @GetMapping("/v1/facilities/{facilityId}/status-composite")
    public ResponseEntity<ApiResponse<FacilitySourceLegitimacyDtos.FacilityStatusCompositeResponse>> getStatusComposite(
            @PathVariable UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                legitimacyService.getComposite(facilityId), ctx.correlationId().toString()));
    }
}
