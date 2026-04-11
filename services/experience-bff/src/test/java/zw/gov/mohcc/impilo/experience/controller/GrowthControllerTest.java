package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.experience.service.GrowthStandardsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GrowthControllerTest {

    @Test
    void listGrowthReturnsDerivedWhoScores() {
        GrowthController controller = new GrowthController(
                new StubJdbcTemplate(patientRow(), List.of(storedMeasurement())),
                new GrowthStandardsService(new ObjectMapper())
        );

        ResponseEntity<Map<String, Object>> response = controller.listGrowth("tenant-moh-zw", "req-1", "corr-1", "patient-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) data.getFirst().get("attributes");
        @SuppressWarnings("unchecked")
        Map<String, Object> derived = (Map<String, Object>) attributes.get("derived");
        @SuppressWarnings("unchecked")
        Map<String, Object> weightForAge = (Map<String, Object>) derived.get("weight_for_age");

        assertEquals("WHO_2006_CHILD_GROWTH_STANDARDS", derived.get("standard"));
        assertEquals(0, derived.get("age_days"));
        assertNotNull(weightForAge.get("z_score"));
        assertEquals(BigDecimal.valueOf(50.0d).setScale(1), weightForAge.get("percentile"));
    }

    @Test
    void recordGrowthCreatesMeasurementAndReturnsDerivedScores() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(patientRow(), List.of());
        GrowthController controller = new GrowthController(
                jdbcTemplate,
                new GrowthStandardsService(new ObjectMapper())
        );

        GrowthController.RecordGrowthRequest request = new GrowthController.RecordGrowthRequest(
                "patient-1",
                "enc-1",
                "nurse-1",
                "2026-01-01T00:00:00Z",
                BigDecimal.valueOf(3.3464d),
                BigDecimal.valueOf(49.8842d),
                null,
                BigDecimal.valueOf(34.4618d),
                null,
                "LENGTH",
                "Birth assessment"
        );

        ResponseEntity<Map<String, Object>> response = controller.recordGrowth("tenant-moh-zw", "req-1", "corr-1", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        @SuppressWarnings("unchecked")
        Map<String, Object> derived = (Map<String, Object>) attributes.get("derived");
        assertEquals("WHO_2006_CHILD_GROWTH_STANDARDS", derived.get("standard"));
        assertEquals(0, derived.get("age_days"));
        assertEquals(1, jdbcTemplate.updateCalls);
    }

    private static Map<String, Object> patientRow() {
        return Map.of("date_of_birth", LocalDate.parse("2026-01-01"), "sex", "MALE");
    }

    private static Map<String, Object> storedMeasurement() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        row.put("patient_id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        row.put("encounter_id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        row.put("measured_at", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        row.put("recorded_by", "nurse-1");
        row.put("weight_kg", BigDecimal.valueOf(3.3464d));
        row.put("length_cm", BigDecimal.valueOf(49.8842d));
        row.put("height_cm", null);
        row.put("head_circumference_cm", BigDecimal.valueOf(34.4618d));
        row.put("muac_cm", null);
        row.put("bmi", BigDecimal.valueOf(13.45d));
        row.put("measurement_mode", "LENGTH");
        row.put("notes", "Birth assessment");
        row.put("created_at", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        return row;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object> patientRow;
        private final List<Map<String, Object>> storedRows;
        private int updateCalls = 0;

        private StubJdbcTemplate(Map<String, Object> patientRow, List<Map<String, Object>> storedRows) {
            this.patientRow = patientRow;
            this.storedRows = storedRows;
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            return patientRow;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return storedRows;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalls += 1;
            return 1;
        }
    }
}
