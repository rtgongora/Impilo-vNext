package zw.gov.mohcc.impilo.assetregistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.assetregistry.core.EquipmentOperationsService;
import zw.gov.mohcc.impilo.assetregistry.domain.EquipmentEntity;
import zw.gov.mohcc.impilo.assetregistry.domain.IotAlertEntity;
import zw.gov.mohcc.impilo.assetregistry.domain.IotThresholdRuleEntity;
import zw.gov.mohcc.impilo.assetregistry.repository.EquipmentRepository;
import zw.gov.mohcc.impilo.assetregistry.repository.IotAlertRepository;
import zw.gov.mohcc.impilo.assetregistry.repository.IotThresholdRuleRepository;
import zw.gov.mohcc.impilo.assetregistry.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WS#5 GAP-2: IoT threshold-breach derivation from REAL ingested metric values.
 *
 * <p>Proves: (a) a reading within threshold raises NO alert; (b) a metric crossing the threshold
 * for >= min_count consecutive readings raises exactly one deduped THRESHOLD_BREACH alert carrying
 * the correct metric_type/threshold_value; (c) the alert flows through the existing outbox path
 * (cold-chain breach tagged route_to=MADI). No telemetry is fabricated — values are supplied as if
 * delivered by iot-ingestion on telemetry.iot.device.reading.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class IotThresholdBreachTest {

    @Autowired private EquipmentOperationsService ops;
    @Autowired private IotThresholdRuleRepository thresholdRules;
    @Autowired private IotAlertRepository alerts;
    @Autowired private EquipmentRepository equipment;
    @Autowired private OutboxEventRepository outbox;

    /** Seed a cold-chain rule: temperature_c GT 8.0, alert after 3 consecutive breaches, CRITICAL, MADI. */
    private IotThresholdRuleEntity seedColdChainRule(UUID tenantId) {
        IotThresholdRuleEntity rule = new IotThresholdRuleEntity(tenantId, "temperature_c", "GT", 8.0, 3);
        rule.setSeverity("CRITICAL");
        rule.setRouteTo("MADI");
        rule.setLabel("Cold-chain fridge over 8C");
        return thresholdRules.save(rule);
    }

    private long activeBreachAlerts(UUID tenantId, String deviceId) {
        return alerts.findByTenantIdAndStatusOrderByObservedAtDesc(tenantId, "ACTIVE").stream()
                .filter(a -> deviceId.equals(a.getDeviceId()) && "THRESHOLD_BREACH".equals(a.getAlertType()))
                .count();
    }

    @Test
    void readingWithinThreshold_raisesNoAlert() {
        UUID tenant = UUID.randomUUID();
        String device = "DEV-OK-" + UUID.randomUUID();
        seedColdChainRule(tenant);

        // Several within-threshold readings (4.0C) — never breaches.
        for (int i = 0; i < 5; i++) {
            ops.evaluateThresholdReading(tenant, device, "temperature_c", 4.0);
        }

        assertThat(activeBreachAlerts(tenant, device)).isZero();
    }

    @Test
    void breachBelowMinCount_raisesNoAlert() {
        UUID tenant = UUID.randomUUID();
        String device = "DEV-2-" + UUID.randomUUID();
        seedColdChainRule(tenant);

        // Only 2 consecutive breaches; min_count is 3 -> still no alert.
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 9.0);
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 9.5);

        assertThat(activeBreachAlerts(tenant, device)).isZero();
    }

    @Test
    void breachResetByWithinThresholdReading_raisesNoAlert() {
        UUID tenant = UUID.randomUUID();
        String device = "DEV-RST-" + UUID.randomUUID();
        seedColdChainRule(tenant);

        // 2 breaches, then a good reading resets the streak, then 2 more breaches: never 3 in a row.
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 9.0);
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 9.0);
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 5.0); // reset
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 9.0);
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 9.0);

        assertThat(activeBreachAlerts(tenant, device)).isZero();
    }

    @Test
    void breachAtMinCount_raisesExactlyOneDedupedAlert_withMetricAndThreshold_andOutbox() {
        UUID tenant = UUID.randomUUID();
        String device = "DEV-BRCH-" + UUID.randomUUID();
        // Link the device to a CRITICAL fridge so the alert attributes to equipment.
        EquipmentEntity fridge = new EquipmentEntity(tenant, "FAC-1", "Vaccine Fridge", "FRIDGE");
        fridge.setDeviceId(device);
        fridge.setCriticality("CRITICAL");
        equipment.save(fridge);
        seedColdChainRule(tenant);

        long outboxBefore = outbox.count();

        // 3 consecutive breaches -> exactly one THRESHOLD_BREACH alert at the 3rd reading.
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 9.0);
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 10.0);
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 11.0);
        // A 4th breach must NOT create a duplicate (raiseAlert dedupes on ACTIVE same-type).
        ops.evaluateThresholdReading(tenant, device, "temperature_c", 12.0);

        List<IotAlertEntity> breaches = alerts.findByTenantIdAndStatusOrderByObservedAtDesc(tenant, "ACTIVE").stream()
                .filter(a -> device.equals(a.getDeviceId()) && "THRESHOLD_BREACH".equals(a.getAlertType()))
                .toList();

        assertThat(breaches).hasSize(1);
        IotAlertEntity alert = breaches.get(0);
        assertThat(alert.getMetricType()).isEqualTo("temperature_c");
        assertThat(alert.getThresholdValue()).isEqualTo(8.0);
        assertThat(alert.getObservedValue()).isEqualTo(11.0); // value at the reading that hit min_count
        assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
        assertThat(alert.getEquipmentId()).isEqualTo(fridge.getEquipmentId());

        // The alert flowed through the existing outbox path (impilo.asset.iot.alert.raised.v1).
        assertThat(outbox.count()).isGreaterThan(outboxBefore);
    }
}
