package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.contraception.MedicalEligibilityEngine;
import zw.gov.mohcc.impilo.clinical.contraception.MedicalEligibilityService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/clinical/contraception/mec")
public class MedicalEligibilityController {

    private final MedicalEligibilityService eligibility;

    public MedicalEligibilityController(MedicalEligibilityService eligibility) {
        this.eligibility = eligibility;
    }

    public record AssessRequest(String usePhase, Map<String, Object> facts, List<String> methodFilter) {}

    @PostMapping("/assess")
    public ResponseEntity<Map<String, Object>> assess(@RequestBody(required = false) AssessRequest request) {
        MedicalEligibilityEngine.UsePhase phase = parsePhase(request == null ? null : request.usePhase());
        Map<String, Object> facts = request == null || request.facts() == null ? Map.of() : request.facts();
        List<String> filter = request == null ? null : request.methodFilter();
        MedicalEligibilityEngine.Assessment result = eligibility.assess(phase, facts, filter);

        if (!result.contentProblems().isEmpty() && result.methods().isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "mec_matrix_unavailable");
            body.put("message", result.note());
            return ResponseEntity.status(502).body(Map.of("data", body));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("methods", result.methods().stream().map(MedicalEligibilityController::method).toList());
        data.put("conditions_not_assessed", result.conditionsNotAssessed());
        data.put("conditions_out_of_scope", result.conditionsOutOfScope());
        data.put("content_problems", result.contentProblems());
        data.put("content_version", result.contentVersion());
        data.put("approval_status", result.approvalStatus());
        data.put("note", result.note());
        return ResponseEntity.ok(Map.of("data", data));
    }

    private static MedicalEligibilityEngine.UsePhase parsePhase(String raw) {
        if (raw == null || raw.isBlank()) return MedicalEligibilityEngine.UsePhase.INITIATION;
        try {
            return MedicalEligibilityEngine.UsePhase.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MedicalEligibilityEngine.UsePhase.INITIATION;
        }
    }

    private static Map<String, Object> method(MedicalEligibilityEngine.Eligibility e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("method_code", e.methodCode());
        m.put("method_name", e.methodName());
        m.put("use_phase", e.usePhase().name());
        m.put("category", e.category());
        m.put("recommendation", e.recommendation().name());
        m.put("driving_conditions", e.drivingConditions().stream().map(MedicalEligibilityController::driving).toList());
        m.put("unresolved_conditions", e.unresolvedConditions());
        m.put("missing_inputs", e.missingInputs());
        m.put("provisional", e.provisional());
        m.put("rationale", e.rationale());
        return m;
    }

    private static Map<String, Object> driving(MedicalEligibilityEngine.DrivingCondition d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("condition_code", d.conditionCode());
        m.put("condition_name", d.conditionName());
        m.put("category", d.category());
        m.put("clarification", d.clarification());
        m.put("source_refs", d.sourceRefs() == null ? List.of() : new ArrayList<>(d.sourceRefs()));
        return m;
    }
}
