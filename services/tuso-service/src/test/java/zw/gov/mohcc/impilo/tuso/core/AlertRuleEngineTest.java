package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;
import zw.gov.mohcc.impilo.tuso.persistence.entity.AlertEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.AlertRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlertRuleEngine}.
 *
 * <p>Validates alert generation based on telemetry thresholds and duplicate alert suppression.</p>
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleEngineTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private TusoProperties tusoProperties;
    private AlertRuleEngine engine;

    private static final Long FACILITY_ID = 1L;
    private static final UUID TENANT_ID = UUID.fromString("cccc0000-0000-0000-0000-000000000001");

    private FacilityEntity facility;

    @BeforeEach
    void setUp() {
        tusoProperties = new TusoProperties();
        TusoProperties.AlertThresholds thresholds = new TusoProperties.AlertThresholds();
        thresholds.setQueueWaitThresholdMinutes(60);
        thresholds.setTaskStagnationThresholdMinutes(120);
        thresholds.setHighBedOccupancyPct(90);
        thresholds.setLowBedOccupancyPct(20);
        tusoProperties.setAlerts(thresholds);

        engine = new AlertRuleEngine(alertRepository, outboxRepository, tusoProperties);

        facility = new FacilityEntity();
        facility.setId(FACILITY_ID);
        facility.setTenantId(TENANT_ID);
        facility.setFacilityCode("FAC-001");
        facility.setName("Test Facility");
    }

    @Test
    void evaluateMetric_queueWaitAboveThreshold_raisesAlert() {
        when(alertRepository.findByFacilityIdAndAlertTypeAndStatus(
                eq(FACILITY_ID), eq("QUEUE_WAIT_EXCEEDED"), eq("OPEN")))
                .thenReturn(Optional.empty());

        AlertEntity saved = new AlertEntity();
        saved.setId(UUID.randomUUID());
        saved.setAlertType("QUEUE_WAIT_EXCEEDED");
        saved.setSeverity("WARNING");
        saved.setStatus("OPEN");
        when(alertRepository.save(any(AlertEntity.class))).thenReturn(saved);
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(i -> i.getArgument(0));

        engine.evaluateMetric(facility, TENANT_ID, "QUEUE_WAIT_MINUTES", new BigDecimal("90"));

        ArgumentCaptor<AlertEntity> captor = ArgumentCaptor.forClass(AlertEntity.class);
        verify(alertRepository).save(captor.capture());
        AlertEntity captured = captor.getValue();
        assertThat(captured.getAlertType()).isEqualTo("QUEUE_WAIT_EXCEEDED");
        assertThat(captured.getSeverity()).isEqualTo("WARNING");
        assertThat(captured.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void evaluateMetric_queueWaitBelowThreshold_noAlert() {
        engine.evaluateMetric(facility, TENANT_ID, "QUEUE_WAIT_MINUTES", new BigDecimal("30"));

        verify(alertRepository, never()).save(any(AlertEntity.class));
    }

    @Test
    void evaluateBedOccupancy_highOccupancy_raisesAlert() {
        when(alertRepository.findByFacilityIdAndAlertTypeAndStatus(
                eq(FACILITY_ID), eq("HIGH_BED_OCCUPANCY"), eq("OPEN")))
                .thenReturn(Optional.empty());

        AlertEntity saved = new AlertEntity();
        saved.setId(UUID.randomUUID());
        saved.setAlertType("HIGH_BED_OCCUPANCY");
        saved.setSeverity("CRITICAL");
        saved.setStatus("OPEN");
        when(alertRepository.save(any(AlertEntity.class))).thenReturn(saved);
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(i -> i.getArgument(0));

        engine.evaluateBedOccupancy(facility, TENANT_ID, new BigDecimal("0.95"));

        ArgumentCaptor<AlertEntity> captor = ArgumentCaptor.forClass(AlertEntity.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo("HIGH_BED_OCCUPANCY");
        assertThat(captor.getValue().getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void evaluateMetric_duplicateAlertNotRaised() {
        AlertEntity existingOpen = new AlertEntity();
        existingOpen.setAlertType("QUEUE_WAIT_EXCEEDED");
        existingOpen.setStatus("OPEN");

        when(alertRepository.findByFacilityIdAndAlertTypeAndStatus(
                eq(FACILITY_ID), eq("QUEUE_WAIT_EXCEEDED"), eq("OPEN")))
                .thenReturn(Optional.of(existingOpen));

        engine.evaluateMetric(facility, TENANT_ID, "QUEUE_WAIT_MINUTES", new BigDecimal("90"));

        verify(alertRepository, never()).save(any(AlertEntity.class));
    }

    @Test
    void evaluateBedOccupancy_lowOccupancy_raisesWarning() {
        when(alertRepository.findByFacilityIdAndAlertTypeAndStatus(
                eq(FACILITY_ID), eq("LOW_BED_OCCUPANCY"), eq("OPEN")))
                .thenReturn(Optional.empty());

        AlertEntity saved = new AlertEntity();
        saved.setId(UUID.randomUUID());
        saved.setAlertType("LOW_BED_OCCUPANCY");
        saved.setSeverity("WARNING");
        saved.setStatus("OPEN");
        when(alertRepository.save(any(AlertEntity.class))).thenReturn(saved);
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(i -> i.getArgument(0));

        engine.evaluateBedOccupancy(facility, TENANT_ID, new BigDecimal("0.10"));

        ArgumentCaptor<AlertEntity> captor = ArgumentCaptor.forClass(AlertEntity.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo("LOW_BED_OCCUPANCY");
        assertThat(captor.getValue().getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void evaluateMetric_unknownMetricType_noAlert() {
        engine.evaluateMetric(facility, TENANT_ID, "UNKNOWN_METRIC", new BigDecimal("999"));

        verify(alertRepository, never()).save(any(AlertEntity.class));
    }
}
