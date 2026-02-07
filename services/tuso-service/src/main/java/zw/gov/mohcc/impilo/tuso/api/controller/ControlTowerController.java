package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.AlertResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilitySummaryResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.OrosTelemetryRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.PctTelemetryRequest;
import zw.gov.mohcc.impilo.tuso.core.ControlTowerService;

import java.util.List;

/**
 * Internal REST API for the Control Tower subsystem.
 *
 * The Control Tower aggregates telemetry from PCT (patient care tracking)
 * and OROS (operations/resource orchestration) services, maintains
 * occupancy snapshots, generates alerts on threshold breaches, and
 * provides facility-level operational summaries for district/provincial
 * dashboards.
 */
@RestController
@RequestMapping("/v1/internal")
public class ControlTowerController {

    private static final Logger log = LoggerFactory.getLogger(ControlTowerController.class);

    private final ControlTowerService controlTowerService;

    public ControlTowerController(ControlTowerService controlTowerService) {
        this.controlTowerService = controlTowerService;
    }

    @PostMapping("/telemetry/pct")
    public ResponseEntity<ApiResponse<Void>> ingestPctTelemetry(
            @Valid @RequestBody PctTelemetryRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Ingesting PCT telemetry [facilityId={}, metricCount={}, hasBedSnapshot={}] correlationId={}",
                request.facilityId(), request.metrics().size(),
                request.bedSnapshot() != null, ctx.correlationId());

        controlTowerService.ingestPctTelemetry(ctx, request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(null, ctx.correlationId().toString()));
    }

    @PostMapping("/telemetry/oros")
    public ResponseEntity<ApiResponse<Void>> ingestOrosTelemetry(
            @Valid @RequestBody OrosTelemetryRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Ingesting OROS telemetry [facilityId={}, metricCount={}] correlationId={}",
                request.facilityId(), request.metrics().size(), ctx.correlationId());

        controlTowerService.ingestOrosTelemetry(ctx, request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(null, ctx.correlationId().toString()));
    }

    @GetMapping("/control-tower/facilities/{facilityId}/summary")
    public ResponseEntity<ApiResponse<FacilitySummaryResponse>> getFacilitySummary(
            @PathVariable Long facilityId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching facility summary [facilityId={}] correlationId={}", facilityId, ctx.correlationId());

        FacilitySummaryResponse response = controlTowerService.getFacilitySummary(ctx, facilityId);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/control-tower/alerts")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getAlerts(
            @RequestParam(required = false) Long facilityId,
            @RequestParam(required = false) String status) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching alerts [facilityId={}, status={}] correlationId={}",
                facilityId, status, ctx.correlationId());

        List<AlertResponse> response = controlTowerService.getAlerts(ctx, facilityId, status);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }
}
