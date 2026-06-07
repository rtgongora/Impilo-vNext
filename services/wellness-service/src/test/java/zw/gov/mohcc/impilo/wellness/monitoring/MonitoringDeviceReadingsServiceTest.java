package zw.gov.mohcc.impilo.wellness.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.hamcrest.Matchers.comparesEqualTo;

@ExtendWith(MockitoExtension.class)
class MonitoringDeviceReadingsServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private MonitoringDeviceReadingsService service;

    @BeforeEach
    void setUp() {
        service = new MonitoringDeviceReadingsService(jdbc);
    }

    @Test
    void ingestReadings_insertsDeviceSyncVital() {
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        Map<String, Object> device = Map.of(
                "patient_id", "cpid-1",
                "device_type", "BLOOD_PRESSURE",
                "device_name", "Home cuff");
        int count = service.ingestReadings(
                "tenant-moh-zw",
                device,
                List.of(Map.of("value", 128, "unit", "mmHg")));

        assertEquals(1, count);

        ArgumentCaptor<Object> vitalType = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(
                contains("INSERT INTO wellness_vitals_log"),
                any(UUID.class),
                eq("tenant-moh-zw"),
                eq("cpid-1"),
                vitalType.capture(),
                argThat(comparesEqualTo(BigDecimal.valueOf(128))),
                eq("mmHg"),
                isNull(),
                eq(MonitoringDeviceReadingsService.SOURCE_DEVICE_SYNC),
                eq("Device sync · Home cuff"));
        assertEquals("BLOOD_PRESSURE_SYSTOLIC", vitalType.getValue());
    }
}
