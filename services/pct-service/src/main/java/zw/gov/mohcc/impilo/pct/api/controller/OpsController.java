package zw.gov.mohcc.impilo.pct.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.ControlTowerService;
import zw.gov.mohcc.impilo.pct.core.ControlTowerService.BottleneckAlert;
import zw.gov.mohcc.impilo.pct.core.ControlTowerService.ControlTowerSnapshot;
import zw.gov.mohcc.impilo.pct.core.TelemetryService;
import zw.gov.mohcc.impilo.pct.persistence.entity.TelemetryEventEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.TelemetryEventRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for operational monitoring endpoints.
 *
 * <p>Provides the control tower dashboard data, bottleneck detection,
 * and telemetry event access for facility operations managers. These
 * endpoints power the ops-console real-time dashboards.</p>
 */
@RestController
@RequestMapping("/v1/ops")
public class OpsController {

    private static final Logger log = LoggerFactory.getLogger(OpsController.class);

    private final ControlTowerService controlTowerService;
    private final TelemetryEventRepository telemetryEventRepository;

    public OpsController(ControlTowerService controlTowerService,
                         TelemetryEventRepository telemetryEventRepository) {
        this.controlTowerService = controlTowerService;
        this.telemetryEventRepository = telemetryEventRepository;
    }

    /**
     * Retrieve the full control tower snapshot for a facility.
     *
     * <p>Aggregates queue stats, admission occupancy, discharge blockers,
     * throughput, active journey counts, and bottleneck alerts into a
     * single response.</p>
     *
     * @param facilityId the facility to generate the control tower view for
     * @return the control tower snapshot
     */
    @GetMapping("/control-tower")
    public ResponseEntity<ApiResponse<ControlTowerSnapshot>> getControlTower(
            @RequestParam UUID facilityId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ControlTowerSnapshot snapshot = controlTowerService.getControlTowerData(facilityId);

        return ResponseEntity.ok(ApiResponse.ok(snapshot, correlationId));
    }

    /**
     * Retrieve detected bottlenecks for a facility.
     *
     * <p>Returns the bottleneck alerts extracted from the current control
     * tower snapshot. This is a convenience endpoint that avoids
     * transferring the full snapshot when only bottlenecks are needed.</p>
     *
     * @param facilityId the facility to check for bottlenecks
     * @return list of bottleneck alerts
     */
    @GetMapping("/bottlenecks")
    public ResponseEntity<ApiResponse<List<BottleneckAlert>>> getBottlenecks(
            @RequestParam UUID facilityId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ControlTowerSnapshot snapshot = controlTowerService.getControlTowerData(facilityId);

        return ResponseEntity.ok(ApiResponse.ok(snapshot.bottlenecks(), correlationId));
    }

    /**
     * Retrieve telemetry events for a facility since a given timestamp.
     *
     * <p>Returns operational telemetry events (shift starts/ends, queue
     * operations, routing decisions, etc.) in reverse chronological order.</p>
     *
     * @param facilityId the facility to query
     * @param since      the earliest timestamp to include (ISO 8601)
     * @return list of telemetry events
     */
    @GetMapping("/queues/telemetry")
    public ResponseEntity<ApiResponse<List<TelemetryEventEntity>>> getTelemetry(
            @RequestParam UUID facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        List<TelemetryEventEntity> events = telemetryEventRepository
                .findByTenantIdAndFacilityIdAndCreatedAtAfterOrderByCreatedAtDesc(
                        ctx.tenantId(), facilityId, since);

        return ResponseEntity.ok(ApiResponse.ok(events, correlationId));
    }
}
