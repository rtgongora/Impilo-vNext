package zw.gov.mohcc.impilo.mentalhealth.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.mentalhealth.core.AdmissionRequestService;
import zw.gov.mohcc.impilo.mentalhealth.core.InvoluntaryEpisodeService;
import zw.gov.mohcc.impilo.mentalhealth.core.RestraintEventService;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.AdmissionRequestEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.InvoluntaryEpisodeEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.RestraintEventEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static zw.gov.mohcc.impilo.mentalhealth.api.controller.ReferralController.dateVal;
import static zw.gov.mohcc.impilo.mentalhealth.api.controller.ReferralController.str;
import static zw.gov.mohcc.impilo.mentalhealth.api.controller.ReferralController.uuidVal;

/** Involuntary episodes (legal basis), restraint-reduction events, and admission requests. */
@RestController
@RequestMapping("/internal/v1/mental-health")
public class SafetyController {

    private final InvoluntaryEpisodeService involuntaryEpisodeService;
    private final RestraintEventService restraintEventService;
    private final AdmissionRequestService admissionRequestService;

    public SafetyController(InvoluntaryEpisodeService involuntaryEpisodeService,
                            RestraintEventService restraintEventService,
                            AdmissionRequestService admissionRequestService) {
        this.involuntaryEpisodeService = involuntaryEpisodeService;
        this.restraintEventService = restraintEventService;
        this.admissionRequestService = admissionRequestService;
    }

    // ── Involuntary episodes ─────────────────────────────────────────────────────────────────

    @PostMapping("/referrals/{referralId}/involuntary-episodes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openInvoluntaryEpisode(
            @PathVariable UUID referralId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String authorisedBy = str(body, "authorisedBy", "authorised_by");
        var e = involuntaryEpisodeService.open(ctx.tenantId(), referralId,
                str(body, "legalBasis", "legal_basis"),
                authorisedBy != null ? authorisedBy : ctx.actorId(),
                dateVal(body, "reviewDueAt", "review_due_at"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(involuntaryRow(e), ctx.correlationId().toString()));
    }

    @PostMapping("/involuntary-episodes/{id}/close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> closeInvoluntaryEpisode(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var e = involuntaryEpisodeService.close(id, ctx.tenantId(), str(body, "endedReason", "ended_reason"));
        return ResponseEntity.ok(ApiResponse.ok(involuntaryRow(e), ctx.correlationId().toString()));
    }

    @GetMapping("/referrals/{referralId}/involuntary-episodes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> involuntaryHistory(@PathVariable UUID referralId) {
        TrustContext ctx = TrustContextHolder.require();
        var list = involuntaryEpisodeService.history(ctx.tenantId(), referralId);
        return ResponseEntity.ok(ApiResponse.ok(list.stream().map(this::involuntaryRow).toList(), ctx.correlationId().toString()));
    }

    // ── Restraint-reduction register ─────────────────────────────────────────────────────────

    @PostMapping("/referrals/{referralId}/restraint-events")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordRestraintEvent(
            @PathVariable UUID referralId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String initiatedBy = str(body, "initiatedBy", "initiated_by");
        var e = restraintEventService.record(ctx.tenantId(), referralId,
                str(body, "restraintType", "restraint_type"),
                str(body, "reason"),
                str(body, "deEscalationAttempted", "de_escalation_attempted"),
                initiatedBy != null ? initiatedBy : ctx.actorId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(restraintRow(e), ctx.correlationId().toString()));
    }

    @PostMapping("/restraint-events/{id}/end")
    public ResponseEntity<ApiResponse<Map<String, Object>>> endRestraintEvent(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        var e = restraintEventService.end(id, ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(restraintRow(e), ctx.correlationId().toString()));
    }

    @PostMapping("/restraint-events/{id}/review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewRestraintEvent(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String reviewedBy = str(body, "reviewedBy", "reviewed_by");
        var e = restraintEventService.review(id, ctx.tenantId(),
                reviewedBy != null ? reviewedBy : ctx.actorId(),
                str(body, "reviewOutcome", "review_outcome"));
        return ResponseEntity.ok(ApiResponse.ok(restraintRow(e), ctx.correlationId().toString()));
    }

    @GetMapping("/referrals/{referralId}/restraint-events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> restraintHistory(@PathVariable UUID referralId) {
        TrustContext ctx = TrustContextHolder.require();
        var list = restraintEventService.history(ctx.tenantId(), referralId);
        return ResponseEntity.ok(ApiResponse.ok(list.stream().map(this::restraintRow).toList(), ctx.correlationId().toString()));
    }

    @GetMapping("/restraint-events/unreviewed")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> unreviewedRestraintEvents() {
        TrustContext ctx = TrustContextHolder.require();
        var list = restraintEventService.unreviewed(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(list.stream().map(this::restraintRow).toList(), ctx.correlationId().toString()));
    }

    // ── Admission requests (raise, never decide) ─────────────────────────────────────────────

    @PostMapping("/referrals/{referralId}/admission-requests")
    public ResponseEntity<ApiResponse<Map<String, Object>>> raiseAdmissionRequest(
            @PathVariable UUID referralId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String requestedBy = str(body, "requestedBy", "requested_by");
        var r = admissionRequestService.raise(ctx.tenantId(), referralId,
                str(body, "reason"),
                requestedBy != null ? requestedBy : ctx.actorId(),
                uuidVal(body, "targetFacilityId", "target_facility_id"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(admissionRow(r), ctx.correlationId().toString()));
    }

    @PostMapping("/admission-requests/{id}/acknowledge")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acknowledgeAdmissionRequest(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var r = admissionRequestService.acknowledge(id, ctx.tenantId(), str(body, "pctAdmissionId", "pct_admission_id"));
        return ResponseEntity.ok(ApiResponse.ok(admissionRow(r), ctx.correlationId().toString()));
    }

    @GetMapping("/referrals/{referralId}/admission-requests")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> admissionHistory(@PathVariable UUID referralId) {
        TrustContext ctx = TrustContextHolder.require();
        var list = admissionRequestService.history(ctx.tenantId(), referralId);
        return ResponseEntity.ok(ApiResponse.ok(list.stream().map(this::admissionRow).toList(), ctx.correlationId().toString()));
    }

    private Map<String, Object> involuntaryRow(InvoluntaryEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("referral_id", e.getReferralId().toString());
        m.put("legal_basis", e.getLegalBasis());
        m.put("authorised_by", e.getAuthorisedBy());
        m.put("authorised_at", e.getAuthorisedAt() != null ? e.getAuthorisedAt().toString() : null);
        m.put("review_due_at", e.getReviewDueAt() != null ? e.getReviewDueAt().toString() : null);
        m.put("status", e.getStatus());
        m.put("ended_at", e.getEndedAt() != null ? e.getEndedAt().toString() : null);
        m.put("ended_reason", e.getEndedReason());
        return m;
    }

    private Map<String, Object> restraintRow(RestraintEventEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("referral_id", e.getReferralId().toString());
        m.put("restraint_type", e.getRestraintType());
        m.put("reason", e.getReason());
        m.put("de_escalation_attempted", e.getDeEscalationAttempted());
        m.put("initiated_by", e.getInitiatedBy());
        m.put("initiated_at", e.getInitiatedAt() != null ? e.getInitiatedAt().toString() : null);
        m.put("ended_at", e.getEndedAt() != null ? e.getEndedAt().toString() : null);
        m.put("reviewed_by", e.getReviewedBy());
        m.put("reviewed_at", e.getReviewedAt() != null ? e.getReviewedAt().toString() : null);
        m.put("review_outcome", e.getReviewOutcome());
        return m;
    }

    private Map<String, Object> admissionRow(AdmissionRequestEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("referral_id", r.getReferralId().toString());
        m.put("reason", r.getReason());
        m.put("requested_by", r.getRequestedBy());
        m.put("requested_at", r.getRequestedAt() != null ? r.getRequestedAt().toString() : null);
        m.put("target_facility_id", r.getTargetFacilityId() != null ? r.getTargetFacilityId().toString() : null);
        m.put("status", r.getStatus());
        m.put("pct_admission_id", r.getPctAdmissionId());
        return m;
    }
}
