package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import zw.gov.mohcc.impilo.experience.service.FetalMonitoringStreamService;

@RestController
@RequestMapping("/internal/v1/maternity/ctg")
public class FetalMonitoringController {

    private static final TypeReference<List<BigDecimal>> SAMPLE_LIST_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FetalMonitoringStreamService streamService;

    public FetalMonitoringController(JdbcTemplate jdbc, ObjectMapper objectMapper, FetalMonitoringStreamService streamService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.streamService = streamService;
    }

    @PostMapping("/sessions")
    @Transactional
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody CreateFetalMonitoringSessionRequest request
    ) {
        if (request.patientId() == null || request.patientId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "patientId is required"));
        }

        UUID id = UUID.randomUUID();
        OffsetDateTime startedAt = parseDateTime(request.startedAt());
        jdbc.update("""
                        INSERT INTO ctg_monitoring_sessions (
                            id, tenant_id, patient_id, encounter_id, status, started_at, started_by,
                            device_id, monitoring_mode, baseline_fhr_bpm, baseline_maternal_hr_bpm, summary_notes
                        ) VALUES (?, ?, ?::uuid, ?::uuid, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                tenantId,
                request.patientId(),
                request.encounterId(),
                startedAt,
                defaultString(request.startedBy(), ""),
                request.deviceId(),
                defaultString(request.monitoringMode(), "EXTERNAL_CTG"),
                request.baselineFhrBpm(),
                request.baselineMaternalHrBpm(),
                request.summaryNotes()
        );

        Map<String, Object> sessionPayload = buildSessionResource(sessionRow(id, request, startedAt), List.of(), List.of());
        streamService.publishSessionOpened(tenantId, id, sessionPayload);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", sessionPayload));
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<Map<String, Object>> getActiveSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(required = false) String encounterId
    ) {
        List<Map<String, Object>> rows = encounterId == null || encounterId.isBlank()
                ? jdbc.queryForList("""
                        SELECT * FROM ctg_monitoring_sessions
                        WHERE tenant_id = ? AND patient_id = ?::uuid AND status = 'ACTIVE'
                        ORDER BY started_at DESC
                        LIMIT 1
                        """, tenantId, patientId)
                : jdbc.queryForList("""
                        SELECT * FROM ctg_monitoring_sessions
                        WHERE tenant_id = ? AND patient_id = ?::uuid AND encounter_id = ?::uuid AND status = 'ACTIVE'
                        ORDER BY started_at DESC
                        LIMIT 1
                        """, tenantId, patientId, encounterId);

        if (rows.isEmpty()) {
            return ResponseEntity.ok(Map.of("data", null));
        }

        Map<String, Object> session = rows.getFirst();
        UUID sessionId = uuidValue(session.get("id"));
        return ResponseEntity.ok(Map.of("data", buildSessionResource(
                session,
                loadAnnotations(sessionId),
                loadChunks(sessionId, null, null, null)
        )));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID sessionId
    ) {
        try {
            Map<String, Object> session = jdbc.queryForMap(
                    "SELECT * FROM ctg_monitoring_sessions WHERE tenant_id = ? AND id = ?",
                    tenantId, sessionId
            );
            return ResponseEntity.ok(Map.of("data", buildSessionResource(
                    session,
                    loadAnnotations(sessionId),
                    loadChunks(sessionId, null, null, null)
            )));
        } catch (EmptyResultDataAccessException ignored) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CTG monitoring session not found"));
        }
    }

    @PostMapping("/sessions/{sessionId}/chunks")
    @Transactional
    public ResponseEntity<Map<String, Object>> addChunk(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID sessionId,
            @RequestBody RecordTraceChunkRequest request
    ) {
        if (request.channel() == null || request.channel().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "channel is required"));
        }
        if (request.sampleRateHz() == null || request.sampleRateHz().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "sampleRateHz must be greater than zero"));
        }
        if (request.samples() == null || request.samples().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "samples are required"));
        }

        try {
            Map<String, Object> session = jdbc.queryForMap(
                    "SELECT * FROM ctg_monitoring_sessions WHERE tenant_id = ? AND id = ?",
                    tenantId, sessionId
            );
            if (!"ACTIVE".equals(String.valueOf(session.get("status")))) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "CTG monitoring session is not active"));
            }

            UUID chunkId = UUID.randomUUID();
            OffsetDateTime startedAt = parseDateTime(request.startedAt());
            int sampleCount = request.samples().size();
            BigDecimal durationSeconds = BigDecimal.valueOf(sampleCount)
                    .divide(request.sampleRateHz(), 3, RoundingMode.HALF_UP);
            OffsetDateTime endedAt = startedAt.plusNanos(durationSeconds.multiply(BigDecimal.valueOf(1_000_000_000L)).longValue());
            String sampleJson = toJson(request.samples());

            jdbc.update("""
                            INSERT INTO ctg_trace_chunks (
                                id, session_id, tenant_id, patient_id, encounter_id, channel,
                                started_at, ended_at, sample_rate_hz, sample_count, duration_seconds,
                                unit, samples_json, captured_by, device_id, notes
                            ) VALUES (
                                ?, ?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?
                            )
                            """,
                    chunkId,
                    sessionId,
                    tenantId,
                    session.get("patient_id"),
                    session.get("encounter_id"),
                    request.channel().trim().toUpperCase(),
                    startedAt,
                    endedAt,
                    request.sampleRateHz(),
                    sampleCount,
                    durationSeconds,
                    defaultString(request.unit(), defaultUnitForChannel(request.channel())),
                    sampleJson,
                    defaultString(request.capturedBy(), ""),
                    coalesce(request.deviceId(), session.get("device_id")),
                    request.notes()
            );

            Map<String, Object> chunkPayload = enrichChunk(chunkRow(
                    chunkId,
                    sessionId,
                    session,
                    request,
                    startedAt,
                    endedAt,
                    sampleCount,
                    durationSeconds
            ));
            streamService.publishChunkRecorded(tenantId, sessionId, chunkPayload);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", chunkPayload));
        } catch (EmptyResultDataAccessException ignored) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CTG monitoring session not found"));
        }
    }

    @GetMapping("/sessions/{sessionId}/chunks")
    public ResponseEntity<Map<String, Object>> getChunks(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID sessionId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        try {
            jdbc.queryForMap("SELECT id FROM ctg_monitoring_sessions WHERE tenant_id = ? AND id = ?", tenantId, sessionId);
            List<Map<String, Object>> chunks = loadChunks(
                    sessionId,
                    channel == null || channel.isBlank() ? null : channel.trim().toUpperCase(),
                    parseDateTimeOrNull(from),
                    parseDateTimeOrNull(to)
            );
            return ResponseEntity.ok(Map.of("data", chunks));
        } catch (EmptyResultDataAccessException ignored) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CTG monitoring session not found"));
        }
    }

    @PostMapping("/sessions/{sessionId}/annotations")
    @Transactional
    public ResponseEntity<Map<String, Object>> addAnnotation(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID sessionId,
            @RequestBody RecordCtgAnnotationRequest request
    ) {
        if (request.category() == null || request.category().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "category is required"));
        }

        try {
            Map<String, Object> session = jdbc.queryForMap(
                    "SELECT * FROM ctg_monitoring_sessions WHERE tenant_id = ? AND id = ?",
                    tenantId, sessionId
            );
            if (!"ACTIVE".equals(String.valueOf(session.get("status")))) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "CTG monitoring session is not active"));
            }

            UUID annotationId = UUID.randomUUID();
            OffsetDateTime recordedAt = parseDateTime(request.recordedAt());
            jdbc.update("""
                            INSERT INTO ctg_annotations (
                                id, session_id, tenant_id, patient_id, encounter_id, recorded_at,
                                category, channel, sample_offset_sec, value, severity, notes, recorded_by
                            ) VALUES (
                                ?, ?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?
                            )
                            """,
                    annotationId,
                    sessionId,
                    tenantId,
                    session.get("patient_id"),
                    session.get("encounter_id"),
                    recordedAt,
                    request.category().trim().toUpperCase(),
                    request.channel() == null || request.channel().isBlank() ? null : request.channel().trim().toUpperCase(),
                    request.sampleOffsetSec(),
                    request.value(),
                    request.severity() == null || request.severity().isBlank() ? null : request.severity().trim().toUpperCase(),
                    request.notes(),
                    defaultString(request.recordedBy(), "")
            );

            Map<String, Object> annotationPayload = annotationRow(
                    annotationId,
                    sessionId,
                    session,
                    request,
                    recordedAt
            );
            streamService.publishAnnotationRecorded(tenantId, sessionId, annotationPayload);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", annotationPayload));
        } catch (EmptyResultDataAccessException ignored) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CTG monitoring session not found"));
        }
    }

    record CreateFetalMonitoringSessionRequest(
            String patientId,
            String encounterId,
            String startedAt,
            String startedBy,
            String deviceId,
            String monitoringMode,
            Integer baselineFhrBpm,
            Integer baselineMaternalHrBpm,
            String summaryNotes
    ) {}

    record RecordTraceChunkRequest(
            String channel,
            String startedAt,
            BigDecimal sampleRateHz,
            String unit,
            List<BigDecimal> samples,
            String capturedBy,
            String deviceId,
            String notes
    ) {}

    record RecordCtgAnnotationRequest(
            String recordedAt,
            String category,
            String channel,
            BigDecimal sampleOffsetSec,
            String value,
            String severity,
            String notes,
            String recordedBy
    ) {}

    private Map<String, Object> buildSessionResource(
            Map<String, Object> session,
            List<Map<String, Object>> annotations,
            List<Map<String, Object>> chunks
    ) {
        Map<String, Object> resource = new LinkedHashMap<>(session);
        resource.put("annotations", annotations);

        List<String> activeChannels = chunks.stream()
                .map(chunk -> String.valueOf(chunk.get("channel")))
                .distinct()
                .toList();

        List<Map<String, Object>> chunkSummaries = chunks.stream()
                .map(chunk -> Map.<String, Object>of(
                        "id", chunk.get("id"),
                        "channel", chunk.get("channel"),
                        "started_at", chunk.get("started_at"),
                        "ended_at", chunk.get("ended_at"),
                        "sample_count", chunk.get("sample_count"),
                        "sample_rate_hz", chunk.get("sample_rate_hz"),
                        "alert_flags", chunk.get("alert_flags")
                ))
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("chunk_count", chunks.size());
        summary.put("annotation_count", annotations.size());
        summary.put("active_channels", activeChannels);
        summary.put("latest_chunk_started_at", chunks.isEmpty() ? null : chunks.get(chunks.size() - 1).get("started_at"));
        summary.put("latest_annotation_at", annotations.isEmpty() ? null : annotations.get(annotations.size() - 1).get("recorded_at"));
        summary.put("chunk_summaries", chunkSummaries);
        resource.put("summary", summary);
        return resource;
    }

    private List<Map<String, Object>> loadChunks(
            UUID sessionId,
            String channel,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ctg_trace_chunks WHERE session_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(sessionId);
        if (channel != null) {
            sql.append(" AND channel = ?");
            args.add(channel);
        }
        if (from != null) {
            sql.append(" AND started_at >= ?");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND started_at <= ?");
            args.add(to);
        }
        sql.append(" ORDER BY started_at ASC");
        return jdbc.queryForList(sql.toString(), args.toArray()).stream().map(this::enrichChunk).toList();
    }

    private List<Map<String, Object>> loadAnnotations(UUID sessionId) {
        return jdbc.queryForList(
                "SELECT * FROM ctg_annotations WHERE session_id = ? ORDER BY recorded_at ASC",
                sessionId
        );
    }

    private Map<String, Object> enrichChunk(Map<String, Object> row) {
        Map<String, Object> enriched = new LinkedHashMap<>(row);
        List<BigDecimal> samples = parseSamples(row.get("samples_json"));
        enriched.put("samples", samples);

        if (enriched.get("sample_count") == null) {
            enriched.put("sample_count", samples.size());
        }
        if (enriched.get("duration_seconds") == null && enriched.get("sample_rate_hz") != null && !samples.isEmpty()) {
            BigDecimal duration = BigDecimal.valueOf(samples.size())
                    .divide(decimalValue(enriched.get("sample_rate_hz")), 3, RoundingMode.HALF_UP);
            enriched.put("duration_seconds", duration);
        }

        if (!samples.isEmpty()) {
            BigDecimal min = samples.getFirst();
            BigDecimal max = samples.getFirst();
            BigDecimal total = BigDecimal.ZERO;
            for (BigDecimal sample : samples) {
                if (sample.compareTo(min) < 0) min = sample;
                if (sample.compareTo(max) > 0) max = sample;
                total = total.add(sample);
            }
            enriched.put("min_value", min);
            enriched.put("max_value", max);
            enriched.put("avg_value", total.divide(BigDecimal.valueOf(samples.size()), 3, RoundingMode.HALF_UP));
        } else {
            enriched.put("min_value", null);
            enriched.put("max_value", null);
            enriched.put("avg_value", null);
        }

        String channel = String.valueOf(enriched.get("channel"));
        List<String> alertFlags = new ArrayList<>();
        if ("FHR".equals(channel)) {
            if (samples.stream().anyMatch(v -> v.compareTo(BigDecimal.valueOf(110)) < 0)) {
                alertFlags.add("FHR_LOW");
            }
            if (samples.stream().anyMatch(v -> v.compareTo(BigDecimal.valueOf(160)) > 0)) {
                alertFlags.add("FHR_HIGH");
            }
        } else if ("MATERNAL_HR".equals(channel)) {
            if (samples.stream().anyMatch(v -> v.compareTo(BigDecimal.valueOf(50)) < 0 || v.compareTo(BigDecimal.valueOf(120)) > 0)) {
                alertFlags.add("MATERNAL_HR_ABNORMAL");
            }
        } else if ("TOCO".equals(channel)) {
            if (samples.stream().anyMatch(v -> v.compareTo(BigDecimal.valueOf(90)) > 0)) {
                alertFlags.add("TOCO_HIGH");
            }
        }
        enriched.put("alert_flags", alertFlags);
        return enriched;
    }

    private Map<String, Object> sessionRow(
            UUID id,
            CreateFetalMonitoringSessionRequest request,
            OffsetDateTime startedAt
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("patient_id", request.patientId());
        row.put("encounter_id", request.encounterId());
        row.put("status", "ACTIVE");
        row.put("started_at", startedAt);
        row.put("started_by", defaultString(request.startedBy(), ""));
        row.put("closed_at", null);
        row.put("closed_by", null);
        row.put("device_id", request.deviceId());
        row.put("monitoring_mode", defaultString(request.monitoringMode(), "EXTERNAL_CTG"));
        row.put("baseline_fhr_bpm", request.baselineFhrBpm());
        row.put("baseline_maternal_hr_bpm", request.baselineMaternalHrBpm());
        row.put("summary_notes", request.summaryNotes());
        return row;
    }

    private Map<String, Object> chunkRow(
            UUID chunkId,
            UUID sessionId,
            Map<String, Object> session,
            RecordTraceChunkRequest request,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            int sampleCount,
            BigDecimal durationSeconds
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", chunkId);
        row.put("session_id", sessionId);
        row.put("patient_id", session.get("patient_id"));
        row.put("encounter_id", session.get("encounter_id"));
        row.put("channel", request.channel().trim().toUpperCase());
        row.put("started_at", startedAt);
        row.put("ended_at", endedAt);
        row.put("sample_rate_hz", request.sampleRateHz());
        row.put("sample_count", sampleCount);
        row.put("duration_seconds", durationSeconds);
        row.put("unit", defaultString(request.unit(), defaultUnitForChannel(request.channel())));
        row.put("samples_json", request.samples());
        row.put("captured_by", defaultString(request.capturedBy(), ""));
        row.put("device_id", coalesce(request.deviceId(), session.get("device_id")));
        row.put("notes", request.notes());
        return row;
    }

    private Map<String, Object> annotationRow(
            UUID annotationId,
            UUID sessionId,
            Map<String, Object> session,
            RecordCtgAnnotationRequest request,
            OffsetDateTime recordedAt
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", annotationId);
        row.put("session_id", sessionId);
        row.put("patient_id", session.get("patient_id"));
        row.put("encounter_id", session.get("encounter_id"));
        row.put("recorded_at", recordedAt);
        row.put("category", request.category().trim().toUpperCase());
        row.put("channel", request.channel() == null || request.channel().isBlank() ? null : request.channel().trim().toUpperCase());
        row.put("sample_offset_sec", request.sampleOffsetSec());
        row.put("value", request.value());
        row.put("severity", request.severity() == null || request.severity().isBlank() ? null : request.severity().trim().toUpperCase());
        row.put("notes", request.notes());
        row.put("recorded_by", defaultString(request.recordedBy(), ""));
        return row;
    }

    private List<BigDecimal> parseSamples(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> rawList) {
            return rawList.stream().map(this::decimalValue).toList();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), SAMPLE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to parse CTG samples_json", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize CTG payload", e);
        }
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String defaultUnitForChannel(String channel) {
        String normalized = channel == null ? "" : channel.trim().toUpperCase();
        return switch (normalized) {
            case "TOCO" -> "MMHG";
            default -> "BPM";
        };
    }

    private static OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value);
    }

    private static OffsetDateTime parseDateTimeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    private static UUID uuidValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static Object coalesce(Object preferred, Object fallback) {
        return preferred != null ? preferred : fallback;
    }
}
