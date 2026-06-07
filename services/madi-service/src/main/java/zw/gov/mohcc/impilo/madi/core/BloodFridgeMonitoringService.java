package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.integration.IotIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodFridgeEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodFridgeReadingEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodFridgeReadingRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodFridgeRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BloodFridgeMonitoringService {

    private static final BigDecimal DEFAULT_MIN_C = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_MAX_C = new BigDecimal("6.0");

    private final BloodFridgeRepository fridgeRepository;
    private final BloodFridgeReadingRepository readingRepository;
    private final IotIntegration iotIntegration;

    public BloodFridgeMonitoringService(BloodFridgeRepository fridgeRepository,
                                        BloodFridgeReadingRepository readingRepository,
                                        IotIntegration iotIntegration) {
        this.fridgeRepository = fridgeRepository;
        this.readingRepository = readingRepository;
        this.iotIntegration = iotIntegration;
    }

    @Transactional(readOnly = true)
    public List<BloodFridgeEntity> listFridges(UUID tenantId, UUID bloodBankId) {
        if (bloodBankId != null) {
            return fridgeRepository.findByTenantIdAndBloodBankIdOrderByCreatedAtDesc(tenantId, bloodBankId);
        }
        return fridgeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public BloodFridgeReadingEntity recordReading(UUID tenantId, UUID fridgeId, BigDecimal temperatureC,
                                                  String iotReadingRef, OffsetDateTime recordedAt) {
        BloodFridgeEntity fridge = requireFridge(tenantId, fridgeId);
        BigDecimal min = fridge.getTargetTempMinC() != null ? fridge.getTargetTempMinC() : DEFAULT_MIN_C;
        BigDecimal max = fridge.getTargetTempMaxC() != null ? fridge.getTargetTempMaxC() : DEFAULT_MAX_C;
        String alarmStatus = computeAlarmStatus(temperatureC, min, max);

        BloodFridgeReadingEntity reading = new BloodFridgeReadingEntity();
        reading.setTenantId(tenantId);
        reading.setFridgeId(fridgeId);
        reading.setTemperatureC(temperatureC);
        reading.setIotReadingRef(iotReadingRef);
        reading.setAlarmStatus(alarmStatus);
        reading.setRecordedAt(recordedAt != null ? recordedAt : OffsetDateTime.now());
        BloodFridgeReadingEntity saved = readingRepository.save(reading);

        fridge.setTemperatureC(temperatureC);
        fridge.setAlarmStatus(alarmStatus);
        fridge.setLastReadingAt(saved.getRecordedAt());
        fridge.setUpdatedAt(OffsetDateTime.now());
        fridgeRepository.save(fridge);
        return saved;
    }

    @Transactional
    public BloodFridgeEntity syncFridgeFromIot(UUID tenantId, UUID fridgeId) {
        BloodFridgeEntity fridge = requireFridge(tenantId, fridgeId);
        if (fridge.getIotDeviceRef() == null || fridge.getIotDeviceRef().isBlank()) {
            throw new IllegalStateException("Fridge has no IoT device reference configured");
        }
        IotIntegration.IotTelemetryReading telemetry = iotIntegration
                .fetchLatestTemperatureReading(fridge.getIotDeviceRef())
                .orElseThrow(() -> new IllegalStateException("No IoT temperature reading available"));
        recordReading(tenantId, fridgeId, telemetry.temperatureC(), telemetry.readingRef(), telemetry.recordedAt());
        return requireFridge(tenantId, fridgeId);
    }

    @Transactional(readOnly = true)
    public List<BloodFridgeReadingEntity> listReadings(UUID tenantId, UUID fridgeId) {
        requireFridge(tenantId, fridgeId);
        return readingRepository.findByFridgeIdAndTenantIdOrderByRecordedAtDesc(fridgeId, tenantId);
    }

    static String computeAlarmStatus(BigDecimal temperatureC, BigDecimal min, BigDecimal max) {
        if (temperatureC.compareTo(min) >= 0 && temperatureC.compareTo(max) <= 0) {
            return "NORMAL";
        }
        BigDecimal warningLow = min.subtract(BigDecimal.ONE);
        BigDecimal warningHigh = max.add(BigDecimal.ONE);
        if (temperatureC.compareTo(warningLow) >= 0 && temperatureC.compareTo(warningHigh) <= 0) {
            return "WARNING";
        }
        return "CRITICAL";
    }

    private BloodFridgeEntity requireFridge(UUID tenantId, UUID fridgeId) {
        return fridgeRepository.findByFridgeIdAndTenantId(fridgeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Blood fridge not found"));
    }
}
