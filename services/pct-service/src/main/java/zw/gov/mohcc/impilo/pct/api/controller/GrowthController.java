package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.clinical.GrowthService;
import zw.gov.mohcc.impilo.pct.persistence.entity.GrowthMeasurementEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Growth measurement SoR API. Fulfils the BFF strangler contract that previously 404'd
 * ({@code GET/POST /v1/growth}) — experience-bff and the growth-chart UI have called this
 * since the Wave 4 slice, and the BFF swallowed the failure into an empty list, so
 * measurements appeared to save and were lost. Landing this makes those surfaces real
 * without any change to them.
 *
 * <p>Field names are snake_case to match the contract the BFF already sends.</p>
 */
@RestController
@RequestMapping("/v1/growth")
public class GrowthController {

    private final GrowthService growthService;

    public GrowthController(GrowthService growthService) {
        this.growthService = growthService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid) {
        String subject = subjectCpid != null && !subjectCpid.isBlank() ? subjectCpid : patientId;
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("patient_id is required");
        }
        List<Map<String, Object>> out = growthService.list(subject)
                .stream().map(GrowthController::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    /**
     * The trajectory view: growth faltering shows up in the change between contacts long
     * before any single measurement crosses a classification boundary.
     */
    @GetMapping("/trajectory")
    public ResponseEntity<ApiResponse<Map<String, Object>>> trajectory(
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid) {
        String subject = subjectCpid != null && !subjectCpid.isBlank() ? subjectCpid : patientId;
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("patient_id is required");
        }
        var assessment = growthService.trajectory(subject);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("patient_id", subject);
        out.put("faltering", assessment.faltering());
        out.put("weight_loss_since_last", assessment.weightLossSinceLast());
        out.put("z_score_change", assessment.zScoreChange());
        out.put("observations", assessment.observations());
        out.put("data_quality_concerns", assessment.dataQualityConcerns());
        out.put("content_version", assessment.contentVersion());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> record(@RequestBody Map<String, Object> body) {
        // Authz: RBAC at ext_authz; subject relationship bound in GrowthService via
        // ClinicalAccessGuard, same discipline as the problems and allergy lists.
        GrowthMeasurementEntity row = growthService.record(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(row), correlationId()));
    }

    private static Map<String, Object> toMap(GrowthMeasurementEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("growth_id", row.getGrowthId().toString());
        m.put("patient_id", row.getSubjectCpid());
        m.put("subject_cpid", row.getSubjectCpid());
        m.put("journey_id", row.getJourneyId());
        m.put("encounter_id", row.getEncounterId());
        m.put("measured_at", row.getMeasuredAt() != null ? row.getMeasuredAt().toString() : null);
        m.put("recorded_by", row.getRecordedBy());

        m.put("weight_kg", row.getWeightKg());
        m.put("length_cm", row.getLengthCm());
        m.put("height_cm", row.getHeightCm());
        m.put("head_circumference_cm", row.getHeadCircumferenceCm());
        m.put("muac_cm", row.getMuacCm());
        m.put("bmi", row.getBmi());
        m.put("oedema", row.getOedema());

        m.put("measurement_mode", row.getMeasurementMode());
        m.put("posture", row.getPosture());
        m.put("equipment_ref", row.getEquipmentRef());
        m.put("clothing", row.getClothing());
        m.put("observer_cadre", row.getObserverCadre());
        m.put("measurement_confidence", row.getMeasurementConfidence());

        m.put("age_days", row.getAgeDays());
        m.put("corrected_age_days", row.getCorrectedAgeDays());
        m.put("corrected_age_applied", row.isCorrectedAgeApplied());
        m.put("gestational_age_weeks", row.getGestationalAgeWeeks());
        m.put("gestational_age_source", row.getGestationalAgeSource());
        m.put("postmenstrual_age_weeks", row.getPostmenstrualAgeWeeks());

        m.put("weight_for_age_z", row.getWeightForAgeZ());
        m.put("length_height_for_age_z", row.getLengthHeightForAgeZ());
        m.put("bmi_for_age_z", row.getBmiForAgeZ());
        m.put("head_circumference_for_age_z", row.getHeadCircumferenceForAgeZ());
        m.put("growth_standard", row.getGrowthStandard());
        m.put("growth_engine_version", row.getGrowthEngineVersion());
        m.put("scoring_note", row.getScoringNote());
        m.put("scoring_gaps", row.getScoringGaps());

        m.put("nutrition_status", row.getNutritionStatus());
        m.put("nutrition_content_version", row.getNutritionContentVersion());

        m.put("notes", row.getNotes());
        m.put("created_at", row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        return m;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
