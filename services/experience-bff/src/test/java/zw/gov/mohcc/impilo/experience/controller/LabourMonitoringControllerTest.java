package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabourMonitoringControllerTest {

    @Test
    void listReturnsDerivedStageAndAlertFlags() {
        LabourMonitoringController controller = new LabourMonitoringController(
                new StubJdbcTemplate(List.of(storedEntry()))
        );

        ResponseEntity<Map<String, Object>> response = controller.listLabourMonitoring(
                "tenant-moh-zw",
                "patient-1",
                null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        Map<String, Object> row = data.getFirst();
        @SuppressWarnings("unchecked")
        List<String> alertFlags = (List<String>) row.get("alert_flags");

        assertEquals("ACTIVE_LABOUR", row.get("derived_stage"));
        assertTrue(alertFlags.contains("FETAL_HEART_ABNORMAL"));
        assertTrue(alertFlags.contains("BLOOD_PRESSURE_ELEVATED"));
        assertTrue(alertFlags.contains("MATERNAL_TEMPERATURE_HIGH"));
    }

    @Test
    void recordCreatesLabourMonitoringEntry() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(List.of());
        LabourMonitoringController controller = new LabourMonitoringController(jdbcTemplate);

        LabourMonitoringController.RecordLabourMonitoringRequest request =
                new LabourMonitoringController.RecordLabourMonitoringRequest(
                        "patient-1",
                        "enc-1",
                        "ACTIVE_LABOUR",
                        "2026-04-11T08:00:00Z",
                        "midwife-1",
                        168,
                        4,
                        55,
                        BigDecimal.valueOf(5.0d),
                        3,
                        96,
                        150,
                        92,
                        BigDecimal.valueOf(38.2d),
                        "CLEAR",
                        "NONE",
                        "NONE",
                        BigDecimal.valueOf(4.0d),
                        120,
                        "TRACE",
                        "NEGATIVE",
                        "Anxious but stable",
                        "Escalate if fetal heart remains high"
                );

        ResponseEntity<Map<String, Object>> response = controller.recordLabourMonitoring("tenant-moh-zw", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        List<String> alertFlags = (List<String>) data.get("alert_flags");
        assertEquals("ACTIVE_LABOUR", data.get("derived_stage"));
        assertTrue(alertFlags.contains("FETAL_HEART_ABNORMAL"));
        assertEquals(1, jdbcTemplate.updateCalls);
    }

    private static Map<String, Object> storedEntry() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        row.put("patient_id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        row.put("encounter_id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        row.put("phase", "ACTIVE_LABOUR");
        row.put("recorded_at", OffsetDateTime.parse("2026-04-11T08:00:00Z"));
        row.put("recorded_by", "midwife-1");
        row.put("fetal_heart_rate_bpm", 168);
        row.put("contraction_frequency_10min", 4);
        row.put("contraction_duration_sec", 55);
        row.put("cervical_dilation_cm", BigDecimal.valueOf(6.0d));
        row.put("fetal_descent_fifths", 3);
        row.put("maternal_pulse_bpm", 96);
        row.put("systolic_bp", 150);
        row.put("diastolic_bp", 92);
        row.put("temperature_c", BigDecimal.valueOf(38.2d));
        row.put("liquor", "CLEAR");
        row.put("moulding", "NONE");
        row.put("caput", "NONE");
        row.put("oxytocin_rate_miu_min", BigDecimal.valueOf(4.0d));
        row.put("urine_volume_ml", 120);
        row.put("urine_protein", "TRACE");
        row.put("urine_acetone", "NEGATIVE");
        row.put("maternal_condition", "Anxious but stable");
        row.put("notes", "Escalate if fetal heart remains high");
        return row;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final List<Map<String, Object>> rows;
        private int updateCalls = 0;

        private StubJdbcTemplate(List<Map<String, Object>> rows) {
            this.rows = rows;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return rows;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalls += 1;
            return 1;
        }
    }
}
