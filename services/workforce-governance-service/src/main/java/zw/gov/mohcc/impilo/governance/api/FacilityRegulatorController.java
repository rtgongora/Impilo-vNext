package zw.gov.mohcc.impilo.governance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.governance.core.FacilityRegulatorRelationshipService;
import zw.gov.mohcc.impilo.governance.persistence.FacilityRegulatorRelationshipEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-regulator facility&lt;-&gt;council relationship API (WGV SoR). A facility may be
 * regulated by MANY councils — no hardcoded single regulator. Authz via existing
 * ext_authz; policies {@code ORG-ADMIN}, {@code REGULATOR-MODE} (spec-only, track P).
 */
@RestController
@RequestMapping("/v1/internal/governance")
public class FacilityRegulatorController {

    private static final Logger log = LoggerFactory.getLogger(FacilityRegulatorController.class);

    private final FacilityRegulatorRelationshipService service;

    public FacilityRegulatorController(FacilityRegulatorRelationshipService service) {
        this.service = service;
    }

    public record LinkRegulatorRequest(
            @NotBlank String councilId,
            @NotBlank String councilCode,
            String relationshipType,
            String registrationNumber,
            String status,
            String validFrom,
            String validTo
    ) {}

    public record RelationshipResponse(
            String id, String facilityId, String councilId, String councilCode,
            String relationshipType, String registrationNumber, String status,
            String validFrom, String validTo
    ) {}

    @GetMapping("/facilities/{facilityId}/regulators")
    public ResponseEntity<ApiResponse<List<RelationshipResponse>>> listForFacility(
            @PathVariable String facilityId) {
        UUID tenantId = tenant();
        List<RelationshipResponse> out = service.listForFacility(tenantId, facilityId).stream()
                .map(FacilityRegulatorController::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok(out, corr()));
    }

    @GetMapping("/councils/{councilId}/facilities")
    public ResponseEntity<ApiResponse<List<RelationshipResponse>>> listForCouncil(
            @PathVariable String councilId) {
        UUID tenantId = tenant();
        List<RelationshipResponse> out = service.listForCouncil(tenantId, councilId).stream()
                .map(FacilityRegulatorController::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok(out, corr()));
    }

    @PostMapping("/facilities/{facilityId}/regulators")
    public ResponseEntity<ApiResponse<RelationshipResponse>> link(
            @PathVariable String facilityId,
            @Valid @RequestBody LinkRegulatorRequest req) {
        UUID tenantId = tenant();
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("councilId", req.councilId());
        map.put("councilCode", req.councilCode());
        map.put("relationshipType", req.relationshipType());
        map.put("registrationNumber", req.registrationNumber());
        map.put("status", req.status());
        map.put("validFrom", req.validFrom());
        map.put("validTo", req.validTo());
        FacilityRegulatorRelationshipEntity e = service.link(tenantId, actor(), facilityId, map, corr());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(e), corr()));
    }

    @PatchMapping("/facilities/regulators/{id}/status")
    public ResponseEntity<ApiResponse<RelationshipResponse>> updateStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        UUID tenantId = tenant();
        FacilityRegulatorRelationshipEntity e = service.updateStatus(
                tenantId, actor(), id, body.get("status"), corr());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(e), corr()));
    }

    @DeleteMapping("/facilities/regulators/{id}")
    public ResponseEntity<ApiResponse<Void>> unlink(@PathVariable UUID id) {
        UUID tenantId = tenant();
        service.unlink(tenantId, actor(), id, corr());
        return ResponseEntity.ok(ApiResponse.ok(null, corr()));
    }

    private static RelationshipResponse toResponse(FacilityRegulatorRelationshipEntity e) {
        return new RelationshipResponse(
                e.getId().toString(), e.getFacilityId(), e.getCouncilId(), e.getCouncilCode(),
                e.getRelationshipType(), e.getRegistrationNumber(), e.getStatus(),
                e.getValidFrom() != null ? e.getValidFrom().toString() : null,
                e.getValidTo() != null ? e.getValidTo().toString() : null);
    }

    private UUID tenant() {
        UUID t = TrustContextHolder.require().tenantId();
        if (t == null) throw new IllegalArgumentException("Missing X-Tenant-ID trust header");
        return t;
    }

    private String corr() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null ? ctx.correlationId().toString() : "";
    }

    private String actor() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.actorId() != null ? ctx.actorId() : "system";
    }
}
