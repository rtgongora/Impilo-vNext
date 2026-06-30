package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.PlanService;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.persistence.entity.PlanEnrollmentEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.WellnessPlanEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness plans / journeys (Simba-owned). Catalogue, eligibility-gated enrolment with task
 * instantiation, and per-task progress. Backs the BFF {@code /programs} + {@code /enrollments}
 * surface (the BFF reverse-proxies {@code /internal/v1/wellness/**} to this service).
 */
@RestController
@RequestMapping("/internal/v1/wellness")
public class PlanController {

    private final PlanService planService;
    private final WellnessAuditService audit;

    public PlanController(PlanService planService, WellnessAuditService audit) {
        this.planService = planService;
        this.audit = audit;
    }

    // ── Plan catalogue ───────────────────────────────────────────
    @GetMapping("/programs")
    public List<WellnessPlanEntity> listPlans(@RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return planService.listPlans(tenantId);
    }

    @GetMapping("/programs/{planId}")
    public Map<String, Object> getPlan(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("planId") UUID planId) {
        WellnessPlanEntity plan = planService.getPlan(tenantId, planId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plan", plan);
        out.put("tasks", planService.planTasks(planId));
        return out;
    }

    // ── Enrolment ────────────────────────────────────────────────
    @PostMapping("/enrollments")
    public ResponseEntity<Map<String, Object>> enroll(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        UUID planId = UUID.fromString(required(body, "plan_id"));
        String personCpid = required(body, "person_cpid");
        String enrolledBy = actorId != null ? actorId : personCpid;

        PlanService.EnrollmentResult result = planService.enroll(tenantId, planId, personCpid, enrolledBy);

        String relationship = personCpid.equals(actorId) || actorId == null
                ? WellnessAuditService.SELF : WellnessAuditService.DEPENDANT;
        audit.record(tenantId, actorId, actorType, null, personCpid, relationship, null, null,
                "wellness.plan.enroll", "ALLOW", null, correlationId, requestId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enrollment", result.enrollment());
        out.put("clinical_caution", result.clinicalCaution());
        if (result.clinicalCaution()) {
            out.put("care_prompt",
                    "This journey touches on a health risk. Consider a screening or speaking with a provider.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @GetMapping("/enrollments")
    public List<PlanEnrollmentEntity> listEnrollments(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return planService.listEnrollments(tenantId, personCpid);
    }

    @GetMapping("/enrollments/{enrollmentId}/tasks")
    public List<?> enrollmentTasks(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("enrollmentId") UUID enrollmentId) {
        return planService.enrollmentTasks(enrollmentId);
    }

    @PostMapping("/enrollments/tasks/{enrollmentTaskId}/complete")
    public PlanEnrollmentEntity completeTask(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("enrollmentTaskId") UUID enrollmentTaskId) {
        return planService.completeTask(tenantId, enrollmentTaskId);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
