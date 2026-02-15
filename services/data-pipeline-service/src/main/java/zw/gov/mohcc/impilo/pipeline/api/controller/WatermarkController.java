package zw.gov.mohcc.impilo.pipeline.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.pipeline.api.dto.WatermarkResponse;
import zw.gov.mohcc.impilo.pipeline.core.WatermarkService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for querying pipeline watermarks.
 */
@RestController
@RequestMapping("/internal/v1")
public class WatermarkController {

    private final WatermarkService watermarkService;

    public WatermarkController(WatermarkService watermarkService) {
        this.watermarkService = watermarkService;
    }

    /**
     * Retrieve all watermarks for the tenant in the request context.
     *
     * @return list of watermark states per source
     */
    @GetMapping("/watermarks")
    public ResponseEntity<List<WatermarkResponse>> getWatermarks() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<WatermarkResponse> watermarks = watermarkService.getWatermarks(tenantId);
        return ResponseEntity.ok(watermarks);
    }
}
