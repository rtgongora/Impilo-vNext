package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.ClinicalSpaceService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clinical-space registry API (TUSO SoR). Consumed by scheduling conflict
 * detection and inpatient readiness evaluation.
 */
@RestController
@RequestMapping("/v1/internal/tuso/clinical-spaces")
public class ClinicalSpaceController {

    private final ClinicalSpaceService service;

    public ClinicalSpaceController(ClinicalSpaceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false, name = "type") String type) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.list(ctx.tenantId(), type), ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.get(ctx.tenantId(), id), ctx.correlationId().toString()));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<Map<String, Object>>> availability(
            @PathVariable UUID id, @RequestParam(required = false) String date) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                service.availability(ctx.tenantId(), id, date), ctx.correlationId().toString()));
    }
}
