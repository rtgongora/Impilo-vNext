package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.ResourceAvailabilityService;

import java.util.List;
import java.util.Map;

/**
 * Theatre resource registry API (TUSO SoR): beds (capacity), equipment assets and
 * instrument sets. Consumed by inpatient theatre-day readiness evaluation.
 */
@RestController
@RequestMapping("/v1/internal/tuso")
public class TheatreResourceController {

    private final ResourceAvailabilityService service;

    public TheatreResourceController(ResourceAvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/beds")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> beds(
            @RequestParam(required = false, name = "class") String bedClass,
            @RequestParam(required = false) String status) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                service.beds(ctx.tenantId(), bedClass, status), ctx.correlationId().toString()));
    }

    @GetMapping("/equipment")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> equipment(
            @RequestParam(required = false) String state) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                service.equipment(ctx.tenantId(), state), ctx.correlationId().toString()));
    }

    @GetMapping("/instrument-sets")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> instrumentSets(
            @RequestParam(required = false) String state) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                service.instrumentSets(ctx.tenantId(), state), ctx.correlationId().toString()));
    }
}
