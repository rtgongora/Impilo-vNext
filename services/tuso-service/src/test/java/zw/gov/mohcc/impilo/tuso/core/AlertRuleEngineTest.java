package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;
import zw.gov.mohcc.impilo.tuso.persistence.entity.AlertEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.OccupancySnapshotEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.TelemetryEventEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.AlertRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.OccupancySnapshotRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlertRuleEngine}.
 *
 * <p>Validates alert generation based on telemetry thresholds, occupancy
 * snapshots, and duplicate alert suppression.</p>
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleEngineTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private OccupancySnapshotRepository occupancyRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<OccupancySnapshotRepository> occupancyRepositoryProvider;

    private TusoProperties tusoProperties;
    private AlertRuleEngine engine;

    private static final Long FACILITY_ID = 1L;
    private static final UUID TENANT_ID = UUID.fromString("cccc0000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        tusoProperties = new TusoProperties();
        TusoProperties.AlertThresholds thresholds = new TusoProperties.AlertThresholds();
        thresholds.setQueueWaitThresholdMinutes(60);
        thresholds.setTaskStagnationThresholdMinutes(120);
        thresholds.setHighBedOccupancyPct(90);
        thresholds.setLowBedOccupancyPct(20);
        tusoProperties.setAlerts(thresholds);

        when(occupancyRepositoryProvider.getIfAvailable()).thenReturn(occupancyRepository);

        engine = new AlertRuleEngine(alertRepository, outboxRepository, tusoProperties, occupancyRepositoryProvider);
    }

    @Test
    void evaluate_queueWaitAboveThreshold_raisesAlert() {
        TelemetryEventEntity event = createTelemetryEvent("QUEUE_WAIT", new BigDecimal("90"));

        when(alertRepository.findByFacility_IdAndAlertTypeAndStatus(eq(FACILITY_ID), eq("QUEUE_WAIT"), eq("OPEN")))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(AlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(occupancyRepository.findTopByFacilityIdOrderBySnapshotTimeDesc(FACILITY_ID))
                .thenReturn(Optional.empty());

        List<AlertEntity> alerts = engine.evaluate(FACILITY_ID, TENANT_ID, List.of(event));

        assertThat(alerts).isNotEmpty();

        ArgumentCaptor<AlertEntity> captor = ArgumentCaptor.forClass(AlertEntity.class);
        verify(alertRepository).save(captor.capture());
        AlertEntity saved = captor.getValue();
        assertThat(saved.getAlertType()).isEqualTo("QUEUE_WAIT");
        assertThat(saved.getSeverity()).isEqualTo("WARNING");
        assertThat(saved.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void evaluate_queueWaitBelowThreshold_noAlert() {
        TelemetryEventEntity event = createTelemetryEvent("QUEUE_WAIT", new BigDecimal("30"));

        when(occupancyRepository.findTopByFacilityIdOrderBySnapshotTimeDesc(FACILITY_ID))
                .thenReturn(Optional.empty());

        List<AlertEntity> alerts = engine.evaluate(FACILITY_ID, TENANT_ID, List.of(event));

        assertThat(alerts).isEmpty();
        verify(alertRepository, never()).save(any(AlertEntity.class));
    }

    @Test
    void evaluate_highBedOccupancy_raisesAlert() {
        OccupancySnapshotEntity snapshot = new OccupancySnapshotEntity();
        snapshot.setTotalBeds(100);
        snapshot.setOccupiedBeds(95);

        when(occupancyRepository.findTopByFacilityIdOrderBySnapshotTimeDesc(FACILITY_ID))
                .thenReturn(Optional.of(snapshot));
        when(alertRepository.findByFacility_IdAndAlertTypeAndStatus(
                eq(FACILITY_ID), eq("HIGH_BED_OCCUPANCY"), eq("OPEN")))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(AlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<AlertEntity> alerts = engine.evaluate(FACILITY_ID, TENANT_ID, Collections.emptyList());

        assertThat(alerts).isNotEmpty();

        boolean hasHighOccupancy = alerts.stream()
                .anyMatch(a -> "HIGH_BED_OCCUPANCY".equals(a.getAlertType()));
        assertThat(hasHighOccupancy).isTrue();
    }

    @Test
    void evaluate_duplicateAlertNotRaised() {
        TelemetryEventEntity event = createTelemetryEvent("QUEUE_WAIT", new BigDecimal("90"));

        AlertEntity existingOpen = new AlertEntity();
        existingOpen.setAlertType("QUEUE_WAIT");
        existingOpen.setStatus("OPEN");

        when(alertRepository.findByFacility_IdAndAlertTypeAndStatus(eq(FACILITY_ID), eq("QUEUE_WAIT"), eq("OPEN")))
                .thenReturn(Optional.of(existingOpen));
        when(occupancyRepository.findTopByFacilityIdOrderBySnapshotTimeDesc(FACILITY_ID))
                .thenReturn(Optional.empty());

        List<AlertEntity> alerts = engine.evaluate(FACILITY_ID, TENANT_ID, List.of(event));

        assertThat(alerts).isEmpty();
        verify(alertRepository, never()).save(any(AlertEntity.class));
    }

    @Test
    void evaluate_multipleThresholds_raisesMultipleAlerts() {
        TelemetryEventEntity queueEvent = createTelemetryEvent("QUEUE_WAIT", new BigDecimal("90"));

        OccupancySnapshotEntity snapshot = new OccupancySnapshotEntity();
        snapshot.setTotalBeds(100);
        snapshot.setOccupiedBeds(95);

        when(occupancyRepository.findTopByFacilityIdOrderBySnapshotTimeDesc(FACILITY_ID))
                .thenReturn(Optional.of(snapshot));
        when(alertRepository.findByFacility_IdAndAlertTypeAndStatus(any(), any(), eq("OPEN")))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(AlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<AlertEntity> alerts = engine.evaluate(FACILITY_ID, TENANT_ID, List.of(queueEvent));

        assertThat(alerts).hasSizeGreaterThanOrEqualTo(2);

        boolean hasQueueWait = alerts.stream()
                .anyMatch(a -> "QUEUE_WAIT".equals(a.getAlertType()));
        boolean hasHighOccupancy = alerts.stream()
                .anyMatch(a -> "HIGH_BED_OCCUPANCY".equals(a.getAlertType()));

        assertThat(hasQueueWait).isTrue();
        assertThat(hasHighOccupancy).isTrue();

        verify(alertRepository, times(alerts.size())).save(any(AlertEntity.class));
    }

    // ---- Helper ----

    private TelemetryEventEntity createTelemetryEvent(String metricType, BigDecimal value) {
        TelemetryEventEntity event = new TelemetryEventEntity();
        event.setMetricType(metricType);
        event.setMetricValue(value);
        event.setSource("TEST");
        return event;
    }
}
