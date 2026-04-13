package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.experience.service.FetalMonitoringStreamService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetalMonitoringControllerTest {

    @Test
    void activeSessionReturnsChunkAndAnnotationSummary() {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        StubJdbcTemplate jdbc = new StubJdbcTemplate(
                sessionRow(sessionId),
                new ArrayList<>(List.of(chunkRow(sessionId))),
                new ArrayList<>(List.of(annotationRow(sessionId)))
        );
        RecordingStreamService streamService = new RecordingStreamService();
        FetalMonitoringController controller = new FetalMonitoringController(jdbc, new ObjectMapper(), streamService);

        ResponseEntity<Map<String, Object>> response = controller.getActiveSession(
                "tenant-moh-zw",
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) data.get("summary");
        @SuppressWarnings("unchecked")
        List<String> channels = (List<String>) summary.get("active_channels");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chunkSummaries = (List<Map<String, Object>>) summary.get("chunk_summaries");
        @SuppressWarnings("unchecked")
        List<String> alertFlags = (List<String>) chunkSummaries.getFirst().get("alert_flags");

        assertEquals(1, summary.get("chunk_count"));
        assertEquals(1, summary.get("annotation_count"));
        assertTrue(channels.contains("FHR"));
        assertTrue(alertFlags.contains("FHR_LOW"));
        assertTrue(alertFlags.contains("FHR_HIGH"));
    }

    @Test
    void createSessionThenAddChunkAndAnnotationSupportDisplayCapture() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(null, new ArrayList<>(), new ArrayList<>());
        RecordingStreamService streamService = new RecordingStreamService();
        FetalMonitoringController controller = new FetalMonitoringController(jdbc, new ObjectMapper(), streamService);

        FetalMonitoringController.CreateFetalMonitoringSessionRequest createRequest =
                new FetalMonitoringController.CreateFetalMonitoringSessionRequest(
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                        "2026-04-11T08:00:00Z",
                        "midwife-1",
                        "ctg-device-7",
                        "EXTERNAL_CTG",
                        140,
                        88,
                        "Continuous fetal monitoring"
                );
        ResponseEntity<Map<String, Object>> createResponse = controller.createSession("tenant-moh-zw", createRequest);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertEquals(1, streamService.sessionOpenedCount);

        @SuppressWarnings("unchecked")
        Map<String, Object> sessionData = (Map<String, Object>) createResponse.getBody().get("data");
        UUID sessionId = UUID.fromString(String.valueOf(sessionData.get("id")));

        FetalMonitoringController.RecordTraceChunkRequest chunkRequest =
                new FetalMonitoringController.RecordTraceChunkRequest(
                        "FHR",
                        "2026-04-11T08:05:00Z",
                        BigDecimal.valueOf(4.0d),
                        "BPM",
                        List.of(
                                BigDecimal.valueOf(142),
                                BigDecimal.valueOf(145),
                                BigDecimal.valueOf(170),
                                BigDecimal.valueOf(108)
                        ),
                        "midwife-1",
                        "ctg-device-7",
                        "First captured strip"
                );
        ResponseEntity<Map<String, Object>> chunkResponse = controller.addChunk("tenant-moh-zw", sessionId, chunkRequest);
        assertEquals(HttpStatus.CREATED, chunkResponse.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> chunkData = (Map<String, Object>) chunkResponse.getBody().get("data");
        @SuppressWarnings("unchecked")
        List<String> chunkAlerts = (List<String>) chunkData.get("alert_flags");
        assertEquals(4, chunkData.get("sample_count"));
        assertTrue(chunkAlerts.contains("FHR_LOW"));
        assertTrue(chunkAlerts.contains("FHR_HIGH"));
        assertEquals(1, streamService.chunkRecordedCount);

        FetalMonitoringController.RecordCtgAnnotationRequest annotationRequest =
                new FetalMonitoringController.RecordCtgAnnotationRequest(
                        "2026-04-11T08:05:15Z",
                        "DECELERATION",
                        "FHR",
                        BigDecimal.valueOf(15.000d),
                        "Variable deceleration",
                        "HIGH",
                        "Escalate to obstetric review",
                        "midwife-1"
                );
        ResponseEntity<Map<String, Object>> annotationResponse = controller.addAnnotation("tenant-moh-zw", sessionId, annotationRequest);
        assertEquals(HttpStatus.CREATED, annotationResponse.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> annotationData = (Map<String, Object>) annotationResponse.getBody().get("data");
        assertEquals("DECELERATION", annotationData.get("category"));
        assertEquals(1, streamService.annotationRecordedCount);

        ResponseEntity<Map<String, Object>> chunksResponse = controller.getChunks("tenant-moh-zw", sessionId, "FHR", null, null);
        assertEquals(HttpStatus.OK, chunksResponse.getStatusCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) chunksResponse.getBody().get("data");
        @SuppressWarnings("unchecked")
        List<BigDecimal> samples = (List<BigDecimal>) chunks.getFirst().get("samples");
        assertEquals(4, samples.size());
        assertEquals(BigDecimal.valueOf(170), chunks.getFirst().get("max_value"));
        assertTrue(jdbc.updateCalls >= 3);
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
        row.put("closed_at", null);
        row.put("closed_by", null);
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

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private Map<String, Object> session;
        private final List<Map<String, Object>> chunks;
        private final List<Map<String, Object>> annotations;
        private int updateCalls = 0;

        private StubJdbcTemplate(
                Map<String, Object> session,
                List<Map<String, Object>> chunks,
                List<Map<String, Object>> annotations
        ) {
            this.session = session == null ? null : new LinkedHashMap<>(session);
            this.chunks = chunks;
            this.annotations = annotations;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("ctg_monitoring_sessions")) {
                return session == null ? List.of() : List.of(session);
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
            if (session == null) {
                throw new EmptyResultDataAccessException(1);
            }
            return session;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalls += 1;
            if (sql.contains("INSERT INTO ctg_monitoring_sessions")) {
                session = new LinkedHashMap<>();
                session.put("id", args[0]);
                session.put("tenant_id", args[1]);
                session.put("patient_id", args[2]);
                session.put("encounter_id", args[3]);
                session.put("status", "ACTIVE");
                session.put("started_at", args[4]);
                session.put("started_by", args[5]);
                session.put("closed_at", null);
                session.put("closed_by", null);
                session.put("device_id", args[6]);
                session.put("monitoring_mode", args[7]);
                session.put("baseline_fhr_bpm", args[8]);
                session.put("baseline_maternal_hr_bpm", args[9]);
                session.put("summary_notes", args[10]);
            } else if (sql.contains("INSERT INTO ctg_trace_chunks")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", args[0]);
                row.put("session_id", args[1]);
                row.put("tenant_id", args[2]);
                row.put("patient_id", args[3]);
                row.put("encounter_id", args[4]);
                row.put("channel", args[5]);
                row.put("started_at", args[6]);
                row.put("ended_at", args[7]);
                row.put("sample_rate_hz", args[8]);
                row.put("sample_count", args[9]);
                row.put("duration_seconds", args[10]);
                row.put("unit", args[11]);
                row.put("samples_json", args[12]);
                row.put("captured_by", args[13]);
                row.put("device_id", args[14]);
                row.put("notes", args[15]);
                chunks.add(row);
            } else if (sql.contains("INSERT INTO ctg_annotations")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", args[0]);
                row.put("session_id", args[1]);
                row.put("tenant_id", args[2]);
                row.put("patient_id", args[3]);
                row.put("encounter_id", args[4]);
                row.put("recorded_at", args[5]);
                row.put("category", args[6]);
                row.put("channel", args[7]);
                row.put("sample_offset_sec", args[8]);
                row.put("value", args[9]);
                row.put("severity", args[10]);
                row.put("notes", args[11]);
                row.put("recorded_by", args[12]);
                annotations.add(row);
            }
            return 1;
        }
    }

    private static final class RecordingStreamService implements FetalMonitoringStreamService {
        private int sessionOpenedCount = 0;
        private int chunkRecordedCount = 0;
        private int annotationRecordedCount = 0;

        @Override
        public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribe(
                String tenantId,
                UUID sessionId,
                Map<String, Object> snapshot
        ) {
            return new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);
        }

        @Override
        public void publishSessionOpened(String tenantId, UUID sessionId, Map<String, Object> sessionPayload) {
            sessionOpenedCount += 1;
        }

        @Override
        public void publishChunkRecorded(String tenantId, UUID sessionId, Map<String, Object> chunkPayload) {
            chunkRecordedCount += 1;
        }

        @Override
        public void publishAnnotationRecorded(String tenantId, UUID sessionId, Map<String, Object> annotationPayload) {
            annotationRecordedCount += 1;
        }
    }
}
