package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.api.dto.AlertResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.BedSnapshotDto;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilitySummaryResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.MetricEntry;
import zw.gov.mohcc.impilo.tuso.api.dto.OrosTelemetryRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.PctTelemetryRequest;
import zw.gov.mohcc.impilo.tuso.persistence.entity.AlertEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.OccupancySnapshotEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.TelemetryEventEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.AlertRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.OccupancySnapshotRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.TelemetryEventRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Control Tower service: aggregates telemetry from PCT and OROS,
 * maintains occupancy snapshots, evaluates alert rules, and provides
 * facility-level operational summaries.
 */
@Service
public class ControlTowerService {

    private static final Logger log = LoggerFactory.getLogger(ControlTowerService.class);

    private final TelemetryEventRepository telemetryRepository;
    private final OccupancySnapshotRepository occupancyRepository;
    private final AlertRepository alertRepository;
    private final FacilityRepository facilityRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ShiftRepository shiftRepository;
    private final AlertRuleEngine alertRuleEngine;

    public ControlTowerService(TelemetryEventRepository telemetryRepository,
                                OccupancySnapshotRepository occupancyRepository,
                                AlertRepository alertRepository,
                                FacilityRepository facilityRepository,
                                WorkspaceRepository workspaceRepository,
                                ShiftRepository shiftRepository,
                                AlertRuleEngine alertRuleEngine) {
        this.telemetryRepository = telemetryRepository;
        this.occupancyRepository = occupancyRepository;
        this.alertRepository = alertRepository;
        this.facilityRepository = facilityRepository;
        this.workspaceRepository = workspaceRepository;
        this.shiftRepository = shiftRepository;
        this.alertRuleEngine = alertRuleEngine;
    }

    /**
     * Ingest telemetry from PCT (Patient Care Tracker).
     * Stores metric events, updates bed occupancy snapshot if provided,
     * and evaluates alert rules.
     */
    @Transactional
    public void ingestPctTelemetry(TrustContext ctx, PctTelemetryRequest request) {
        UUID tenantId = ctx.tenantId();
        Long facilityId = request.facilityId();

        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        log.info("Ingesting {} PCT metrics for facility {} tenant {}",
                request.metrics().size(), facilityId, tenantId);

        // Store telemetry events
        for (MetricEntry metric : request.metrics()) {
            TelemetryEventEntity event = new TelemetryEventEntity();
            event.setFacility(facility);
            event.setTenantId(tenantId);
            event.setSource("PCT");
            event.setMetricType(metric.metricType());
            event.setMetricValue(metric.metricValue());
            event.setUnit(metric.unit());
            event.setMetadata(metric.metadata());
            telemetryRepository.save(event);
        }

        // Update bed occupancy snapshot if provided
        if (request.bedSnapshot() != null) {
            BedSnapshotDto snapshot = request.bedSnapshot();
            OccupancySnapshotEntity occupancy = new OccupancySnapshotEntity();
            occupancy.setFacility(facility);
            occupancy.setTenantId(tenantId);
            occupancy.setWard(snapshot.ward());
            occupancy.setTotalBeds(snapshot.totalBeds());
            occupancy.setOccupiedBeds(snapshot.occupiedBeds());
            occupancy.setAvailableBeds(snapshot.totalBeds() - snapshot.occupiedBeds());
            occupancy.setSource("PCT");
            occupancyRepository.save(occupancy);

            // Evaluate bed occupancy alerts
            if (snapshot.totalBeds() > 0) {
                BigDecimal occupancyRate = BigDecimal.valueOf(snapshot.occupiedBeds())
                        .divide(BigDecimal.valueOf(snapshot.totalBeds()), 4, java.math.RoundingMode.HALF_UP);
                alertRuleEngine.evaluateBedOccupancy(facility, tenantId, occupancyRate);
            }
        }

        // Evaluate metric-specific alert rules
        for (MetricEntry metric : request.metrics()) {
            alertRuleEngine.evaluateMetric(facility, tenantId, metric.metricType(), metric.metricValue());
        }

        log.info("PCT telemetry ingested for facility {}", facilityId);
    }

    /**
     * Ingest telemetry from OROS (Orders & Results Orchestration Service).
     * Stores metric events and evaluates alert rules.
     */
    @Transactional
    public void ingestOrosTelemetry(TrustContext ctx, OrosTelemetryRequest request) {
        UUID tenantId = ctx.tenantId();
        Long facilityId = request.facilityId();

        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        log.info("Ingesting {} OROS metrics for facility {} tenant {}",
                request.metrics().size(), facilityId, tenantId);

        for (MetricEntry metric : request.metrics()) {
            TelemetryEventEntity event = new TelemetryEventEntity();
            event.setFacility(facility);
            event.setTenantId(tenantId);
            event.setSource("OROS");
            event.setMetricType(metric.metricType());
            event.setMetricValue(metric.metricValue());
            event.setUnit(metric.unit());
            event.setMetadata(metric.metadata());
            telemetryRepository.save(event);

            alertRuleEngine.evaluateMetric(facility, tenantId, metric.metricType(), metric.metricValue());
        }

        log.info("OROS telemetry ingested for facility {}", facilityId);
    }

    /**
     * Get operational summary for a facility including occupancy, recent telemetry,
     * active alerts, workspace count, and shift count.
     */
    @Transactional(readOnly = true)
    public FacilitySummaryResponse getFacilitySummary(TrustContext ctx, Long facilityId) {
        UUID tenantId = ctx.tenantId();

        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        // Latest occupancy
        OccupancySnapshotEntity latestOccupancy = occupancyRepository.findLatestByFacility(facilityId);
        FacilitySummaryResponse.OccupancySnapshot occupancy = latestOccupancy != null
                ? new FacilitySummaryResponse.OccupancySnapshot(
                        latestOccupancy.getTotalBeds(),
                        latestOccupancy.getOccupiedBeds(),
                        latestOccupancy.getAvailableBeds(),
                        latestOccupancy.getSnapshotTime())
                : new FacilitySummaryResponse.OccupancySnapshot(0, 0, 0, null);

        // Recent telemetry (last 10 events)
        Page<TelemetryEventEntity> recentEvents = telemetryRepository
                .findByFacility_IdOrderByRecordedAtDesc(facilityId, PageRequest.of(0, 10));
        List<FacilitySummaryResponse.RecentTelemetry> recentTelemetry = recentEvents.getContent().stream()
                .map(e -> new FacilitySummaryResponse.RecentTelemetry(
                        e.getSource(), e.getMetricType(), e.getMetricValue(), e.getUnit(), e.getRecordedAt()))
                .toList();

        // Active alert count
        int alertCount = (int) alertRepository.findByFacility_IdAndStatusOrderByCreatedAtDesc(
                facilityId, "OPEN", PageRequest.of(0, 1)).getTotalElements();

        // Workspace count
        int workspaceCount = workspaceRepository.findByFacility_IdAndActiveTrue(facilityId).size();

        // Active shift count
        int shiftCount = shiftRepository.findByFacility_IdAndStatus(facilityId, "ACTIVE").size();

        return new FacilitySummaryResponse(
                facilityId,
                facility.getName(),
                facility.getFacilityType(),
                facility.getStatus(),
                occupancy,
                recentTelemetry,
                alertCount,
                workspaceCount,
                shiftCount
        );
    }

    /**
     * Get alerts, optionally filtered by facility and status.
     */
    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(TrustContext ctx, Long facilityId, String status) {
        UUID tenantId = ctx.tenantId();
        Page<AlertEntity> alerts;

        if (facilityId != null && status != null) {
            alerts = alertRepository.findByFacility_IdAndStatusOrderByCreatedAtDesc(
                    facilityId, status, PageRequest.of(0, 100));
        } else if (facilityId != null) {
            alerts = alertRepository.findByFacility_IdAndStatusOrderByCreatedAtDesc(
                    facilityId, "OPEN", PageRequest.of(0, 100));
        } else if (status != null && "OPEN".equals(status)) {
            alerts = alertRepository.findOpenAlerts(tenantId, PageRequest.of(0, 100));
        } else {
            alerts = alertRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 100));
        }

        return alerts.getContent().stream()
                .map(a -> new AlertResponse(
                        a.getId(),
                        a.getFacility().getId(),
                        a.getFacility().getName(),
                        a.getAlertType(),
                        a.getSeverity(),
                        a.getTitle(),
                        a.getDescription(),
                        a.getMetricType(),
                        a.getMetricValue(),
                        a.getThreshold(),
                        a.getStatus(),
                        a.getAcknowledgedBy(),
                        a.getAcknowledgedAt(),
                        a.getResolvedAt(),
                        a.getCreatedAt()))
                .toList();
    }
}
