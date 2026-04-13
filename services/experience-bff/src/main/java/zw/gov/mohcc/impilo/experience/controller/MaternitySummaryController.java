package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

@RestController
@RequestMapping("/internal/v1/maternity")
public class MaternitySummaryController {

    private static final Logger log = LoggerFactory.getLogger(MaternitySummaryController.class);
    private static final TypeReference<List<BigDecimal>> SAMPLE_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final PctServiceClient pctClient;

    public MaternitySummaryController(ObjectMapper objectMapper,
                                      PctServiceClient pctClient) {
        this.objectMapper = objectMapper;
        this.pctClient = pctClient;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(required = false) String encounterId
    ) {
        // STRANGLER: delegate to PctServiceClient
        try {
            JsonNode pctData = pctClient.getMaternitySummary(patientId, encounterId);
            if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData));
        } catch (Exception e) {
            log.warn("PCT getMaternitySummary failed, falling back to local: {}", e.getMessage());
        }
        // STRANGLER: migrated to PctServiceClient — fallback to local JDBC
        Map<String, Object> activePartograph = loadActivePartograph(tenantId, patientId, encounterId);
        Map<String, Object> latestPartographPoint = activePartograph == null
                ? null
                : loadLatestPartographPoint(uuidValue(activePartograph.get("id")));

        Map<String, Object> activeCtg = loadActiveCtg(tenantId, patientId, encounterId);
        Map<String, Object> latestCtgChunk = activeCtg == null
                ? null
                : loadLatestCtgChunk(uuidValue(activeCtg.get("id")));
        Map<String, Object> latestCtgAnnotation = activeCtg == null
                ? null
                : loadLatestCtgAnnotation(uuidValue(activeCtg.get("id")));

        Map<String, Object> latestLabourEntry = loadLatestLabourEntry(tenantId, patientId, encounterId);

        Set<String> alertFlags = new LinkedHashSet<>();
        collectAlertFlags(alertFlags, latestPartographPoint);
        collectAlertFlags(alertFlags, latestCtgChunk);
        collectAlertFlags(alertFlags, latestLabourEntry);

        List<String> monitoringModes = new ArrayList<>();
        if (activePartograph != null) {
            monitoringModes.add("PARTOGRAPH");
        }
        if (activeCtg != null) {
            monitoringModes.add("CTG");
        }
        if (latestLabourEntry != null) {
            monitoringModes.add("LABOUR_MONITORING_COMPAT");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patient_id", patientId);
        data.put("encounter_id", encounterId);
        data.put("active_partograph_session", activePartograph);
        data.put("latest_partograph_point", latestPartographPoint);
        data.put("active_ctg_session", activeCtg);
        data.put("latest_ctg_chunk", latestCtgChunk);
        data.put("latest_ctg_annotation", latestCtgAnnotation);
        data.put("latest_labour_monitoring_entry", latestLabourEntry);
        data.put("current_alert_flags", List.copyOf(alertFlags));
        data.put("monitoring_modes", monitoringModes);
        data.put("workflow_state", deriveWorkflowState(activePartograph, activeCtg, latestLabourEntry));

        return ResponseEntity.ok(Map.of("data", data));
    }

    private Map<String, Object> loadActivePartograph(String tenantId, String patientId, String encounterId) {
        List<Map<String, Object>> rows = encounterId == null || encounterId.isBlank()

        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> session = new LinkedHashMap<>(rows.getFirst());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("point_count", points.size());
        if (!points.isEmpty()) {
            Map<String, Object> latest = enrichLabourStyleObservation(points.getLast());
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
        session.put("summary", summary);
        return session;
    }

    private Map<String, Object> loadLatestPartographPoint(UUID sessionId) {
        return rows.isEmpty() ? null : enrichLabourStyleObservation(rows.getFirst());
    }

    private Map<String, Object> loadActiveCtg(String tenantId, String patientId, String encounterId) {
        List<Map<String, Object>> rows = encounterId == null || encounterId.isBlank()

        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> session = new LinkedHashMap<>(rows.getFirst());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("chunk_count", chunkRows.size());
        summary.put("annotation_count", annotationRows.size());
        summary.put("active_channels", chunkRows.stream().map(row -> String.valueOf(row.get("channel"))).distinct().toList());
        summary.put("latest_chunk_started_at", chunkRows.isEmpty() ? null : chunkRows.getLast().get("started_at"));
        summary.put("latest_annotation_at", annotationRows.isEmpty() ? null : annotationRows.getLast().get("recorded_at"));
        session.put("summary", summary);
        return session;
    }

    private Map<String, Object> loadLatestCtgChunk(UUID sessionId) {
        return rows.isEmpty() ? null : enrichCtgChunk(rows.getFirst());
    }

    private Map<String, Object> loadLatestCtgAnnotation(UUID sessionId) {
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
    }

    private Map<String, Object> loadLatestLabourEntry(String tenantId, String patientId, String encounterId) {
        List<Map<String, Object>> rows = encounterId == null || encounterId.isBlank()
        return rows.isEmpty() ? null : enrichLabourStyleObservation(rows.getFirst());
    }

    private Map<String, Object> enrichLabourStyleObservation(Map<String, Object> row) {
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

    private Map<String, Object> enrichCtgChunk(Map<String, Object> row) {
        Map<String, Object> enriched = new LinkedHashMap<>(row);
        List<BigDecimal> samples = parseSamples(row.get("samples_json"));
        enriched.put("samples", samples);
        enriched.put("sample_count", row.get("sample_count") != null ? row.get("sample_count") : samples.size());

        if (row.get("duration_seconds") == null && row.get("sample_rate_hz") != null && !samples.isEmpty()) {
            enriched.put(
                    "duration_seconds",
                    BigDecimal.valueOf(samples.size()).divide(decimalValue(row.get("sample_rate_hz")), 3, RoundingMode.HALF_UP)
            );
        }

        List<String> alertFlags = new ArrayList<>();
        String channel = String.valueOf(enriched.get("channel"));
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

        if (!samples.isEmpty()) {
            BigDecimal min = samples.getFirst();
            BigDecimal max = samples.getFirst();
            BigDecimal total = BigDecimal.ZERO;
            for (BigDecimal sample : samples) {
                if (sample.compareTo(min) < 0) {
                    min = sample;
                }
                if (sample.compareTo(max) > 0) {
                    max = sample;
                }
                total = total.add(sample);
            }
            enriched.put("min_value", min);
            enriched.put("max_value", max);
            enriched.put("avg_value", total.divide(BigDecimal.valueOf(samples.size()), 3, RoundingMode.HALF_UP));
        }
        return enriched;
    }

    private void collectAlertFlags(Set<String> target, Map<String, Object> row) {
        if (row == null) {
            return;
        }
        Object alertValue = row.get("alert_flags");
        if (alertValue instanceof List<?> flags) {
            for (Object flag : flags) {
                target.add(String.valueOf(flag));
            }
        }
    }

    private String deriveWorkflowState(
            Map<String, Object> activePartograph,
            Map<String, Object> activeCtg,
            Map<String, Object> latestLabourEntry
    ) {
        if (activePartograph != null || activeCtg != null) {
            return "ACTIVE_MONITORING";
        }
        if (latestLabourEntry != null) {
            return "COMPATIBILITY_ONLY";
        }
        return "NO_ACTIVE_MONITORING";
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

    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
