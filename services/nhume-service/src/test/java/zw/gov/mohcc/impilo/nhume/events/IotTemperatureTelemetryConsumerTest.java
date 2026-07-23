package zw.gov.mohcc.impilo.nhume.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.nhume.core.NhumeDeliveryService;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryRequestRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OF-B19 — the telemetry.iot.device.reading consumer: temperature metrics fan
 * into sensor-bound in-transit cold deliveries; other metrics and malformed
 * payloads are tolerated without touching delivery truth; a tenant mismatch is
 * refused.
 */
class IotTemperatureTelemetryConsumerTest {

    private DeliveryRequestRepository deliveryRepo;
    private NhumeDeliveryService deliveryService;
    private IotTemperatureTelemetryConsumer consumer;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        deliveryRepo = mock(DeliveryRequestRepository.class);
        deliveryService = mock(NhumeDeliveryService.class);
        consumer = new IotTemperatureTelemetryConsumer(deliveryRepo, deliveryService,
                new ObjectMapper());
    }

    private DeliveryRequestEntity boundDelivery() {
        DeliveryRequestEntity d = new DeliveryRequestEntity(deliveryId, tenantId, "NHM-1", "TEST");
        d.setColdChainRequired(true);
        d.setSensorDeviceRef("IOT-SENS-1");
        d.setStatus("IN_TRANSIT");
        return d;
    }

    private String reading(String tenant, String device, String metricType, String value) {
        return "{\"reading_id\":\"r-1\",\"tenant_id\":\"" + tenant + "\",\"device_id\":\"" + device
                + "\",\"metric_type\":\"" + metricType + "\",\"metric_value\":\"" + value
                + "\",\"unit\":\"C\",\"recorded_at\":\"2026-07-23T10:15:30Z\",\"source\":\"SENSOR\"}";
    }

    @Test
    void temperatureReading_routesToBoundColdDelivery() {
        when(deliveryRepo.findActiveColdChainBySensor("IOT-SENS-1"))
                .thenReturn(List.of(boundDelivery()));

        consumer.consume(reading(tenantId.toString(), "IOT-SENS-1", "TEMPERATURE", "9.6"));

        verify(deliveryService).recordTemperatureReading(eq(deliveryId), eq("IOT-SENS-1"),
                eq(new BigDecimal("9.6")), any(), eq("IOT_BUS"), isNull());
    }

    @Test
    void nonTemperatureMetric_isIgnored() {
        consumer.consume(reading(tenantId.toString(), "IOT-SENS-1", "HEART_RATE", "72"));
        verify(deliveryService, never()).recordTemperatureReading(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unboundSensor_isIgnored() {
        when(deliveryRepo.findActiveColdChainBySensor("IOT-SENS-1")).thenReturn(List.of());
        consumer.consume(reading(tenantId.toString(), "IOT-SENS-1", "TEMPERATURE", "4.0"));
        verify(deliveryService, never()).recordTemperatureReading(any(), any(), any(), any(), any(), any());
    }

    @Test
    void tenantMismatch_isRefused() {
        when(deliveryRepo.findActiveColdChainBySensor("IOT-SENS-1"))
                .thenReturn(List.of(boundDelivery()));

        consumer.consume(reading(UUID.randomUUID().toString(), "IOT-SENS-1", "TEMPERATURE", "4.0"));

        verify(deliveryService, never()).recordTemperatureReading(any(), any(), any(), any(), any(), any());
    }

    @Test
    void malformedPayloads_areToleratedNeverThrown() {
        consumer.consume("not-json{{{");
        consumer.consume("42");
        consumer.consume("{\"metric_type\":\"TEMPERATURE\"}"); // no device / value
        consumer.consume(reading(tenantId.toString(), "IOT-SENS-1", "TEMPERATURE", "warm-ish"));
        verify(deliveryService, never()).recordTemperatureReading(any(), any(), any(), any(), any(), any());
    }

    @Test
    void parse_extractsFieldsAndSurvivesBadTimestampAndTenant() {
        var ok = consumer.parse(reading(tenantId.toString(), "dev-1", "TEMPERATURE_C", "-18.5"));
        assertThat(ok).isNotNull();
        assertThat(ok.deviceId()).isEqualTo("dev-1");
        assertThat(ok.value()).isEqualByComparingTo("-18.5");
        assertThat(ok.tenantId()).isEqualTo(tenantId);
        assertThat(ok.recordedAt()).isNotNull();

        var badExtras = consumer.parse("{\"device_id\":\"dev-1\",\"metric_type\":\"TEMP\","
                + "\"metric_value\":\"3.2\",\"recorded_at\":\"yesterday\",\"tenant_id\":\"nope\"}");
        assertThat(badExtras).isNotNull(); // degraded, not dropped
        assertThat(badExtras.recordedAt()).isNull();
        assertThat(badExtras.tenantId()).isNull();
    }
}
