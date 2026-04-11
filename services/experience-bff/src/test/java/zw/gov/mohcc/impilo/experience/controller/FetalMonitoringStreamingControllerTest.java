package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import zw.gov.mohcc.impilo.experience.service.FetalMonitoringStreamService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FetalMonitoringStreamingControllerTest {

    @Test
    void streamSessionReturnsEmitterWithSnapshot() {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        RecordingStreamService streamService = new RecordingStreamService();
        FetalMonitoringController delegate = new FetalMonitoringController(
                new StubJdbcTemplate(
                        sessionRow(sessionId),
                        new ArrayList<>(List.of(chunkRow(sessionId))),
                        new ArrayList<>(List.of(annotationRow(sessionId)))
                ),
                new ObjectMapper(),
                streamService
        );
        FetalMonitoringStreamingController controller = new FetalMonitoringStreamingController(delegate, streamService);

        ResponseEntity<SseEmitter> response = controller.streamSession("tenant-moh-zw", sessionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("tenant-moh-zw", streamService.tenantId);
        assertEquals(sessionId, streamService.sessionId);
        assertEquals("ACTIVE", streamService.snapshot.get("status"));
    }

    private static Map<String, Object> sessionRow(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", sessionId);
        row.put("tenant_id", "tenant-moh-zw");
        row.put("patient_id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        row.put("encounter_id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        row.put("status", "ACTIVE");
        row.put("started_at", OffsetDateTime.parse("2026-04-11T08:00:00Z"));
        row.put("started_by", "midwife-1");
        row.put("device_id", "ctg-device-7");
        row.put("monitoring_mode", "EXTERNAL_CTG");
        row.put("baseline_fhr_bpm", 140);
        row.put("baseline_maternal_hr_bpm", 88);
        row.put("summary_notes", "Continuous fetal monitoring");
        return row;
    }

    private static Map<String, Object> chunkRow(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.fromString("22222222-2222-2222-2222-222222222222"));
        row.put("session_id", sessionId);
        row.put("tenant_id", "tenant-moh-zw");
        row.put("patient_id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        row.put("encounter_id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        row.put("channel", "FHR");
        row.put("started_at", OffsetDateTime.parse("2026-04-11T08:05:00Z"));
        row.put("ended_at", OffsetDateTime.parse("2026-04-11T08:05:01Z"));
        row.put("sample_rate_hz", BigDecimal.valueOf(4.0d));
        row.put("sample_count", 4);
        row.put("duration_seconds", BigDecimal.valueOf(1.0d));
        row.put("unit", "BPM");
        row.put("samples_json", "[142,145,170,108]");
        row.put("captured_by", "midwife-1");
        row.put("device_id", "ctg-device-7");
        row.put("notes", "First captured strip");
        return row;
    }

    private static Map<String, Object> annotationRow(UUID sessionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.fromString("33333333-3333-3333-3333-333333333333"));
        row.put("session_id", sessionId);
        row.put("tenant_id", "tenant-moh-zw");
        row.put("patient_id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        row.put("encounter_id", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        row.put("recorded_at", OffsetDateTime.parse("2026-04-11T08:05:15Z"));
        row.put("category", "DECELERATION");
        row.put("channel", "FHR");
        row.put("sample_offset_sec", BigDecimal.valueOf(15.0d));
        row.put("value", "Variable deceleration");
        row.put("severity", "HIGH");
        row.put("notes", "Escalate to obstetric review");
        row.put("recorded_by", "midwife-1");
        return row;
    }

    private static final class RecordingStreamService implements FetalMonitoringStreamService {
        private String tenantId;
        private UUID sessionId;
        private Map<String, Object> snapshot;

        @Override
        public SseEmitter subscribe(String tenantId, UUID sessionId, Map<String, Object> snapshot) {
            this.tenantId = tenantId;
            this.sessionId = sessionId;
            this.snapshot = snapshot;
            return new SseEmitter(0L);
        }

        @Override
        public void publishSessionOpened(String tenantId, UUID sessionId, Map<String, Object> sessionPayload) {
        }

        @Override
        public void publishChunkRecorded(String tenantId, UUID sessionId, Map<String, Object> chunkPayload) {
        }

        @Override
        public void publishAnnotationRecorded(String tenantId, UUID sessionId, Map<String, Object> annotationPayload) {
        }
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object> session;
        private final List<Map<String, Object>> chunks;
        private final List<Map<String, Object>> annotations;

        private StubJdbcTemplate(
                Map<String, Object> session,
                List<Map<String, Object>> chunks,
                List<Map<String, Object>> annotations
        ) {
            this.session = session;
            this.chunks = chunks;
            this.annotations = annotations;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("ctg_monitoring_sessions")) {
                return List.of(session);
            }
            if (sql.contains("ctg_trace_chunks")) {
                return chunks;
            }
            if (sql.contains("ctg_annotations")) {
                return annotations;
            }
            return List.of();
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            return session;
        }
    }
}
