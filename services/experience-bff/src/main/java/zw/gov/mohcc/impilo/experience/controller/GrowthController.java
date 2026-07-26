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
import zw.gov.mohcc.impilo.experience.service.GrowthReferenceCurveService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Growth measurements. PCT is the system of record and stamps every z-score at write; this
 * layer shapes those stamps for the experience and serves the reference curves a chart
 * needs to plot them against.
 *
 * <p>Nothing here recomputes a score. The BFF used to carry a second copy of the WHO
 * arithmetic and re-score each measurement on the way past, which produced a different
 * answer from the stored one for every preterm infant — the stored score used corrected
 * age and the echo did not. A second opinion nobody asked for is not a feature.</p>
 */
@RestController
@RequestMapping("/internal/v1/growth")
public class GrowthController {

    private static final Logger log = LoggerFactory.getLogger(GrowthController.class);

    private final GrowthReferenceCurveService referenceCurves;
    private final PctServiceClient pctClient;

    public GrowthController(GrowthReferenceCurveService referenceCurves, PctServiceClient pctClient) {
        this.referenceCurves = referenceCurves;
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
                    "data", toResources(pctData),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // An empty 200 here reads as "this child has never been weighed", which is a finding
            // in its own right and would be a fabricated one. Fail loudly instead.
            log.error("PCT listGrowthMeasurements failed for patient={}: {}", patientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "growth_history_unavailable",
                    "message", "Growth measurements could not be retrieved. Do not treat this as an "
                               + "absence of measurements.",
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

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", toResource(created),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /**
     * The reference curves for the standard this child is currently read against, so a
     * chart can plot their measurements against the right chart rather than a plausible
     * one. Which standard that is comes from the engine, not from the caller.
     */
    @GetMapping("/reference-curves")
    public ResponseEntity<Map<String, Object>> referenceCurves(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId,
            @RequestParam(name = "indicator", defaultValue = "weight_for_age") String indicator) {

        Map<String, Object> meta = Map.of("request_id", requestId, "correlation_id", correlationId);
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "patient_id_required",
                    "message", "Reference curves are specific to a child's age, sex and gestation.",
                    "meta", meta));
        }
        try {
            GrowthReferenceCurveService.PatientGrowthContext context = loadContext(patientId);
            return ResponseEntity.ok(Map.of(
                    "data", referenceCurves.curvesFor(context, indicator),
                    "meta", meta));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "unknown_indicator",
                    "message", e.getMessage(),
                    "meta", meta));
        } catch (Exception e) {
            // Same rule as the history: a chart drawn without its curves would look like a
            // chart while answering none of the questions a chart is for.
            log.error("Growth reference curves failed for patient={}: {}", patientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "growth_reference_unavailable",
                    "message", "The growth standard for this child could not be resolved. Do not plot "
                               + "measurements against an assumed standard.",
                    "meta", meta));
        }
    }

    // --- shaping ---------------------------------------------------------------------

    private List<Map<String, Object>> toResources(JsonNode rows) {
        List<Map<String, Object>> resources = new ArrayList<>();
        if (rows == null || !rows.isArray()) {
            return resources;
        }
        for (JsonNode row : rows) {
            resources.add(toResource(row));
        }
        return resources;
    }

    /**
     * PCT speaks a flat snake_case row; the experience speaks resources with the scoring
     * outcome gathered under {@code derived}. The two were never reconciled, so every read
     * arrived in a shape the client could not walk.
     */
    private Map<String, Object> toResource(JsonNode row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (row == null || row.isNull()) {
            return resource("", attributes);
        }

        attributes.put("patient_id", text(row, "patient_id"));
        attributes.put("encounter_id", text(row, "encounter_id"));
        attributes.put("measured_at", text(row, "measured_at"));
        attributes.put("recorded_by", text(row, "recorded_by"));
        attributes.put("weight_kg", number(row, "weight_kg"));
        attributes.put("length_cm", number(row, "length_cm"));
        attributes.put("height_cm", number(row, "height_cm"));
        attributes.put("head_circumference_cm", number(row, "head_circumference_cm"));
        attributes.put("muac_cm", number(row, "muac_cm"));
        attributes.put("bmi", number(row, "bmi"));
        attributes.put("oedema", text(row, "oedema"));
        attributes.put("measurement_mode", text(row, "measurement_mode"));
        attributes.put("nutrition_status", text(row, "nutrition_status"));
        attributes.put("notes", text(row, "notes"));
        attributes.put("created_at", text(row, "created_at"));

        Map<String, Object> derived = new LinkedHashMap<>();
        derived.put("age_days", number(row, "age_days"));
        derived.put("corrected_age_days", number(row, "corrected_age_days"));
        derived.put("corrected_age_applied", row.path("corrected_age_applied").asBoolean(false));
        derived.put("gestational_age_weeks", number(row, "gestational_age_weeks"));
        derived.put("gestational_age_source", text(row, "gestational_age_source"));
        derived.put("postmenstrual_age_weeks", number(row, "postmenstrual_age_weeks"));

        String standardId = text(row, "growth_standard");
        derived.put("standard", standardId);
        derived.put("standard_label", referenceCurves.labelOf(standardId));
        derived.put("standard_attribution", referenceCurves.attributionOf(standardId));
        derived.put("engine_version", text(row, "growth_engine_version"));
        // Why something was not scored travels with the record. A missing z-score that
        // explains itself is a gap; one that does not is indistinguishable from normal.
        derived.put("scoring_note", text(row, "scoring_note"));
        derived.put("scoring_gaps", text(row, "scoring_gaps"));

        derived.put("normalized_stature_cm", number(row, "length_cm") != null
                ? number(row, "length_cm") : number(row, "height_cm"));
        derived.put("normalized_stature_mode", text(row, "measurement_mode"));
        derived.put("body_mass_index", number(row, "bmi"));
        derived.put("weight_for_age", scoreOf(row, "weight_for_age_z"));
        derived.put("length_height_for_age", scoreOf(row, "length_height_for_age_z"));
        derived.put("body_mass_index_for_age", scoreOf(row, "bmi_for_age_z"));
        derived.put("head_circumference_for_age", scoreOf(row, "head_circumference_for_age_z"));

        attributes.put("derived", derived);

        String id = text(row, "growth_id");
        return resource(id == null ? "" : id, attributes);
    }

    private static Map<String, Object> resource(String id, Map<String, Object> attributes) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", id);
        resource.put("type", "growth_measurement");
        resource.put("attributes", attributes);
        return resource;
    }

    private Map<String, Object> scoreOf(JsonNode row, String field) {
        JsonNode node = row.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        double z = node.asDouble();
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("z_score", z);
        score.put("percentile", referenceCurves.percentileOf(z));
        return score;
    }

    private GrowthReferenceCurveService.PatientGrowthContext loadContext(String patientId) {
        JsonNode summary = pctClient.getPatientHealthSummary(patientId);
        String dateOfBirth = null;
        String sex = null;
        if (summary != null && !summary.isNull()) {
            dateOfBirth = firstText(summary, "dateOfBirth", "birthDate", "date_of_birth");
            sex = firstText(summary, "sex", "gender");
        }

        // Gestational age is read back off the growth rows, where PCT resolved it once at
        // write from the birth record. Asking the experience layer to establish it a second
        // time would be a second answer to a question the record already settled.
        Integer gestationalAgeWeeks = null;
        JsonNode rows = pctClient.listGrowthMeasurements(patientId);
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                JsonNode ga = row.get("gestational_age_weeks");
                if (ga != null && !ga.isNull()) {
                    gestationalAgeWeeks = ga.asInt();
                    break;
                }
            }
        }
        return new GrowthReferenceCurveService.PatientGrowthContext(dateOfBirth, sex, gestationalAgeWeeks);
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static BigDecimal number(JsonNode row, String field) {
        JsonNode node = row.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return node.isTextual() ? new BigDecimal(node.asText().trim()) : node.decimalValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
