package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.CarePlanService;
import zw.gov.mohcc.impilo.pct.persistence.entity.CarePlanEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.CarePlanGoalEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Outpatient care-plan API (PCT) — parity with inpatient-service CarePlan for the OPD context.
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz (policy {@code CARE-PLAN-WRITE}, track P).
 * Every write is audited via the outbox.</p>
 */
@RestController
@RequestMapping("/v1/care-plans")
public class CarePlanController {

    private final CarePlanService carePlanService;

    public CarePlanController(CarePlanService carePlanService) {
        this.carePlanService = carePlanService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        List<Map<String, Object>> out = carePlanService.listForSubject(subjectCpid)
                .stream().map(this::toPlan).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        // Authz: RBAC enforced at ext_authz via tshepo_authz.policy_rule `clinical-care-plan-write-*`
        // (V018) — PROVIDER + CLINICIAN/NURSE, facility-scoped, purpose TREATMENT. The subject
        // relationship (the actor must hold an active care context for the patient) is enforced in
        // CarePlanService via ClinicalAccessGuard — ext_authz cannot bind the body subject to consent.
        CarePlanEntity plan = carePlanService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toPlan(plan), TrustContextHolder.require().correlationId().toString()));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID planId) {
        CarePlanEntity plan = carePlanService.get(planId);
        Map<String, Object> detail = toPlan(plan);
        detail.put("goals", carePlanService.goals(planId).stream().map(this::toGoal).collect(Collectors.toList()));
        return ResponseEntity.ok(ApiResponse.ok(detail, TrustContextHolder.require().correlationId().toString()));
    }

    @PatchMapping("/{planId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStatus(
            @PathVariable UUID planId, @RequestBody Map<String, Object> body) {
        CarePlanEntity plan = carePlanService.updateStatus(planId, String.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(toPlan(plan), TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping("/{planId}/goals")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addGoal(
            @PathVariable UUID planId, @RequestBody Map<String, Object> body) {
        CarePlanGoalEntity goal = carePlanService.addGoal(planId, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toGoal(goal), TrustContextHolder.require().correlationId().toString()));
    }

    @PatchMapping("/{planId}/goals/{goalId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateGoalStatus(
            @PathVariable UUID planId, @PathVariable UUID goalId, @RequestBody Map<String, Object> body) {
        CarePlanGoalEntity goal = carePlanService.updateGoalStatus(planId, goalId, String.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(toGoal(goal), TrustContextHolder.require().correlationId().toString()));
    }

    private Map<String, Object> toPlan(CarePlanEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan_id", p.getPlanId().toString());
        m.put("subject_cpid", p.getSubjectCpid());
        m.put("journey_id", p.getJourneyId());
        m.put("encounter_id", p.getEncounterId());
        m.put("title", p.getTitle());
        m.put("plan_type", p.getPlanType());
        m.put("status", p.getStatus());
        m.put("created_by", p.getCreatedBy());
        m.put("created_at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        m.put("updated_at", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toGoal(CarePlanGoalEntity g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("goal_id", g.getGoalId().toString());
        m.put("plan_id", g.getPlanId().toString());
        m.put("category", g.getCategory());
        m.put("description", g.getDescription());
        m.put("target_value", g.getTargetValue());
        m.put("current_value", g.getCurrentValue());
        m.put("priority", g.getPriority());
        m.put("status", g.getStatus());
        m.put("target_date", g.getTargetDate() != null ? g.getTargetDate().toString() : null);
        m.put("achieved_at", g.getAchievedAt() != null ? g.getAchievedAt().toString() : null);
        return m;
    }
}
