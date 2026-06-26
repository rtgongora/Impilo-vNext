package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.ProblemService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Outpatient problems-list API (PCT).
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz (policy {@code CARE-PLAN-WRITE} family, track P).
 * Every write is audited via the outbox.</p>
 */
@RestController
@RequestMapping("/v1/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "clinical_status", required = false) String clinicalStatus) {
        List<Map<String, Object>> out = problemService.list(subjectCpid, clinicalStatus)
                .stream().map(this::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> add(@RequestBody Map<String, Object> body) {
        // TODO(policy CARE-PLAN-WRITE): clinical-write authz enforced at ext_authz; rule lives in track P.
        ProblemEntity p = problemService.add(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(p), TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping("/{problemId}/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolve(@PathVariable UUID problemId) {
        ProblemEntity p = problemService.resolve(problemId);
        return ResponseEntity.ok(ApiResponse.ok(toMap(p), TrustContextHolder.require().correlationId().toString()));
    }

    private Map<String, Object> toMap(ProblemEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("problem_id", p.getProblemId().toString());
        m.put("subject_cpid", p.getSubjectCpid());
        m.put("journey_id", p.getJourneyId());
        m.put("encounter_id", p.getEncounterId());
        m.put("code", p.getCode());
        m.put("code_system", p.getCodeSystem());
        m.put("display", p.getDisplay());
        m.put("clinical_status", p.getClinicalStatus());
        m.put("category", p.getCategory());
        m.put("onset_date", p.getOnsetDate() != null ? p.getOnsetDate().toString() : null);
        m.put("recorded_by", p.getRecordedBy());
        m.put("resolved_at", p.getResolvedAt() != null ? p.getResolvedAt().toString() : null);
        m.put("notes", p.getNotes());
        m.put("created_at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        return m;
    }
}
