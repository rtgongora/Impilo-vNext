package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.integration.IotIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodFridgeEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodFridgeReadingRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodFridgeRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodFridgeMonitoringTest {

    @Mock private BloodFridgeRepository fridgeRepository;
    @Mock private BloodFridgeReadingRepository readingRepository;
    @Mock private IotIntegration iotIntegration;

    private BloodFridgeMonitoringService monitoringService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        monitoringService = new BloodFridgeMonitoringService(fridgeRepository, readingRepository, iotIntegration);
    }

    @Test
    void computeAlarmStatus_normalWithinRange() {
        assertThat(BloodFridgeMonitoringService.computeAlarmStatus(
                new BigDecimal("4.0"), new BigDecimal("2.0"), new BigDecimal("6.0")))
                .isEqualTo("NORMAL");
    }

    @Test
    void computeAlarmStatus_warningNearBoundary() {
        assertThat(BloodFridgeMonitoringService.computeAlarmStatus(
                new BigDecimal("6.5"), new BigDecimal("2.0"), new BigDecimal("6.0")))
                .isEqualTo("WARNING");
    }

    @Test
    void computeAlarmStatus_criticalFarOutside() {
        assertThat(BloodFridgeMonitoringService.computeAlarmStatus(
                new BigDecimal("10.0"), new BigDecimal("2.0"), new BigDecimal("6.0")))
                .isEqualTo("CRITICAL");
    }

    @Test
    void recordReading_updatesFridgeState() {
        UUID fridgeId = UUID.randomUUID();
        BloodFridgeEntity fridge = new BloodFridgeEntity();
        fridge.setFridgeId(fridgeId);
        fridge.setTargetTempMinC(new BigDecimal("2.0"));
        fridge.setTargetTempMaxC(new BigDecimal("6.0"));
        when(fridgeRepository.findByFridgeIdAndTenantId(fridgeId, TENANT_ID)).thenReturn(Optional.of(fridge));
        when(readingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fridgeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var reading = monitoringService.recordReading(
                TENANT_ID, fridgeId, new BigDecimal("4.5"), null, OffsetDateTime.now());

        assertThat(reading.getAlarmStatus()).isEqualTo("NORMAL");
        assertThat(fridge.getTemperatureC()).isEqualByComparingTo("4.5");
        assertThat(fridge.getAlarmStatus()).isEqualTo("NORMAL");
    }

    @Test
    void syncFridgeFromIot_pullsLatestReading() {
        UUID fridgeId = UUID.randomUUID();
        BloodFridgeEntity fridge = new BloodFridgeEntity();
        fridge.setFridgeId(fridgeId);
        fridge.setIotDeviceRef("FRIDGE-SENSOR-1");
        fridge.setTargetTempMinC(new BigDecimal("2.0"));
        fridge.setTargetTempMaxC(new BigDecimal("6.0"));
        when(fridgeRepository.findByFridgeIdAndTenantId(fridgeId, TENANT_ID)).thenReturn(Optional.of(fridge));
        when(iotIntegration.fetchLatestTemperatureReading("FRIDGE-SENSOR-1"))
                .thenReturn(Optional.of(new IotIntegration.IotTelemetryReading(
                        "reading-1", new BigDecimal("3.2"), OffsetDateTime.now())));
        when(readingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fridgeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BloodFridgeEntity synced = monitoringService.syncFridgeFromIot(TENANT_ID, fridgeId);

        assertThat(synced.getTemperatureC()).isEqualByComparingTo("3.2");
        verify(iotIntegration).fetchLatestTemperatureReading("FRIDGE-SENSOR-1");
    }
}
