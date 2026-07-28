package zw.gov.mohcc.impilo.mentalhealth.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.mentalhealth.core.ClinicalRecordService;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.FollowupEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.RiskFormulationEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.SafetyPlanEntity;
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

/** Assessment, risk formulation, safety plan, follow-up — the clinical record chain hung off a referral. */
@RestController
@RequestMapping("/internal/v1/mental-health")
public class ClinicalRecordController {

    private final ClinicalRecordService service;

    public ClinicalRecordController(ClinicalRecordService service) {
        this.service = service;
    }

    @PostMapping("/referrals/{referralId}/assessments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordAssessment(
            @PathVariable UUID referralId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String assessedBy = str(body, "assessedBy", "assessed_by");
        var a = service.recordAssessment(ctx.tenantId(), referralId,
                str(body, "assessmentType", "assessment_type"),
                str(body, "mentalStateExam", "mental_state_exam"),
                Boolean.TRUE.equals(body.get("capacityAssessed")) || Boolean.TRUE.equals(body.get("capacity_assessed")),
                str(body, "capacityOutcome", "capacity_outcome"),
                assessedBy != null ? assessedBy : ctx.actorId(),
                str(body, "notes"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(assessmentRow(a), ctx.correlationId().toString()));
    }

    @GetMapping("/referrals/{referralId}/assessments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listAssessments(@PathVariable UUID referralId) {
        TrustContext ctx = TrustContextHolder.require();
        var list = service.listAssessments(ctx.tenantId(), referralId);
        return ResponseEntity.ok(ApiResponse.ok(list.stream().map(this::assessmentRow).toList(), ctx.correlationId().toString()));
    }

    @PostMapping("/assessments/{assessmentId}/risk-formulation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordRiskFormulation(
            @PathVariable UUID assessmentId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String formulatedBy = str(body, "formulatedBy", "formulated_by");
        var rf = service.recordRiskFormulation(ctx.tenantId(), assessmentId,
                str(body, "riskToSelf", "risk_to_self"),
                str(body, "riskToOthers", "risk_to_others"),
                str(body, "riskSummary", "risk_summary"),
                formulatedBy != null ? formulatedBy : ctx.actorId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(riskRow(rf), ctx.correlationId().toString()));
    }

    @GetMapping("/assessments/{assessmentId}/risk-formulation")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listRiskFormulations(@PathVariable UUID assessmentId) {
        TrustContext ctx = TrustContextHolder.require();
        var list = service.listRiskFormulations(ctx.tenantId(), assessmentId);
        return ResponseEntity.ok(ApiResponse.ok(list.stream().map(this::riskRow).toList(), ctx.correlationId().toString()));
    }

    @PostMapping("/referrals/{referralId}/safety-plans")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordSafetyPlan(
            @PathVariable UUID referralId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String createdBy = str(body, "createdBy", "created_by");
        var sp = service.recordSafetyPlan(ctx.tenantId(), referralId,
                str(body, "warningSigns", "warning_signs"),
                str(body, "copingStrategies", "coping_strategies"),
                str(body, "supportContacts", "support_contacts"),
                str(body, "meansRestrictionNotes", "means_restriction_notes"),
                createdBy != null ? createdBy : ctx.actorId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(safetyPlanRow(sp), ctx.correlationId().toString()));
    }

    @GetMapping("/referrals/{referralId}/safety-plans")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listSafetyPlans(@PathVariable UUID referralId) {
        TrustContext ctx = TrustContextHolder.require();
        var list = service.listSafetyPlans(ctx.tenantId(), referralId);
        return ResponseEntity.ok(ApiResponse.ok(list.stream().map(this::safetyPlanRow).toList(), ctx.correlationId().toString()));
    }

    @PostMapping("/referrals/{referralId}/followups")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scheduleFollowup(
            @PathVariable UUID referralId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String scheduledBy = str(body, "scheduledBy", "scheduled_by");
        var f = service.scheduleFollowup(ctx.tenantId(), referralId,
                str(body, "followupType", "followup_type"),
                dateVal(body, "scheduledAt", "scheduled_at"),
                scheduledBy != null ? scheduledBy : ctx.actorId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(followupRow(f), ctx.correlationId().toString()));
    }

    @PostMapping("/followups/{id}/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> completeFollowup(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var f = service.completeFollowup(id, ctx.tenantId(), str(body, "outcomeNotes", "outcome_notes"));
        return ResponseEntity.ok(ApiResponse.ok(followupRow(f), ctx.correlationId().toString()));
    }

    @GetMapping("/referrals/{referralId}/followups")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listFollowups(@PathVariable UUID referralId) {
        TrustContext ctx = TrustContextHolder.require();
        var list = service.listFollowups(ctx.tenantId(), referralId);
        return ResponseEntity.ok(ApiResponse.ok(list.stream().map(this::followupRow).toList(), ctx.correlationId().toString()));
    }

    private Map<String, Object> assessmentRow(AssessmentEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("referral_id", a.getReferralId().toString());
        m.put("assessment_type", a.getAssessmentType());
        m.put("mental_state_exam", a.getMentalStateExam());
        m.put("capacity_assessed", a.isCapacityAssessed());
        m.put("capacity_outcome", a.getCapacityOutcome());
        m.put("assessed_by", a.getAssessedBy());
        m.put("assessed_at", a.getAssessedAt() != null ? a.getAssessedAt().toString() : null);
        m.put("notes", a.getNotes());
        return m;
    }

    private Map<String, Object> riskRow(RiskFormulationEntity rf) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rf.getId().toString());
        m.put("assessment_id", rf.getAssessmentId().toString());
        m.put("risk_to_self", rf.getRiskToSelf());
        m.put("risk_to_others", rf.getRiskToOthers());
        m.put("risk_summary", rf.getRiskSummary());
        m.put("formulated_by", rf.getFormulatedBy());
        m.put("formulated_at", rf.getFormulatedAt() != null ? rf.getFormulatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> safetyPlanRow(SafetyPlanEntity sp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", sp.getId().toString());
        m.put("referral_id", sp.getReferralId().toString());
        m.put("warning_signs", sp.getWarningSigns());
        m.put("coping_strategies", sp.getCopingStrategies());
        m.put("support_contacts", sp.getSupportContacts());
        m.put("means_restriction_notes", sp.getMeansRestrictionNotes());
        m.put("created_by", sp.getCreatedBy());
        m.put("created_at", sp.getCreatedAt() != null ? sp.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> followupRow(FollowupEntity f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId().toString());
        m.put("referral_id", f.getReferralId().toString());
        m.put("followup_type", f.getFollowupType());
        m.put("scheduled_at", f.getScheduledAt() != null ? f.getScheduledAt().toString() : null);
        m.put("scheduled_by", f.getScheduledBy());
        m.put("completed_at", f.getCompletedAt() != null ? f.getCompletedAt().toString() : null);
        m.put("outcome_notes", f.getOutcomeNotes());
        return m;
    }
}
