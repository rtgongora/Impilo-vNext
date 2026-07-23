package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.AllergyService;
import zw.gov.mohcc.impilo.pct.persistence.entity.AllergyEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Allergy/intolerance SoR API (OF-B3). Fulfils the BFF strangler contract that
 * previously 404'd ({@code GET/POST /v1/allergies}, {@code POST /{id}/deactivate}) —
 * landing this flips the BFF prescribing validation's allergy check from
 * WARNING ALLERGIES_UNVERIFIED to a real coded conflict check automatically.
 */
@RestController
@RequestMapping("/v1/allergies")
public class AllergyController {

    private final AllergyService allergyService;

    public AllergyController(AllergyService allergyService) {
        this.allergyService = allergyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid,
            @RequestParam(name = "clinical_status", required = false) String clinicalStatus) {
        String subject = subjectCpid != null && !subjectCpid.isBlank() ? subjectCpid : patientId;
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("patient_id is required");
        }
        List<Map<String, Object>> out = allergyService.list(subject, clinicalStatus)
                .stream().map(AllergyController::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> add(@RequestBody Map<String, Object> body) {
        // Authz: RBAC at ext_authz; subject relationship bound in AllergyService via
        // ClinicalAccessGuard (same discipline as the problems list).
        AllergyEntity a = allergyService.add(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(a), correlationId()));
    }

    @PostMapping("/{allergyId}/deactivate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivate(@PathVariable UUID allergyId) {
        AllergyEntity a = allergyService.deactivate(allergyId);
        return ResponseEntity.ok(ApiResponse.ok(toMap(a), correlationId()));
    }

    private static Map<String, Object> toMap(AllergyEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("allergy_id", a.getAllergyId().toString());
        m.put("patient_id", a.getSubjectCpid());
        m.put("subject_cpid", a.getSubjectCpid());
        m.put("allergen", a.getAllergen());
        m.put("allergen_code", a.getAllergenCode());
        m.put("code_system", a.getCodeSystem());
        m.put("allergen_type", a.getAllergenType());
        m.put("reaction", a.getReaction());
        m.put("severity", a.getSeverity());
        m.put("clinical_status", a.getClinicalStatus());
        m.put("onset_date", a.getOnsetDate() != null ? a.getOnsetDate().toString() : null);
        m.put("notes", a.getNotes());
        m.put("recorded_by", a.getRecordedBy());
        m.put("deactivated_at", a.getDeactivatedAt() != null ? a.getDeactivatedAt().toString() : null);
        m.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        return m;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
