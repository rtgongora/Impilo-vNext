package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/growth")
public class GrowthController {

    private static final Logger log = LoggerFactory.getLogger(GrowthController.class);

    private final JdbcTemplate jdbcTemplate; // TODO: remove after verification
    private final GrowthStandardsService growthStandardsService;
    private final PctServiceClient pctClient;

    public GrowthController(JdbcTemplate jdbcTemplate,
                            GrowthStandardsService growthStandardsService,
                            PctServiceClient pctClient) {
        this.jdbcTemplate = jdbcTemplate;
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

        // STRANGLER: delegate to PctServiceClient
        try {
            JsonNode pctData = pctClient.listGrowthMeasurements(patientId);
            if (pctData != null) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", pctData);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.warn("PCT listGrowthMeasurements failed, falling back to local: {}", e.getMessage());
        }

        // STRANGLER: migrated to PctServiceClient — fallback to local JDBC
        GrowthStandardsService.PatientContext patient = loadPatientContext(tenantId, patientId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, patient_id, encounter_id, measured_at, recorded_by,
                       weight_kg, length_cm, height_cm, head_circumference_cm, muac_cm,
                       bmi, measurement_mode, notes, created_at
                FROM growth_measurements
                WHERE tenant_id = ? AND patient_id = ?::uuid
                ORDER BY measured_at DESC, created_at DESC
                """, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream()
                .map(row -> toResource(row, patient))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> recordGrowth(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody RecordGrowthRequest request) {

        // STRANGLER: delegate to PctServiceClient first
        try {
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
            pctClient.recordGrowthMeasurement(pctBody);
            log.info("PCT growth measurement recorded for patient={}", request.patient_id());
        } catch (Exception e) {
            log.warn("PCT recordGrowthMeasurement failed (non-blocking): {}", e.getMessage());
        }

        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        OffsetDateTime measuredAt = request.measured_at() != null && !request.measured_at().isBlank()
                ? OffsetDateTime.parse(request.measured_at())
                : OffsetDateTime.now();
        BigDecimal bmi = deriveBmi(request.weight_kg(), request.length_cm(), request.height_cm());
        UUID id = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO growth_measurements
                    (id, tenant_id, patient_id, encounter_id, measured_at, recorded_by,
                     weight_kg, length_cm, height_cm, head_circumference_cm, muac_cm,
                     bmi, measurement_mode, notes, created_at)
                VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """,
                id, tenantId, request.patient_id(), request.encounter_id(), measuredAt, request.recorded_by(),
                request.weight_kg(), request.length_cm(), request.height_cm(), request.head_circumference_cm(),
                request.muac_cm(), bmi, request.measurement_mode() != null ? request.measurement_mode() : "AUTO",
                request.notes());

        GrowthStandardsService.PatientContext patient = loadPatientContext(tenantId, request.patient_id());

        Map<String, Object> inserted = new LinkedHashMap<>();
        inserted.put("id", id);
        inserted.put("patient_id", request.patient_id());
        inserted.put("encounter_id", request.encounter_id());
        inserted.put("measured_at", measuredAt);
        inserted.put("recorded_by", request.recorded_by());
        inserted.put("weight_kg", request.weight_kg());
        inserted.put("length_cm", request.length_cm());
        inserted.put("height_cm", request.height_cm());
        inserted.put("head_circumference_cm", request.head_circumference_cm());
        inserted.put("muac_cm", request.muac_cm());
        inserted.put("bmi", bmi);
        inserted.put("measurement_mode", request.measurement_mode() != null ? request.measurement_mode() : "AUTO");
        inserted.put("notes", request.notes());
        inserted.put("created_at", measuredAt);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(inserted, patient));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Map<String, Object> toResource(Map<String, Object> row, GrowthStandardsService.PatientContext patient) {
        OffsetDateTime measuredAt = row.get("measured_at") instanceof OffsetDateTime measured
                ? measured
                : OffsetDateTime.parse(row.get("measured_at").toString());
        GrowthStandardsService.GrowthMeasurement measurement = new GrowthStandardsService.GrowthMeasurement(
                measuredAt,
                asDecimal(row.get("weight_kg")),
                asDecimal(row.get("length_cm")),
                asDecimal(row.get("height_cm")),
                asDecimal(row.get("head_circumference_cm")),
                asDecimal(row.get("bmi")),
                stringValue(row.get("measurement_mode"))
        );
        GrowthStandardsService.GrowthAssessment assessment = growthStandardsService.assess(patient, measurement);

        Map<String, Object> derived = new LinkedHashMap<>();
        derived.put("age_days", assessment.ageDays());
        derived.put("standard", assessment.standard());
        derived.put("normalized_stature_cm", assessment.normalizedStatureCm());
        derived.put("normalized_stature_mode", assessment.normalizedStatureMode());
        derived.put("stature_adjustment_cm", assessment.statureAdjustmentCm());
        derived.put("body_mass_index", assessment.bmi());
        derived.put("weight_for_age", scoreToMap(assessment.weightForAge()));
        derived.put("length_height_for_age", scoreToMap(assessment.lengthHeightForAge()));
        derived.put("body_mass_index_for_age", scoreToMap(assessment.bodyMassIndexForAge()));
        derived.put("head_circumference_for_age", scoreToMap(assessment.headCircumferenceForAge()));

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", stringValue(row.get("patient_id")));
        attributes.put("encounter_id", stringValue(row.get("encounter_id")));
        attributes.put("measured_at", measuredAt);
        attributes.put("recorded_by", stringValue(row.get("recorded_by")));
        attributes.put("weight_kg", asDecimal(row.get("weight_kg")));
        attributes.put("length_cm", asDecimal(row.get("length_cm")));
        attributes.put("height_cm", asDecimal(row.get("height_cm")));
        attributes.put("head_circumference_cm", asDecimal(row.get("head_circumference_cm")));
        attributes.put("muac_cm", asDecimal(row.get("muac_cm")));
        attributes.put("bmi", asDecimal(row.get("bmi")));
        attributes.put("measurement_mode", stringValue(row.get("measurement_mode")));
        attributes.put("notes", stringValue(row.get("notes")));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("derived", derived);

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "GrowthMeasurement");
        resource.put("attributes", attributes);
        return resource;
    }

    private GrowthStandardsService.PatientContext loadPatientContext(String tenantId, String patientId) {
        Map<String, Object> patient = jdbcTemplate.queryForMap("""
                SELECT date_of_birth, sex
                FROM patients
                WHERE tenant_id = ? AND id = ?::uuid
                """, tenantId, patientId);

        LocalDate dateOfBirth = patient.get("date_of_birth") instanceof LocalDate value
                ? value
                : patient.get("date_of_birth") != null ? LocalDate.parse(patient.get("date_of_birth").toString()) : null;
        return new GrowthStandardsService.PatientContext(dateOfBirth, stringValue(patient.get("sex")));
    }

    private Map<String, Object> scoreToMap(GrowthStandardsService.Score score) {
        if (score == null) {
            return null;
        }
        return Map.of(
                "z_score", score.zScore(),
                "percentile", score.percentile()
        );
    }

    private BigDecimal deriveBmi(BigDecimal weightKg, BigDecimal lengthCm, BigDecimal heightCm) {
        BigDecimal stature = lengthCm != null ? lengthCm : heightCm;
        if (weightKg == null || stature == null || stature.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal heightM = stature.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 3, RoundingMode.HALF_UP);
    }

    private BigDecimal asDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString()).setScale(3, RoundingMode.HALF_UP);
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
