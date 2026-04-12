package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/internal/v1/labour-monitoring")
public class LabourMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(LabourMonitoringController.class);

    private final JdbcTemplate jdbc; // TODO: remove after verification
    private final PctServiceClient pctClient;

    public LabourMonitoringController(JdbcTemplate jdbc, PctServiceClient pctClient) {
        this.jdbc = jdbc;
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLabourMonitoring(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(required = false) String encounterId
    ) {
        // STRANGLER: delegate to PctServiceClient
        try {
            JsonNode pctData = pctClient.listLabourMonitoring(patientId, encounterId);
            if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData));
        } catch (Exception e) {
            log.warn("PCT listLabourMonitoring failed, falling back to local: {}", e.getMessage());
        }
        // STRANGLER: migrated to PctServiceClient — fallback to local JDBC
        List<Map<String, Object>> rows = encounterId == null || encounterId.isBlank()
                ? jdbc.queryForList(
                "SELECT * FROM labour_monitoring_entries WHERE tenant_id = ? AND patient_id = ?::uuid ORDER BY recorded_at DESC LIMIT 50",
                tenantId, patientId
        )
                : jdbc.queryForList(
                "SELECT * FROM labour_monitoring_entries WHERE tenant_id = ? AND patient_id = ?::uuid AND encounter_id = ?::uuid ORDER BY recorded_at DESC LIMIT 50",
                tenantId, patientId, encounterId
        );
        return ResponseEntity.ok(Map.of("data", rows.stream().map(this::enrichRow).toList()));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> recordLabourMonitoring(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody RecordLabourMonitoringRequest request
    ) {
        if (request.patientId() == null || request.patientId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "patientId is required"));
        }
        if (!hasClinicalValue(request)) {
            return ResponseEntity.badRequest().body(Map.of("error", "At least one labour monitoring measurement is required"));
        }

        // STRANGLER: delegate to PctServiceClient first
        try {
            Map<String, Object> pctBody = new LinkedHashMap<>();
            pctBody.put("patientId", request.patientId());
            pctBody.put("encounterId", request.encounterId());
            pctBody.put("phase", request.phase());
            pctBody.put("recordedAt", request.recordedAt());
            pctBody.put("recordedBy", request.recordedBy());
            pctBody.put("fetalHeartRateBpm", request.fetalHeartRateBpm());
            pctBody.put("cervicalDilationCm", request.cervicalDilationCm());
            pctClient.recordLabourMonitoring(pctBody);
            log.info("PCT labour monitoring recorded for patient={}", request.patientId());
        } catch (Exception e) {
            log.warn("PCT recordLabourMonitoring failed (non-blocking): {}", e.getMessage());
        }

        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        UUID id = UUID.randomUUID();
        OffsetDateTime recordedAt = parseRecordedAt(request.recordedAt());
        jdbc.update("""
                        INSERT INTO labour_monitoring_entries (
                            id, tenant_id, patient_id, encounter_id, phase, recorded_at, recorded_by,
                            fetal_heart_rate_bpm, contraction_frequency_10min, contraction_duration_sec,
                            cervical_dilation_cm, fetal_descent_fifths,
                            maternal_pulse_bpm, systolic_bp, diastolic_bp, temperature_c,
                            liquor, moulding, caput, oxytocin_rate_miu_min,
                            urine_volume_ml, urine_protein, urine_acetone,
                            maternal_condition, notes
                        ) VALUES (
                            ?, ?, ?::uuid, ?::uuid, ?, ?, ?,
                            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                        )
                        """,
                id,
                tenantId,
                request.patientId(),
                request.encounterId(),
                defaultString(request.phase(), "ACTIVE_LABOUR"),
                recordedAt,
                defaultString(request.recordedBy(), ""),
                request.fetalHeartRateBpm(),
                request.contractionFrequency10Min(),
                request.contractionDurationSec(),
                request.cervicalDilationCm(),
                request.fetalDescentFifths(),
                request.maternalPulseBpm(),
                request.systolicBp(),
                request.diastolicBp(),
                request.temperatureC(),
                request.liquor(),
                request.moulding(),
                request.caput(),
                request.oxytocinRateMiuMin(),
                request.urineVolumeMl(),
                request.urineProtein(),
                request.urineAcetone(),
                request.maternalCondition(),
                request.notes()
        );

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("patient_id", request.patientId());
        row.put("encounter_id", request.encounterId());
        row.put("phase", defaultString(request.phase(), "ACTIVE_LABOUR"));
        row.put("recorded_at", recordedAt);
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

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", enrichRow(row)));
    }

    record RecordLabourMonitoringRequest(
            String patientId,
            String encounterId,
            String phase,
            String recordedAt,
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
    ) {
    }

    private Map<String, Object> enrichRow(Map<String, Object> row) {
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

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static OffsetDateTime parseRecordedAt(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value);
    }

    private static boolean hasClinicalValue(RecordLabourMonitoringRequest request) {
        return request.fetalHeartRateBpm() != null
                || request.contractionFrequency10Min() != null
                || request.contractionDurationSec() != null
                || request.cervicalDilationCm() != null
                || request.fetalDescentFifths() != null
                || request.maternalPulseBpm() != null
                || request.systolicBp() != null
                || request.diastolicBp() != null
                || request.temperatureC() != null
                || request.liquor() != null
                || request.moulding() != null
                || request.caput() != null
                || request.oxytocinRateMiuMin() != null
                || request.urineVolumeMl() != null
                || request.urineProtein() != null
                || request.urineAcetone() != null
                || request.maternalCondition() != null
                || request.notes() != null;
    }

    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }
}
