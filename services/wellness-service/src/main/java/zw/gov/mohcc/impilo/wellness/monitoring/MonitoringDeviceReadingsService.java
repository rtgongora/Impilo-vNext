package zw.gov.mohcc.impilo.wellness.monitoring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ingests device-native monitoring readings into {@code wellness_vitals_log}.
 */
@Service
public class MonitoringDeviceReadingsService {

    public static final String SOURCE_DEVICE_SYNC = "DEVICE_SYNC";

    private final JdbcTemplate jdbc;

    public MonitoringDeviceReadingsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int ingestReadings(String tenantId, Map<String, Object> device, List<?> readings) {
        if (readings == null || readings.isEmpty()) {
            return 0;
        }
        String patientId = stringField(device.get("patient_id"), device.get("patientId"));
        String deviceType = stringField(device.get("device_type"), device.get("deviceType"));
        String deviceName = stringField(device.get("device_name"), device.get("deviceName"));
        int ingested = 0;
        for (Object raw : readings) {
            if (raw instanceof Map<?, ?> reading) {
                ingestOne(tenantId, patientId, deviceType, deviceName, reading);
                ingested++;
            }
        }
        return ingested;
    }

    public UUID ingestOne(
            String tenantId,
            String patientId,
            String deviceType,
            String deviceName,
            Map<?, ?> reading) {
        String vitalType = firstNonBlank(
                stringField(reading.get("vitalType"), reading.get("vital_type")),
                defaultVitalType(deviceType));
        BigDecimal value = toBigDecimal(reading.get("value"));
        if (value == null) {
            throw new IllegalArgumentException("reading value is required");
        }
        String unit = firstNonBlank(
                stringField(reading.get("unit")),
                defaultUnit(vitalType));
        String measuredAt = blankToNull(stringField(reading.get("measuredAt"), reading.get("measured_at")));
        String notes = firstNonBlank(
                stringField(reading.get("notes")),
                deviceName.isBlank() ? "Device sync" : "Device sync · " + deviceName);

        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO wellness_vitals_log (id, tenant_id, patient_id, vital_type, value, unit, measured_at, source, notes)
                VALUES (?, ?, ?, ?, ?, ?, COALESCE(?::timestamptz, NOW()), ?, ?)
                """,
                id,
                tenantId,
                patientId,
                vitalType,
                value,
                unit,
                measuredAt,
                SOURCE_DEVICE_SYNC,
                notes);
        return id;
    }

    private static String defaultVitalType(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            return "HEART_RATE";
        }
        return switch (deviceType.toUpperCase()) {
            case "BLOOD_PRESSURE" -> "BLOOD_PRESSURE_SYSTOLIC";
            case "PULSE_OXIMETER" -> "SPO2";
            case "GLUCOMETER" -> "BLOOD_GLUCOSE";
            case "SCALE" -> "WEIGHT";
            case "WEARABLE" -> "HEART_RATE";
            default -> deviceType.toUpperCase();
        };
    }

    private static String defaultUnit(String vitalType) {
        if (vitalType == null) {
            return "";
        }
        return switch (vitalType.toUpperCase()) {
            case "BLOOD_PRESSURE_SYSTOLIC", "BLOOD_PRESSURE_DIASTOLIC" -> "mmHg";
            case "SPO2" -> "%";
            case "BLOOD_GLUCOSE" -> "mmol/L";
            case "WEIGHT" -> "kg";
            case "HEART_RATE" -> "bpm";
            default -> "";
        };
    }

    private static String stringField(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof BigDecimal b) {
            return b;
        }
        if (val instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String text = val.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }
}
