package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.FacilityEmergencyCapabilityService;

import java.util.Map;

/** Hand-declared emergency capability per facility (V200, W10) — for the emergency command view. */
@RestController
@RequestMapping("/v1/internal/tuso/facilities/{facilityId}/emergency-capability")
public class FacilityEmergencyCapabilityController {

    private final FacilityEmergencyCapabilityService service;

    public FacilityEmergencyCapabilityController(FacilityEmergencyCapabilityService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.get(ctx.tenantId(), facilityId), ctx.correlationId().toString()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> declare(@PathVariable Long facilityId,
                                                                      @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var result = service.declare(ctx.tenantId(), facilityId, body, ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(result, ctx.correlationId().toString()));
    }
}
