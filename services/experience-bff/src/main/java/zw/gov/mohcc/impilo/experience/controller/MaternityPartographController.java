package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/maternity/partograph")
public class MaternityPartographController {

    private static final Logger log = LoggerFactory.getLogger(MaternityPartographController.class);

    private final PctServiceClient pctClient;

    public MaternityPartographController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody CreatePartographSessionRequest request
    ) {
        if (request.patientId() == null || request.patientId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "patientId is required"));
        }

        try {
            Map<String, Object> pctBody = new LinkedHashMap<>();
            pctBody.put("patientId", request.patientId());
            pctBody.put("encounterId", request.encounterId());
            pctBody.put("labourPhase", request.labourPhase());
            pctBody.put("startedAt", request.startedAt());
            pctBody.put("startedBy", request.startedBy());
            pctBody.put("outcome", request.outcome());
            pctBody.put("summaryNotes", request.summaryNotes());
            pctClient.createPartographSession(pctBody);
            log.info("PCT partograph session created for patient={}", request.patientId());
        } catch (Exception e) {
            log.warn("PCT createPartographSession failed (non-blocking): {}", e.getMessage());
        }

        UUID id = UUID.randomUUID();
        OffsetDateTime startedAt = parseDateTime(request.startedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", buildSessionResource(
                sessionRow(id, request, startedAt),
                List.of()
        )));
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<Map<String, Object>> getActiveSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(required = false) String encounterId
    ) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID sessionId
    ) {
        try {
            return ResponseEntity.ok(Map.of("data", buildSessionResource(session, loadPoints(sessionId))));
        } catch (EmptyResultDataAccessException ignored) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Partograph session not found"));
        }
    }

    @PostMapping("/sessions/{sessionId}/points")
    public ResponseEntity<Map<String, Object>> addPoint(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID sessionId,
            @RequestBody RecordPartographPointRequest request
    ) {
        try {
            if (!"ACTIVE".equals(String.valueOf(session.get("status")))) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Partograph session is not active"));
            }

            UUID pointId = UUID.randomUUID();
            OffsetDateTime observedAt = parseDateTime(request.observedAt());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", enrichPoint(pointRow(
                    pointId,
                    sessionId,
                    session,
                    request,
                    observedAt
            ))));
        } catch (EmptyResultDataAccessException ignored) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Partograph session not found"));
        }
    }

    @PatchMapping("/sessions/{sessionId}/close")
    public ResponseEntity<Map<String, Object>> closeSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID sessionId,
            @RequestBody(required = false) ClosePartographSessionRequest request
    ) {
        try {
            OffsetDateTime closedAt = parseDateTime(request != null ? request.closedAt() : null);
            String closedBy = request != null ? request.closedBy() : null;
            String outcome = request != null ? request.outcome() : null;
            String summaryNotes = request != null ? request.summaryNotes() : null;

            return ResponseEntity.ok(Map.of("data", buildSessionResource(session, loadPoints(sessionId))));
        } catch (EmptyResultDataAccessException ignored) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Partograph session not found"));
        }
    }

    record CreatePartographSessionRequest(
            String patientId,
            String encounterId,
            String labourPhase,
            String startedAt,
            String startedBy,
            String outcome,
            String summaryNotes
    ) {}

    record RecordPartographPointRequest(
            String observedAt,
            String recordedBy,
            Integer fetalHeartRateBpm,
            Integer contractionFrequency10Min,
            Integer contractionDurationSec,
            BigDecimal cervicalDilationCm,
            Integer fetalDescentFifths,
            Integer maternalPulseBpm,
            Integer systolicBp,
            Integer diastolicBp,
            BigDecimal temperatureC,
            String liquor,
            String moulding,
            String caput,
            BigDecimal oxytocinRateMiuMin,
            Integer urineVolumeMl,
            String urineProtein,
            String urineAcetone,
            String maternalCondition,
            String notes
    ) {}

    record ClosePartographSessionRequest(
            String closedAt,
            String closedBy,
            String outcome,
            String summaryNotes
    ) {}

    private List<Map<String, Object>> loadPoints(UUID sessionId) {
    }

    private Map<String, Object> buildSessionResource(Map<String, Object> session, List<Map<String, Object>> points) {
        Map<String, Object> resource = new LinkedHashMap<>(session);
        resource.put("points", points);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("point_count", points.size());
        if (!points.isEmpty()) {
            Map<String, Object> latest = points.get(points.size() - 1);
            summary.put("latest_observed_at", latest.get("observed_at"));
            summary.put("latest_alert_flags", latest.get("alert_flags"));
            summary.put("latest_cervical_dilation_cm", latest.get("cervical_dilation_cm"));
            summary.put("latest_fetal_heart_rate_bpm", latest.get("fetal_heart_rate_bpm"));
        } else {
            summary.put("latest_observed_at", null);
            summary.put("latest_alert_flags", List.of());
            summary.put("latest_cervical_dilation_cm", null);
            summary.put("latest_fetal_heart_rate_bpm", null);
        }
        resource.put("summary", summary);
        return resource;
    }

    private Map<String, Object> enrichPoint(Map<String, Object> row) {
        Map<String, Object> enriched = new LinkedHashMap<>(row);
        List<String> alertFlags = new ArrayList<>();

        Integer fetalHeartRate = integerValue(row.get("fetal_heart_rate_bpm"));
        if (fetalHeartRate != null && (fetalHeartRate < 110 || fetalHeartRate > 160)) {
            alertFlags.add("FETAL_HEART_ABNORMAL");
        }

        BigDecimal temperature = decimalValue(row.get("temperature_c"));
        if (temperature != null && temperature.compareTo(BigDecimal.valueOf(38.0d)) >= 0) {
            alertFlags.add("MATERNAL_TEMPERATURE_HIGH");
        }

        Integer systolic = integerValue(row.get("systolic_bp"));
        Integer diastolic = integerValue(row.get("diastolic_bp"));
        if ((systolic != null && systolic >= 140) || (diastolic != null && diastolic >= 90)) {
            alertFlags.add("BLOOD_PRESSURE_ELEVATED");
        }

        BigDecimal cervicalDilation = decimalValue(row.get("cervical_dilation_cm"));
        if (cervicalDilation != null && cervicalDilation.compareTo(BigDecimal.valueOf(8.0d)) >= 0) {
            enriched.put("derived_stage", "TRANSITION");
        } else if (cervicalDilation != null && cervicalDilation.compareTo(BigDecimal.valueOf(4.0d)) >= 0) {
            enriched.put("derived_stage", "ACTIVE_LABOUR");
        } else if (cervicalDilation != null) {
            enriched.put("derived_stage", "LATENT_LABOUR");
        } else {
            enriched.put("derived_stage", null);
        }

        enriched.put("alert_flags", alertFlags);
        return enriched;
    }

    private Map<String, Object> sessionRow(UUID id, CreatePartographSessionRequest request, OffsetDateTime startedAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("patient_id", request.patientId());
        row.put("encounter_id", request.encounterId());
        row.put("labour_phase", defaultString(request.labourPhase(), "ACTIVE_LABOUR"));
        row.put("status", "ACTIVE");
        row.put("started_at", startedAt);
        row.put("started_by", defaultString(request.startedBy(), ""));
        row.put("closed_at", null);
        row.put("closed_by", null);
        row.put("outcome", request.outcome());
        row.put("summary_notes", request.summaryNotes());
        return row;
    }

    private Map<String, Object> pointRow(
            UUID pointId,
            UUID sessionId,
            Map<String, Object> session,
            RecordPartographPointRequest request,
            OffsetDateTime observedAt
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", pointId);
        row.put("session_id", sessionId);
        row.put("patient_id", session.get("patient_id"));
        row.put("encounter_id", session.get("encounter_id"));
        row.put("observed_at", observedAt);
        row.put("recorded_by", defaultString(request.recordedBy(), ""));
        row.put("fetal_heart_rate_bpm", request.fetalHeartRateBpm());
        row.put("contraction_frequency_10min", request.contractionFrequency10Min());
        row.put("contraction_duration_sec", request.contractionDurationSec());
        row.put("cervical_dilation_cm", request.cervicalDilationCm());
        row.put("fetal_descent_fifths", request.fetalDescentFifths());
        row.put("maternal_pulse_bpm", request.maternalPulseBpm());
        row.put("systolic_bp", request.systolicBp());
        row.put("diastolic_bp", request.diastolicBp());
        row.put("temperature_c", request.temperatureC());
        row.put("liquor", request.liquor());
        row.put("moulding", request.moulding());
        row.put("caput", request.caput());
        row.put("oxytocin_rate_miu_min", request.oxytocinRateMiuMin());
        row.put("urine_volume_ml", request.urineVolumeMl());
        row.put("urine_protein", request.urineProtein());
        row.put("urine_acetone", request.urineAcetone());
        row.put("maternal_condition", request.maternalCondition());
        row.put("notes", request.notes());
        return row;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value);
    }

    private static UUID uuidValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private static Integer integerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    private static BigDecimal decimalValue(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(value.toString());
    }
}
