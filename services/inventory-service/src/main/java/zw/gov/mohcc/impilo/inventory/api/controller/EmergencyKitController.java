package zw.gov.mohcc.impilo.inventory.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.core.EmergencyKitService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Emergency kit / resus trolley registry (W10). */
@RestController
@RequestMapping("/v1/emergency-kits")
public class EmergencyKitController {

    private final EmergencyKitService service;

    public EmergencyKitController(EmergencyKitService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        var kit = service.registerKit(ctx.tenantId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(kit, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(@RequestParam UUID facilityId) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.listForFacility(ctx.tenantId(), facilityId), ctx.correlationId().toString()));
    }

    @GetMapping("/{kitId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID kitId) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.get(ctx.tenantId(), kitId), ctx.correlationId().toString()));
    }

    @PostMapping("/{kitId}/checks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordCheck(@PathVariable UUID kitId,
                                                                          @RequestBody Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        var check = service.recordCheck(ctx.tenantId(), kitId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(check, ctx.correlationId().toString()));
    }

    @GetMapping("/{kitId}/checks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> checkHistory(@PathVariable UUID kitId) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.checkHistory(ctx.tenantId(), kitId), ctx.correlationId().toString()));
    }
}
