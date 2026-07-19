package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.core.FacilityTrustDimensionService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityTrustDimensionEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facility trust dimensions (Place Journey Doctrine §4, D-L8) — steward/console
 * surface, internal plane. The dimension view is a GOVERNANCE read-model (Trust
 * Console, steward review); it is never part of the public projection and never
 * disclosed on the claim lane.
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityUuid}/trust-dimensions")
public class FacilityTrustDimensionController {

    private final FacilityTrustDimensionService trustDimensionService;

    public FacilityTrustDimensionController(FacilityTrustDimensionService trustDimensionService) {
        this.trustDimensionService = trustDimensionService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> view(@PathVariable("facilityUuid") UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(Map.of("dimensions",
                trustDimensionService.view(ctx.tenantId(), facilityUuid).stream()
                        .map(FacilityTrustDimensionController::toView).toList()));
    }

    @PostMapping("/recompute")
    public ResponseEntity<Map<String, Object>> recompute(@PathVariable("facilityUuid") UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        List<FacilityTrustDimensionEntity> rows =
                trustDimensionService.recompute(ctx.tenantId(), facilityUuid);
        return ResponseEntity.ok(Map.of("dimensions",
                rows.stream().map(FacilityTrustDimensionController::toView).toList()));
    }

    private static Map<String, Object> toView(FacilityTrustDimensionEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dimension", row.getDimension());
        m.put("status", row.getStatus());
        m.put("evidenceRef", row.getEvidenceRef());
        m.put("source", row.getSource());
        m.put("computedAt", row.getComputedAt());
        return m;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
