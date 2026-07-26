package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/labour-monitoring")
public class LabourMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(LabourMonitoringController.class);

    private final PctServiceClient pctClient;

    public LabourMonitoringController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLabourMonitoring(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String patientId,
            @RequestParam(required = false) String encounterId
    ) {
        try {
            JsonNode pctData = pctClient.listLabourMonitoring(patientId, encounterId);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // A failed call is not an empty labour record. Returning 200 with an empty list here
            // told the ward that a woman in labour had no observations, which is the most
            // reassuring thing this endpoint can say and was said precisely when the system knew
            // least. "PCT has no rows" and "PCT could not be reached" must not share a response.
            log.error("PCT listLabourMonitoring failed for patient={}: {}", patientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "labour_monitoring_unavailable",
                    "message", "Labour observations could not be retrieved. Do not treat this as an "
                               + "absence of observations.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> recordLabourMonitoring(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody RecordLabourMonitoringRequest request
    ) {
        if (request.patientId() == null || request.patientId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "patientId is required"));
        }
        if (!hasClinicalValue(request)) {
            return ResponseEntity.badRequest().body(Map.of("error", "At least one labour monitoring measurement is required"));
        }

        Map<String, Object> pctBody = new LinkedHashMap<>();
        pctBody.put("patientId", request.patientId());
        pctBody.put("encounterId", request.encounterId());
        pctBody.put("phase", request.phase());
        pctBody.put("recordedAt", request.recordedAt());
        pctBody.put("recordedBy", request.recordedBy());
        pctBody.put("fetalHeartRateBpm", request.fetalHeartRateBpm());
        pctBody.put("cervicalDilationCm", request.cervicalDilationCm());
        pctBody.put("contractionFrequency10Min", request.contractionFrequency10Min());
        pctBody.put("contractionDurationSec", request.contractionDurationSec());
        pctBody.put("fetalDescentFifths", request.fetalDescentFifths());
        pctBody.put("maternalPulseBpm", request.maternalPulseBpm());
        pctBody.put("systolicBp", request.systolicBp());
        pctBody.put("diastolicBp", request.diastolicBp());
        pctBody.put("temperatureC", request.temperatureC());
        pctBody.put("liquor", request.liquor());
        pctBody.put("moulding", request.moulding());
        pctBody.put("caput", request.caput());
        pctBody.put("oxytocinRateMiuMin", request.oxytocinRateMiuMin());
        pctBody.put("urineVolumeMl", request.urineVolumeMl());
        pctBody.put("urineProtein", request.urineProtein());
        pctBody.put("urineAcetone", request.urineAcetone());
        pctBody.put("maternalCondition", request.maternalCondition());
        pctBody.put("notes", request.notes());

        JsonNode created = pctClient.recordLabourMonitoring(pctBody);
        log.info("PCT labour monitoring recorded for patient={}", request.patientId());

        Map<String, Object> meta = new LinkedHashMap<>(Map.of(
                "request_id", requestId,
                "correlation_id", correlationId));
        meta.put("labour_derived", enrichRow(requestToDerivedRow(request)));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", created != null ? created : Map.of(),
                "meta", meta));
    }

    private static Map<String, Object> requestToDerivedRow(RecordLabourMonitoringRequest request) {
        Map<String, Object> row = new LinkedHashMap<>();
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
