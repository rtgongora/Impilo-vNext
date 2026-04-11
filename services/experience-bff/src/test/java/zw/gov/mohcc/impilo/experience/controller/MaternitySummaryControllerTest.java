package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaternitySummaryControllerTest {

    @Test
    void summaryAggregatesPartographCtgAndCompatibilityRows() {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        StubJdbcTemplate jdbc = new StubJdbcTemplate(
                List.of(partographSession(sessionId)),
                List.of(partographPoint(sessionId)),
                List.of(ctgSession(sessionId)),
                List.of(ctgChunk(sessionId)),
                List.of(ctgAnnotation(sessionId)),
                List.of(labourEntry())
        );
        MaternitySummaryController controller = new MaternitySummaryController(jdbc, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.getSummary(
                "tenant-moh-zw",
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        List<String> flags = (List<String>) data.get("current_alert_flags");
        @SuppressWarnings("unchecked")
        List<String> modes = (List<String>) data.get("monitoring_modes");
        @SuppressWarnings("unchecked")
        Map<String, Object> latestChunk = (Map<String, Object>) data.get("latest_ctg_chunk");

        assertEquals("ACTIVE_MONITORING", data.get("workflow_state"));
        assertTrue(modes.contains("PARTOGRAPH"));
        assertTrue(modes.contains("CTG"));
        assertTrue(modes.contains("LABOUR_MONITORING_COMPAT"));
        assertTrue(flags.contains("FETAL_HEART_ABNORMAL"));
        assertTrue(flags.contains("FHR_HIGH"));
        assertEquals(BigDecimal.valueOf(170), latestChunk.get("max_value"));
    }

    @Test
    void summaryReturnsEmptyStateWhenNoMonitoringExists() {
        MaternitySummaryController controller = new MaternitySummaryController(
                new StubJdbcTemplate(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new ObjectMapper()
        );

        ResponseEntity<Map<String, Object>> response = controller.getSummary(
                "tenant-moh-zw",
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("NO_ACTIVE_MONITORING", data.get("workflow_state"));
        assertEquals(List.of(), data.get("current_alert_flags"));
    }

    private static Map<String, Object> partographSession(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", sessionId);
        row.put("tenant_id", "tenant-moh-zw");
        row.put("patient_id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        row.put("encounter_id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        row.put("status", "ACTIVE");
        row.put("labour_phase", "ACTIVE_LABOUR");
        row.put("started_at", OffsetDateTime.parse("2026-04-11T08:00:00Z"));
        return row;
    }

    private static Map<String, Object> partographPoint(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.fromString("22222222-2222-2222-2222-222222222222"));
        row.put("session_id", sessionId);
        row.put("observed_at", OffsetDateTime.parse("2026-04-11T09:00:00Z"));
        row.put("fetal_heart_rate_bpm", 168);
        row.put("cervical_dilation_cm", BigDecimal.valueOf(6.0d));
        row.put("systolic_bp", 150);
        row.put("diastolic_bp", 95);
        row.put("temperature_c", BigDecimal.valueOf(38.2d));
        return row;
    }

    private static Map<String, Object> ctgSession(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", sessionId);
        row.put("tenant_id", "tenant-moh-zw");
        row.put("patient_id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        row.put("encounter_id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        row.put("status", "ACTIVE");
        row.put("started_at", OffsetDateTime.parse("2026-04-11T08:05:00Z"));
        row.put("monitoring_mode", "EXTERNAL_CTG");
        return row;
    }

    private static Map<String, Object> ctgChunk(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.fromString("33333333-3333-3333-3333-333333333333"));
        row.put("session_id", sessionId);
        row.put("channel", "FHR");
        row.put("started_at", OffsetDateTime.parse("2026-04-11T08:05:00Z"));
        row.put("sample_rate_hz", BigDecimal.valueOf(4.0d));
        row.put("sample_count", 4);
        row.put("duration_seconds", BigDecimal.ONE);
        row.put("samples_json", "[142,145,170,108]");
        return row;
    }

    private static Map<String, Object> ctgAnnotation(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.fromString("44444444-4444-4444-4444-444444444444"));
        row.put("session_id", sessionId);
        row.put("recorded_at", OffsetDateTime.parse("2026-04-11T08:05:15Z"));
        row.put("category", "DECELERATION");
        return row;
    }

    private static Map<String, Object> labourEntry() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.fromString("55555555-5555-5555-5555-555555555555"));
        row.put("recorded_at", OffsetDateTime.parse("2026-04-11T08:50:00Z"));
        row.put("fetal_heart_rate_bpm", 162);
        row.put("cervical_dilation_cm", BigDecimal.valueOf(7.0d));
        return row;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final List<Map<String, Object>> partographSessions;
        private final List<Map<String, Object>> partographPoints;
        private final List<Map<String, Object>> ctgSessions;
        private final List<Map<String, Object>> ctgChunks;
        private final List<Map<String, Object>> ctgAnnotations;
        private final List<Map<String, Object>> labourEntries;

        private StubJdbcTemplate(
                List<Map<String, Object>> partographSessions,
                List<Map<String, Object>> partographPoints,
                List<Map<String, Object>> ctgSessions,
                List<Map<String, Object>> ctgChunks,
                List<Map<String, Object>> ctgAnnotations,
                List<Map<String, Object>> labourEntries
        ) {
            this.partographSessions = partographSessions;
            this.partographPoints = partographPoints;
            this.ctgSessions = ctgSessions;
            this.ctgChunks = ctgChunks;
            this.ctgAnnotations = ctgAnnotations;
            this.labourEntries = labourEntries;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("maternity_partograph_sessions")) {
                return partographSessions;
            }
            if (sql.contains("maternity_partograph_points")) {
                return partographPoints;
            }
            if (sql.contains("ctg_monitoring_sessions")) {
                return ctgSessions;
            }
            if (sql.contains("ctg_trace_chunks")) {
                return ctgChunks;
            }
            if (sql.contains("ctg_annotations")) {
                return ctgAnnotations;
            }
            if (sql.contains("labour_monitoring_entries")) {
                return labourEntries;
            }
            return List.of();
        }
    }
}
