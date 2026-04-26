package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiError;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.ZwGeoReferenceService;

import java.util.List;
import java.util.Map;

/**
 * COD-AB / ZIMSTAT-aligned Zimbabwe admin reference data (districts, wards).
 */
@RestController
@RequestMapping("/v1/internal/geo/zw")
public class ZwGeoReferenceController {

    private static final Logger log = LoggerFactory.getLogger(ZwGeoReferenceController.class);

    private final ZwGeoReferenceService geoService;

    public ZwGeoReferenceController(ZwGeoReferenceService geoService) {
        this.geoService = geoService;
    }

    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listDistricts(
            @RequestParam("provinceCode") String provinceCode) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("ZW geo: list districts provinceCode={} correlationId={}", provinceCode, ctx.correlationId());
        List<Map<String, Object>> rows = geoService.listDistricts(provinceCode);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @GetMapping("/wards")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listWards(
            @RequestParam("districtCode") String districtCode) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("ZW geo: list wards districtCode={} correlationId={}", districtCode, ctx.correlationId());
        List<Map<String, Object>> rows = geoService.listWards(districtCode);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkUpsert(
            @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> districts = (List<Map<String, Object>>) body.get("districts");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wards = (List<Map<String, Object>>) body.get("wards");
        try {
            Map<String, Object> summary = geoService.bulkUpsert(districts, wards);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.ok(summary, ctx.correlationId().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            new ApiError(ApiError.VALIDATION_FAILED, e.getMessage(), 400),
                            ctx.correlationId().toString()));
        }
    }
}
