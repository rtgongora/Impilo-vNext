package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;
import zw.gov.mohcc.impilo.tuso.persistence.entity.AlertEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.OccupancySnapshotEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.TelemetryEventEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.AlertRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.OccupancySnapshotRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates telemetry metrics against configured thresholds and creates alerts.
 *
 * <p>Rules:
 * - No duplicate open alerts for the same alert type + facility.
 * - Thresholds are configured in TusoProperties.
 * - Alerts are published to the event outbox for downstream consumption.</p>
 */
@Service
public class AlertRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleEngine.class);

    private final AlertRepository alertRepository;
    private final EventOutboxRepository outboxRepository;
    private final TusoProperties tusoProperties;
    private final OccupancySnapshotRepository occupancyRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public AlertRuleEngine(AlertRepository alertRepository,
                           EventOutboxRepository outboxRepository,
                           TusoProperties tusoProperties,
                           org.springframework.beans.factory.ObjectProvider<OccupancySnapshotRepository> occupancyRepositoryProvider) {
        this.alertRepository = alertRepository;
        this.outboxRepository = outboxRepository;
        this.tusoProperties = tusoProperties;
        this.occupancyRepository = occupancyRepositoryProvider.getIfAvailable();
    }

    public List<AlertEntity> evaluate(Long facilityId, UUID tenantId, List<TelemetryEventEntity> events) {
        List<AlertEntity> raised = new ArrayList<>();
        TusoProperties.AlertThresholds thresholds = tusoProperties.getAlerts();

        for (TelemetryEventEntity event : events) {
            String metricType = event.getMetricType();
            BigDecimal value = event.getMetricValue();
            if (value == null) continue;

            if ("QUEUE_WAIT".equals(metricType)) {
                BigDecimal threshold = BigDecimal.valueOf(thresholds.getQueueWaitThresholdMinutes());
                if (value.compareTo(threshold) > 0) {
                    createAlertById(facilityId, tenantId, "QUEUE_WAIT", "WARNING",
                            "Queue wait time exceeded",
                            "Queue wait " + value.toPlainString() + " min > " + threshold.toPlainString() + " min",
                            metricType, value, threshold)
                            .ifPresent(raised::add);
                }
            } else if ("TASK_STAGNATION".equals(metricType)) {
                BigDecimal threshold = BigDecimal.valueOf(thresholds.getTaskStagnationThresholdMinutes())
                        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                if (value.compareTo(threshold) > 0) {
                    createAlertById(facilityId, tenantId, "TASK_STAGNATION", "WARNING",
                            "Task stagnation detected",
                            "Stagnation " + value.toPlainString() + " hr > " + threshold.toPlainString() + " hr",
                            metricType, value, threshold)
                            .ifPresent(raised::add);
                }
            }
        }

        if (occupancyRepository != null) {
            occupancyRepository.findTopByFacilityIdOrderBySnapshotTimeDesc(facilityId).ifPresent(snapshot -> {
                if (snapshot.getTotalBeds() != null && snapshot.getTotalBeds() > 0) {
                    BigDecimal rate = BigDecimal.valueOf(snapshot.getOccupiedBeds())
                            .divide(BigDecimal.valueOf(snapshot.getTotalBeds()), 4, RoundingMode.HALF_UP);
                    BigDecimal highThreshold = BigDecimal.valueOf(thresholds.getHighBedOccupancyPct())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                    BigDecimal lowThreshold = BigDecimal.valueOf(thresholds.getLowBedOccupancyPct())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                    if (rate.compareTo(highThreshold) >= 0) {
                        createAlertById(facilityId, tenantId, "HIGH_BED_OCCUPANCY", "CRITICAL",
                                "High bed occupancy", "Occupancy " + rate,
                                "BED_OCCUPANCY", rate, highThreshold)
                                .ifPresent(raised::add);
                    } else if (rate.compareTo(lowThreshold) <= 0) {
                        createAlertById(facilityId, tenantId, "LOW_BED_OCCUPANCY", "WARNING",
                                "Low bed occupancy", "Occupancy " + rate,
                                "BED_OCCUPANCY", rate, lowThreshold)
                                .ifPresent(raised::add);
                    }
                }
            });
        }

        return raised;
    }

    private java.util.Optional<AlertEntity> createAlertById(Long facilityId, UUID tenantId,
                                                             String alertType, String severity,
                                                             String title, String description,
                                                             String metricType, BigDecimal metricValue,
                                                             BigDecimal threshold) {
        var existing = alertRepository.findByFacility_IdAndAlertTypeAndStatus(facilityId, alertType, "OPEN");
        if (existing.isPresent()) {
            log.debug("Skipping duplicate alert {} for facility {}", alertType, facilityId);
            return java.util.Optional.empty();
        }
        AlertEntity alert = new AlertEntity();
        alert.setTenantId(tenantId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setDescription(description);
        alert.setMetricType(metricType);
        alert.setMetricValue(metricValue);
        alert.setThreshold(threshold);
        alert.setStatus("OPEN");
        alert = alertRepository.save(alert);
        if (outboxRepository != null) {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("ALERT");
            event.setEventType("tuso.alert.created");
            event.setPayload(String.format(
                    "{\"facilityId\":%d,\"tenantId\":\"%s\",\"alertType\":\"%s\",\"severity\":\"%s\"}",
                    facilityId, tenantId, alertType, severity));
            outboxRepository.save(event);
        }
        return java.util.Optional.of(alert);
    }

    /**
     * Evaluate a general metric against configured thresholds.
     */
    public void evaluateMetric(FacilityEntity facility, UUID tenantId,
                                String metricType, BigDecimal metricValue) {
        switch (metricType) {
            case "QUEUE_WAIT_MINUTES" -> evaluateQueueWait(facility, tenantId, metricValue);
            case "TASK_STAGNATION_HOURS" -> evaluateTaskStagnation(facility, tenantId, metricValue);
            default -> log.trace("No alert rule for metric type: {}", metricType);
        }
    }

    /**
     * Evaluate bed occupancy rate (0.0 to 1.0) against high/low thresholds.
     */
    public void evaluateBedOccupancy(FacilityEntity facility, UUID tenantId, BigDecimal occupancyRate) {
        TusoProperties.AlertThresholds thresholds = tusoProperties.getAlerts();

        BigDecimal highThreshold = BigDecimal.valueOf(thresholds.getHighBedOccupancyPct())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal lowThreshold = BigDecimal.valueOf(thresholds.getLowBedOccupancyPct())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        if (occupancyRate.compareTo(highThreshold) >= 0) {
            createAlertIfNotDuplicate(facility, tenantId, "HIGH_BED_OCCUPANCY", "CRITICAL",
                    "High bed occupancy",
                    String.format("Bed occupancy at %.1f%% exceeds threshold of %.1f%%",
                            occupancyRate.doubleValue() * 100, highThreshold.doubleValue() * 100),
                    "BED_OCCUPANCY", occupancyRate, highThreshold);
        } else if (occupancyRate.compareTo(lowThreshold) <= 0) {
            createAlertIfNotDuplicate(facility, tenantId, "LOW_BED_OCCUPANCY", "WARNING",
                    "Low bed occupancy",
                    String.format("Bed occupancy at %.1f%% below threshold of %.1f%%",
                            occupancyRate.doubleValue() * 100, lowThreshold.doubleValue() * 100),
                    "BED_OCCUPANCY", occupancyRate, lowThreshold);
        }
    }

    private void evaluateQueueWait(FacilityEntity facility, UUID tenantId, BigDecimal waitMinutes) {
        TusoProperties.AlertThresholds thresholds = tusoProperties.getAlerts();
        BigDecimal threshold = BigDecimal.valueOf(thresholds.getQueueWaitThresholdMinutes());

        if (waitMinutes.compareTo(threshold) > 0) {
            createAlertIfNotDuplicate(facility, tenantId, "QUEUE_WAIT_EXCEEDED", "WARNING",
                    "Queue wait time exceeded",
                    String.format("Queue wait time of %s minutes exceeds threshold of %s minutes",
                            waitMinutes.toPlainString(), threshold.toPlainString()),
                    "QUEUE_WAIT_MINUTES", waitMinutes, threshold);
        }
    }

    private void evaluateTaskStagnation(FacilityEntity facility, UUID tenantId, BigDecimal stagnationHours) {
        TusoProperties.AlertThresholds thresholds = tusoProperties.getAlerts();
        BigDecimal threshold = BigDecimal.valueOf(thresholds.getTaskStagnationThresholdMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);

        if (stagnationHours.compareTo(threshold) > 0) {
            createAlertIfNotDuplicate(facility, tenantId, "TASK_STAGNATION", "WARNING",
                    "Task stagnation detected",
                    String.format("Task stagnation of %s hours exceeds threshold of %s hours",
                            stagnationHours.toPlainString(), threshold.toPlainString()),
                    "TASK_STAGNATION_HOURS", stagnationHours, threshold);
        }
    }

    /**
     * Create an alert only if there is no existing open alert of the same type for this facility.
     */
    private void createAlertIfNotDuplicate(FacilityEntity facility, UUID tenantId,
                                            String alertType, String severity,
                                            String title, String description,
                                            String metricType, BigDecimal metricValue,
                                            BigDecimal threshold) {
        // Check for existing open alert of same type for this facility
        var existing = alertRepository.findByFacility_IdAndAlertTypeAndStatus(facility.getId(), alertType, "OPEN");
        if (existing.isPresent()) {
            log.debug("Skipping duplicate alert {} for facility {}", alertType, facility.getId());
            return;
        }

        AlertEntity alert = new AlertEntity();
        alert.setFacility(facility);
        alert.setTenantId(tenantId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setDescription(description);
        alert.setMetricType(metricType);
        alert.setMetricValue(metricValue);
        alert.setThreshold(threshold);
        alert.setStatus("OPEN");
        alertRepository.save(alert);

        // Publish to outbox
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("ALERT");
        event.setAggregateId(alert.getId().toString());
        event.setEventType("tuso.alert.created");
        event.setPayload(String.format(
                "{\"alertId\":\"%s\",\"facilityId\":%d,\"tenantId\":\"%s\",\"alertType\":\"%s\"," +
                "\"severity\":\"%s\",\"title\":\"%s\",\"metricType\":\"%s\"," +
                "\"metricValue\":%s,\"threshold\":%s}",
                alert.getId(), facility.getId(), tenantId, alertType, severity, title,
                metricType, metricValue.toPlainString(), threshold.toPlainString()));
        outboxRepository.save(event);

        log.info("Alert created: type={}, severity={}, facility={}, metric={}={}",
                alertType, severity, facility.getId(), metricType, metricValue);
    }
}
