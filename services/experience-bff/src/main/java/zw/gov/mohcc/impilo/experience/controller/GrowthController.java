package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.service.GrowthStandardsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Growth measurements — PCT is source of truth; optional WHO-derived z-scores in {@code meta.growth_standards}.
 */
@RestController
@RequestMapping("/internal/v1/growth")
public class GrowthController {

    private static final Logger log = LoggerFactory.getLogger(GrowthController.class);

    private final GrowthStandardsService growthStandardsService;
    private final PctServiceClient pctClient;

    public GrowthController(GrowthStandardsService growthStandardsService, PctServiceClient pctClient) {
        this.growthStandardsService = growthStandardsService;
        this.pctClient = pctClient;
    }

    public record RecordGrowthRequest(
            @NotBlank String patient_id,
            String encounter_id,
            String recorded_by,
            String measured_at,
            BigDecimal weight_kg,
            BigDecimal length_cm,
            BigDecimal height_cm,
            BigDecimal head_circumference_cm,
            BigDecimal muac_cm,
            String measurement_mode,
            String notes
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listGrowth(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode pctData = pctClient.listGrowthMeasurements(patientId);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT listGrowthMeasurements failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> recordGrowth(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody RecordGrowthRequest request) {

        Map<String, Object> pctBody = new LinkedHashMap<>();
        pctBody.put("patient_id", request.patient_id());
        pctBody.put("encounter_id", request.encounter_id());
        pctBody.put("recorded_by", request.recorded_by());
        pctBody.put("measured_at", request.measured_at());
        pctBody.put("weight_kg", request.weight_kg());
        pctBody.put("length_cm", request.length_cm());
        pctBody.put("height_cm", request.height_cm());
        pctBody.put("head_circumference_cm", request.head_circumference_cm());
        pctBody.put("muac_cm", request.muac_cm());
        pctBody.put("measurement_mode", request.measurement_mode());
        pctBody.put("notes", request.notes());

        JsonNode created = pctClient.recordGrowthMeasurement(pctBody);

        Map<String, Object> meta = new LinkedHashMap<>(Map.of(
                "request_id", requestId,
                "correlation_id", correlationId));

        if (request.measured_at() != null && !request.measured_at().isBlank()) {
            try {
                OffsetDateTime measuredAt = OffsetDateTime.parse(request.measured_at());
                GrowthStandardsService.PatientContext patient = loadPatientContext(request.patient_id());
                BigDecimal bmi = deriveBmi(request.weight_kg(), request.length_cm(), request.height_cm());
                GrowthStandardsService.GrowthMeasurement measurement = new GrowthStandardsService.GrowthMeasurement(
                        measuredAt,
                        request.weight_kg(),
                        request.length_cm(),
                        request.height_cm(),
                        request.head_circumference_cm(),
                        bmi,
                        request.measurement_mode() != null ? request.measurement_mode() : "AUTO");
                GrowthStandardsService.GrowthAssessment assessment = growthStandardsService.assess(patient, measurement);
                meta.put("growth_standards", assessmentToMap(assessment));
            } catch (Exception e) {
                log.debug("WHO growth enrichment skipped: {}", e.getMessage());
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", created != null ? created : Map.of(),
                "meta", meta));
    }

    private GrowthStandardsService.PatientContext loadPatientContext(String patientId) {
        try {
            JsonNode summary = pctClient.getPatientHealthSummary(patientId);
            if (summary == null || summary.isNull()) {
                return new GrowthStandardsService.PatientContext(null, null);
            }
            LocalDate dob = null;
            JsonNode dobNode = summary.get("dateOfBirth");
            if (dobNode == null || dobNode.isNull()) {
                dobNode = summary.get("birthDate");
            }
            if (dobNode != null && !dobNode.isNull() && dobNode.isTextual()) {
                String t = dobNode.asText();
                if (t.length() >= 10) {
                    dob = LocalDate.parse(t.substring(0, 10));
                }
            }
            String sex = summary.path("sex").asText(null);
            if (sex == null || sex.isBlank()) {
                sex = summary.path("gender").asText(null);
            }
            return new GrowthStandardsService.PatientContext(dob, sex);
        } catch (Exception e) {
            log.debug("Could not load patient context from PCT summary: {}", e.getMessage());
            return new GrowthStandardsService.PatientContext(null, null);
        }
    }

    private Map<String, Object> assessmentToMap(GrowthStandardsService.GrowthAssessment assessment) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("age_days", assessment.ageDays());
        m.put("standard", assessment.standard());
        m.put("normalized_stature_cm", assessment.normalizedStatureCm());
        m.put("normalized_stature_mode", assessment.normalizedStatureMode());
        m.put("stature_adjustment_cm", assessment.statureAdjustmentCm());
        m.put("body_mass_index", assessment.bmi());
        m.put("weight_for_age", scoreToMap(assessment.weightForAge()));
        m.put("length_height_for_age", scoreToMap(assessment.lengthHeightForAge()));
        m.put("body_mass_index_for_age", scoreToMap(assessment.bodyMassIndexForAge()));
        m.put("head_circumference_for_age", scoreToMap(assessment.headCircumferenceForAge()));
        return m;
    }

    private Map<String, Object> scoreToMap(GrowthStandardsService.Score score) {
        if (score == null) {
            return null;
        }
        return Map.of(
                "z_score", score.zScore(),
                "percentile", score.percentile());
    }

    private BigDecimal deriveBmi(BigDecimal weightKg, BigDecimal lengthCm, BigDecimal heightCm) {
        BigDecimal stature = lengthCm != null ? lengthCm : heightCm;
        if (weightKg == null || stature == null || stature.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal heightM = stature.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 3, RoundingMode.HALF_UP);
    }
}
