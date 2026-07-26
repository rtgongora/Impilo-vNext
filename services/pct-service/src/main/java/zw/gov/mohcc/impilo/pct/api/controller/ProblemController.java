package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.DuplicateProblemException;
import zw.gov.mohcc.impilo.pct.core.clinical.ProblemLinkService;
import zw.gov.mohcc.impilo.pct.core.clinical.ProblemService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemLinkEntity;
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
    private final ProblemLinkService linkService;

    public ProblemController(ProblemService problemService, ProblemLinkService linkService) {
        this.problemService = problemService;
        this.linkService = linkService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "clinical_status", required = false) String clinicalStatus) {
        List<Map<String, Object>> out = problemService.list(subjectCpid, clinicalStatus)
                .stream().map(this::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, TrustContextHolder.require().correlationId().toString()));
    }

    /**
     * Reports a suspected duplicate as 409, carrying the problem already on the list and the two
     * answers the caller can give. It is a question, not a refusal: the clinician is the only one
     * who can say whether this is the same disease returning or a genuinely distinct problem.
     */
    @ExceptionHandler(DuplicateProblemException.class)
    public ResponseEntity<Map<String, Object>> duplicate(DuplicateProblemException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "problem_already_on_list");
        body.put("message", e.getMessage());
        body.put("existing", toMap(e.getExisting()));
        body.put("resolutions", List.of(
                Map.of("duplicate_resolution", "SAME_PROBLEM_RETURNING",
                        "effect", "Moves the existing problem to RECURRENCE. No new entry is created."),
                Map.of("duplicate_resolution", "DISTINCT_PROBLEM",
                        "effect", "Records this as a separate problem alongside the existing one.")));
        body.put("correlation_id", TrustContextHolder.require().correlationId().toString());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> add(@RequestBody Map<String, Object> body) {
        // Authz: RBAC enforced at ext_authz via tshepo_authz.policy_rule `clinical-problem-write-*`
        // (V018) — PROVIDER + CLINICIAN/NURSE, facility-scoped, purpose TREATMENT. The subject
        // relationship (the actor must hold an active care context for the patient) is enforced in
        // ProblemService via ClinicalAccessGuard — ext_authz cannot bind the body subject to consent.
        ProblemEntity p = problemService.add(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(p), TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping("/{problemId}/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolve(@PathVariable UUID problemId) {
        ProblemEntity p = problemService.resolve(problemId);
        return ResponseEntity.ok(ApiResponse.ok(toMap(p), TrustContextHolder.require().correlationId().toString()));
    }

    /**
     * Moves a problem to a new clinical status — including back out of RESOLVED when a condition
     * returns, which is the transition that keeps one disease as one row instead of accumulating a
     * fresh entry on the list every time it relapses.
     */
    @PostMapping("/{problemId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changeStatus(
            @PathVariable UUID problemId,
            @RequestBody Map<String, Object> body) {
        ProblemEntity p = problemService.changeStatus(problemId, String.valueOf(body.get("clinical_status")));
        return ResponseEntity.ok(ApiResponse.ok(toMap(p), TrustContextHolder.require().correlationId().toString()));
    }

    // ── Links: what this problem rests on, caused, and is treated by ────────────

    @GetMapping("/{problemId}/links")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> links(@PathVariable UUID problemId) {
        List<Map<String, Object>> out = linkService.list(problemId)
                .stream().map(ProblemController::linkToMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping("/{problemId}/links")
    public ResponseEntity<ApiResponse<Map<String, Object>>> link(
            @PathVariable UUID problemId,
            @RequestBody Map<String, Object> body) {
        ProblemLinkEntity link = linkService.link(problemId, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(linkToMap(link), TrustContextHolder.require().correlationId().toString()));
    }

    @DeleteMapping("/links/{linkId}")
    public ResponseEntity<Void> unlink(@PathVariable UUID linkId) {
        linkService.unlink(linkId);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> linkToMap(ProblemLinkEntity link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("link_id", link.getLinkId().toString());
        m.put("problem_id", link.getProblemId().toString());
        m.put("link_type", link.getLinkType());
        m.put("target_type", link.getTargetType());
        m.put("target_ref", link.getTargetProblemId() != null
                ? link.getTargetProblemId().toString() : link.getTargetRef());
        m.put("note", link.getNote());
        m.put("recorded_by", link.getRecordedBy());
        m.put("created_at", link.getCreatedAt() != null ? link.getCreatedAt().toString() : null);
        return m;
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
        m.put("severity", p.getSeverity());
        m.put("diagnostic_certainty", p.getDiagnosticCertainty());
        m.put("evidence", p.getEvidence());
        m.put("responsible_service", p.getResponsibleService());
        m.put("review_date", p.getReviewDate() != null ? p.getReviewDate().toString() : null);
        m.put("last_recurrence_at", p.getLastRecurrenceAt() != null ? p.getLastRecurrenceAt().toString() : null);
        m.put("onset_date", p.getOnsetDate() != null ? p.getOnsetDate().toString() : null);
        m.put("recorded_by", p.getRecordedBy());
        m.put("resolved_at", p.getResolvedAt() != null ? p.getResolvedAt().toString() : null);
        m.put("notes", p.getNotes());
        m.put("created_at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        return m;
    }
}
