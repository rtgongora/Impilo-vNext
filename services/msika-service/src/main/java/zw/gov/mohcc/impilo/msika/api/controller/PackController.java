package zw.gov.mohcc.impilo.msika.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.core.PackService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/v1/packs")
public class PackController {

    private final PackService packService;

    public PackController(PackService packService) {
        this.packService = packService;
    }

    @GetMapping("/orderables")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderablesPack(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String version,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return buildPackResponse(packService.getOrderablesPack(tenantId, version), ifNoneMatch);
    }

    @GetMapping("/item-master")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getItemMasterPack(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String version,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return buildPackResponse(packService.getItemMasterPack(tenantId, version), ifNoneMatch);
    }

    @GetMapping("/chargeables")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChargeablesPack(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String version,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return buildPackResponse(packService.getChargeablesPack(tenantId, version), ifNoneMatch);
    }

    @GetMapping("/capabilities/facility")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFacilityCapabilitiesPack(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String version,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return buildPackResponse(packService.getFacilityCapabilitiesPack(tenantId, version), ifNoneMatch);
    }

    @GetMapping("/capabilities/provider")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProviderCapabilitiesPack(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String version,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return buildPackResponse(packService.getProviderCapabilitiesPack(tenantId, version), ifNoneMatch);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ApiResponse<Map<String, Object>>> buildPackResponse(
            Map<String, Object> pack, String ifNoneMatch) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        String checksum = (String) pack.get("checksum");
        String etag = "\"" + checksum + "\"";

        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(304).build();
        }

        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.MINUTES).mustRevalidate())
                .body(ApiResponse.ok(pack, correlationId));
    }
}
