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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaternityPartographControllerTest {

    @Test
    void activeSessionReturnsPointsAndPlottingSummary() {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MaternityPartographController controller = new MaternityPartographController(
                new StubJdbcTemplate(sessionRow(sessionId), List.of(pointRow(sessionId)))
        );

        ResponseEntity<Map<String, Object>> response = controller.getActiveSession("tenant-moh-zw", "patient-1", "enc-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) data.get("summary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) data.get("points");
        @SuppressWarnings("unchecked")
        List<String> alerts = (List<String>) points.getFirst().get("alert_flags");

        assertEquals(1, summary.get("point_count"));
        assertEquals(BigDecimal.valueOf(6.0d), points.getFirst().get("cervical_dilation_cm"));
        assertTrue(alerts.contains("FETAL_HEART_ABNORMAL"));
        assertEquals("ACTIVE_LABOUR", points.getFirst().get("derived_stage"));
    }

    @Test
    void createPointAndCloseSessionReturnRealResources() {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        StubJdbcTemplate jdbc = new StubJdbcTemplate(sessionRow(sessionId), List.of(pointRow(sessionId)));
        MaternityPartographController controller = new MaternityPartographController(jdbc);

        MaternityPartographController.CreatePartographSessionRequest createRequest =
                new MaternityPartographController.CreatePartographSessionRequest(
                        "patient-1",
                        "enc-1",
                        "ACTIVE_LABOUR",
                        "2026-04-11T08:00:00Z",
                        "midwife-1",
                        null,
                        "Started in active labour"
                );
        ResponseEntity<Map<String, Object>> createResponse = controller.createSession("tenant-moh-zw", createRequest);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());

        MaternityPartographController.RecordPartographPointRequest pointRequest =
                new MaternityPartographController.RecordPartographPointRequest(
                        "2026-04-11T09:00:00Z",
                        "midwife-1",
                        168,
                        4,
                        55,
                        BigDecimal.valueOf(6.0d),
                        3,
                        96,
                        150,
                        92,
                        BigDecimal.valueOf(38.1d),
                        "CLEAR",
                        "NONE",
                        "NONE",
                        BigDecimal.valueOf(4.0d),
                        120,
                        "TRACE",
                        "NEGATIVE",
                        "Stable",
                        "Escalate if FHR remains elevated"
                );
        ResponseEntity<Map<String, Object>> pointResponse = controller.addPoint("tenant-moh-zw", sessionId, pointRequest);
        assertEquals(HttpStatus.CREATED, pointResponse.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> pointData = (Map<String, Object>) pointResponse.getBody().get("data");
        assertNotNull(pointData.get("alert_flags"));

        MaternityPartographController.ClosePartographSessionRequest closeRequest =
                new MaternityPartographController.ClosePartographSessionRequest(
                        "2026-04-11T11:00:00Z",
                        "midwife-2",
                        "TRANSFERRED_TO_THEATRE",
                        "Closed for operative delivery"
                );
        ResponseEntity<Map<String, Object>> closeResponse = controller.closeSession("tenant-moh-zw", sessionId, closeRequest);
        assertEquals(HttpStatus.OK, closeResponse.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> closedData = (Map<String, Object>) closeResponse.getBody().get("data");
        assertEquals("CLOSED", closedData.get("status"));
        assertTrue(jdbc.updateCalls >= 3);
    }

    private static Map<String, Object> sessionRow(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", sessionId);
        row.put("tenant_id", "tenant-moh-zw");
        row.put("patient_id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        row.put("encounter_id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        row.put("labour_phase", "ACTIVE_LABOUR");
        row.put("status", "ACTIVE");
        row.put("started_at", OffsetDateTime.parse("2026-04-11T08:00:00Z"));
        row.put("started_by", "midwife-1");
        row.put("closed_at", null);
        row.put("closed_by", null);
        row.put("outcome", null);
        row.put("summary_notes", "Started in active labour");
        return row;
    }

    private static Map<String, Object> pointRow(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.fromString("22222222-2222-2222-2222-222222222222"));
        row.put("session_id", sessionId);
        row.put("tenant_id", "tenant-moh-zw");
        row.put("patient_id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        row.put("encounter_id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        row.put("observed_at", OffsetDateTime.parse("2026-04-11T09:00:00Z"));
        row.put("recorded_by", "midwife-1");
        row.put("fetal_heart_rate_bpm", 168);
        row.put("contraction_frequency_10min", 4);
        row.put("contraction_duration_sec", 55);
        row.put("cervical_dilation_cm", BigDecimal.valueOf(6.0d));
        row.put("fetal_descent_fifths", 3);
        row.put("maternal_pulse_bpm", 96);
        row.put("systolic_bp", 150);
        row.put("diastolic_bp", 92);
        row.put("temperature_c", BigDecimal.valueOf(38.1d));
        row.put("liquor", "CLEAR");
        row.put("moulding", "NONE");
        row.put("caput", "NONE");
        row.put("oxytocin_rate_miu_min", BigDecimal.valueOf(4.0d));
        row.put("urine_volume_ml", 120);
        row.put("urine_protein", "TRACE");
        row.put("urine_acetone", "NEGATIVE");
        row.put("maternal_condition", "Stable");
        row.put("notes", "Escalate if FHR remains elevated");
        return row;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private Map<String, Object> session;
        private final List<Map<String, Object>> points;
        private int updateCalls = 0;

        private StubJdbcTemplate(Map<String, Object> session, List<Map<String, Object>> points) {
            this.session = new LinkedHashMap<>(session);
            this.points = points;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("maternity_partograph_sessions")) {
                return List.of(session);
            }
            if (sql.contains("maternity_partograph_points")) {
                return points;
            }
            return List.of();
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            return session;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalls += 1;
            if (sql.contains("UPDATE maternity_partograph_sessions")) {
                session.put("status", "CLOSED");
                session.put("closed_at", args[0]);
                session.put("closed_by", args[1]);
                session.put("outcome", args[2]);
                session.put("summary_notes", args[3]);
            }
            return 1;
        }
    }
}
