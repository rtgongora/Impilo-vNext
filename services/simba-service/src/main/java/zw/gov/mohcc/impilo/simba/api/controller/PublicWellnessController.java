package zw.gov.mohcc.impilo.simba.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.simba.persistence.entity.ScreeningProgrammeEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.ScreeningProgrammeRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public (R0, anonymous) wellness lane — the gateway public-lane {@code Public*Controller} rule.
 * Lets an anonymous citizen, WITHOUT signing in, (1) browse the ACTIVE screening-programme
 * definitions and (2) run a one-off, non-diagnostic health calculator (BMI, blood pressure).
 *
 * <p><b>Compute-only, no persistence — a new anonymous-write-but-stateless lane.</b>
 * The {@code POST /calculate} endpoint is a pure, stateless arithmetic calculator. It reads the
 * request body, computes a result in-controller, and returns it. It touches NO repository and NO
 * entity, opens NO transaction, and writes NOTHING to any store. Per doctrine, anonymous one-off
 * assessments retain no data: the input and the result are discarded at the end of the request.
 * There is deliberately no persistence dependency wired into this controller for the calculator.</p>
 *
 * <p><b>Disclosure-limited GET by construction.</b> The screening-programme response is an
 * allow-listed, PII-free view built from a fresh {@link LinkedHashMap} per programme. Only public-safe
 * descriptive fields are mapped — programmeCode, title, description, ageMin, ageMax, frequencyMonths.
 * Internal identifiers and metadata (id, programmeId, tenantId, status, createdAt) live on the entity
 * but are deliberately NOT mapped. Adding a field is a deliberate disclosure decision.</p>
 *
 * <p>Screening programmes are care-plane, so the read is scoped to the fixed care-plane tenant. A
 * public read must never fail loudly: any error is logged at warn and yields an empty list, never a
 * 500. The programme read reuses exactly the same repository query the internal
 * {@code ScreeningProgrammeController} uses, only with the tenant supplied as a constant rather than
 * from a trust header.</p>
 */
@RestController
@RequestMapping("/v1/public/wellness")
public class PublicWellnessController {

    private static final Logger log = LoggerFactory.getLogger(PublicWellnessController.class);

    /** Care-plane tenant — screening programmes are care-plane. */
    private static final UUID CARE_PLANE_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final ScreeningProgrammeRepository repository;

    public PublicWellnessController(ScreeningProgrammeRepository repository) {
        this.repository = repository;
    }

    /**
     * List ACTIVE screening-programme definitions for the anonymous public. Reuses the exact same
     * read the internal controller uses ({@code findByTenantIdAndStatusOrderByTitleAsc(tenant,
     * "ACTIVE")}), with the care-plane tenant supplied as a constant. Errors yield an empty list.
     */
    @GetMapping("/screening-programmes")
    public List<Map<String, Object>> screeningProgrammes() {
        try {
            return repository.findByTenantIdAndStatusOrderByTitleAsc(CARE_PLANE_TENANT, "ACTIVE").stream()
                    .map(PublicWellnessController::toPublicView)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Public screening-programme read failed; returning empty list", ex);
            return List.of();
        }
    }

    /**
     * Allow-listed, PII-free public view of a screening-programme definition. Only public-safe
     * descriptive fields are mapped — never internal identifiers, status, or audit metadata.
     */
    private static Map<String, Object> toPublicView(ScreeningProgrammeEntity programme) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("programmeCode", programme.getProgrammeCode());
        view.put("title", programme.getTitle());
        view.put("description", programme.getDescription());
        view.put("ageMin", programme.getAgeMin());
        view.put("ageMax", programme.getAgeMax());
        view.put("frequencyMonths", programme.getFrequencyMonths());
        return view;
    }

    /**
     * Stateless, compute-only health calculator. PERSISTS NOTHING. Dispatches on {@code kind}:
     * <ul>
     *   <li>{@code "bmi"} — inputs heightCm, weightKg → bmi + band + non-diagnostic advice.</li>
     *   <li>{@code "bp"} — inputs systolic, diastolic → category + non-diagnostic advice.</li>
     * </ul>
     * Invalid or missing inputs → 400 with {@code {error:{code,message}}}. No store is touched.
     */
    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculate(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return badRequest("MISSING_BODY", "A JSON request body is required");
        }
        Object kindRaw = body.get("kind");
        String kind = kindRaw != null ? kindRaw.toString().trim().toLowerCase() : null;
        if (kind == null || kind.isEmpty()) {
            return badRequest("MISSING_KIND", "Field 'kind' is required (e.g. 'bmi' or 'bp')");
        }
        switch (kind) {
            case "bmi":
                return calculateBmi(body);
            case "bp":
                return calculateBp(body);
            default:
                return badRequest("UNKNOWN_KIND", "Unsupported calculator kind: " + kind);
        }
    }

    private ResponseEntity<Map<String, Object>> calculateBmi(Map<String, Object> body) {
        Double heightCm = toDouble(body.get("heightCm"));
        Double weightKg = toDouble(body.get("weightKg"));
        if (heightCm == null || weightKg == null) {
            return badRequest("INVALID_INPUT", "Fields 'heightCm' and 'weightKg' must be numbers");
        }
        if (heightCm <= 0 || weightKg <= 0) {
            return badRequest("INVALID_INPUT", "Fields 'heightCm' and 'weightKg' must be positive");
        }
        double heightM = heightCm / 100.0;
        double bmiRaw = weightKg / (heightM * heightM);
        double bmi = Math.round(bmiRaw * 10.0) / 10.0;

        String band;
        String advice;
        if (bmi < 18.5) {
            band = "Underweight";
            advice = "Your result is below the healthy range. Consider a balanced, nutrient-rich diet and speak to a health worker if you have concerns.";
        } else if (bmi < 25.0) {
            band = "Normal";
            advice = "Your result is in the healthy range. Keep up balanced eating and regular activity.";
        } else if (bmi < 30.0) {
            band = "Overweight";
            advice = "Your result is above the healthy range. Regular activity and balanced meals can help; a health worker can offer guidance.";
        } else {
            band = "Obese";
            advice = "Your result is well above the healthy range. Consider discussing a plan with a health worker.";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "bmi");
        result.put("bmi", bmi);
        result.put("band", band);
        result.put("advice", advice);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> calculateBp(Map<String, Object> body) {
        Double systolic = toDouble(body.get("systolic"));
        Double diastolic = toDouble(body.get("diastolic"));
        if (systolic == null || diastolic == null) {
            return badRequest("INVALID_INPUT", "Fields 'systolic' and 'diastolic' must be numbers");
        }
        if (systolic <= 0 || diastolic <= 0) {
            return badRequest("INVALID_INPUT", "Fields 'systolic' and 'diastolic' must be positive");
        }

        String category;
        String advice;
        // Priority order: crisis → stage 2 → stage 1 → elevated → normal.
        if (systolic >= 180 || diastolic >= 120) {
            category = "Crisis";
            advice = "Seek emergency care now";
        } else if (systolic >= 140 || diastolic >= 90) {
            category = "Stage 2";
            advice = "Your reading is high. Please arrange to see a health worker soon.";
        } else if ((systolic >= 130 && systolic <= 139) || (diastolic >= 80 && diastolic <= 89)) {
            category = "Stage 1";
            advice = "Your reading is somewhat high. Lifestyle steps help; consider a check-up.";
        } else if (systolic >= 120 && systolic <= 129 && diastolic < 80) {
            category = "Elevated";
            advice = "Your reading is slightly raised. Healthy habits can help keep it in range.";
        } else {
            category = "Normal";
            advice = "Your reading is in the normal range. Keep up healthy habits.";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "bp");
        result.put("category", category);
        result.put("advice", advice);
        return ResponseEntity.ok(result);
    }

    /** Null-safe numeric coercion: accepts JSON numbers and numeric strings, else null. */
    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        return ResponseEntity.badRequest().body(body);
    }
}
