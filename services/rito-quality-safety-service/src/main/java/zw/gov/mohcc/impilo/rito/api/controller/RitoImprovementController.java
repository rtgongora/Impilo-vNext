package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.ImprovementService;
import zw.gov.mohcc.impilo.rito.persistence.entity.CorrectiveActionEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.QiPlanEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.QiTaskEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/rito")
public class RitoImprovementController {

    private final ImprovementService improvementService;

    public RitoImprovementController(ImprovementService improvementService) {
        this.improvementService = improvementService;
    }

    // ---- CAPA ----

    @PostMapping("/corrective-actions")
    public ResponseEntity<CorrectiveActionEntity> createCorrectiveAction(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(improvementService.createCorrectiveAction(tenantId,
                str(body, "action_type"), required(body, "title"), str(body, "description"),
                str(body, "source_type"), str(body, "source_ref"), uuid(body, "case_id"),
                uuid(body, "audit_finding_id"), uuid(body, "facility_id"), str(body, "owner_actor_id"),
                str(body, "owner_actor_type"), str(body, "priority"), odt(body, "due_at"), actorId));
    }

    @GetMapping("/corrective-actions")
    public List<CorrectiveActionEntity> listCorrectiveActions(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) String status) {
        return improvementService.listCorrectiveActions(tenantId, status);
    }

    @GetMapping("/corrective-actions/{id}")
    public CorrectiveActionEntity getCorrectiveAction(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return improvementService.getCorrectiveAction(tenantId, id);
    }

    @PostMapping("/corrective-actions/{id}/progress")
    public CorrectiveActionEntity progress(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return improvementService.progress(tenantId, id);
    }

    @PostMapping("/corrective-actions/{id}/complete")
    public CorrectiveActionEntity complete(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String completedBy = str(body, "completed_by");
        return improvementService.complete(tenantId, id,
                completedBy != null ? completedBy : actorId, str(body, "completion_notes"));
    }

    @PostMapping("/corrective-actions/{id}/verify")
    public CorrectiveActionEntity verify(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String verifiedBy = str(body, "verified_by");
        return improvementService.verify(tenantId, id,
                verifiedBy != null ? verifiedBy : actorId, required(body, "outcome"), str(body, "notes"));
    }

    @PostMapping("/corrective-actions/{id}/cancel")
    public CorrectiveActionEntity cancel(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return improvementService.cancel(tenantId, id, str(body, "reason"));
    }

    @GetMapping("/cases/{caseId}/corrective-actions")
    public List<CorrectiveActionEntity> byCase(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID caseId) {
        return improvementService.byCase(tenantId, caseId);
    }

    // ---- QI / PDSA ----

    @PostMapping("/qi-plans")
    public ResponseEntity<QiPlanEntity> createQiPlan(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(improvementService.createQiPlan(tenantId,
                required(body, "title"), str(body, "aim_statement"), str(body, "methodology"),
                str(body, "problem_statement"), bd(body, "baseline_measure"), bd(body, "target_measure"),
                str(body, "measure_unit"), uuid(body, "facility_id"), uuid(body, "district_id"),
                str(body, "sponsor_actor_id"), str(body, "lead_actor_id"), uuid(body, "source_case_id"),
                uuid(body, "source_finding_id"), actorId));
    }

    @GetMapping("/qi-plans")
    public List<QiPlanEntity> listQiPlans(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) String status) {
        return improvementService.listQiPlans(tenantId, status);
    }

    @GetMapping("/qi-plans/{id}")
    public QiPlanEntity getQiPlan(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return improvementService.getQiPlan(tenantId, id);
    }

    @PostMapping("/qi-plans/{id}/activate")
    public QiPlanEntity activateQiPlan(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return improvementService.activateQiPlan(tenantId, id);
    }

    @PostMapping("/qi-plans/{id}/complete")
    public QiPlanEntity completeQiPlan(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return improvementService.completeQiPlan(tenantId, id, str(body, "outcome_summary"));
    }

    @PostMapping("/qi-plans/{id}/tasks")
    public ResponseEntity<QiTaskEntity> addTask(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(improvementService.addTask(tenantId, id,
                required(body, "title"), str(body, "description"), str(body, "pdsa_stage"),
                str(body, "owner_actor_id"), intVal(body, "ordinal"), odt(body, "due_at")));
    }

    @PutMapping("/qi-tasks/{taskId}")
    public QiTaskEntity updateTask(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID taskId,
            @RequestBody Map<String, Object> body) {
        return improvementService.updateTask(tenantId, taskId,
                required(body, "status"), odt(body, "completed_at"));
    }

    @GetMapping("/qi-plans/{id}/tasks")
    public List<QiTaskEntity> listTasks(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return improvementService.listTasks(id);
    }

    // ---- request-map helpers ----

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return UUID.fromString(v.toString());
    }

    private static BigDecimal bd(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(v.toString());
    }

    private static Integer intVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString());
    }

    private static OffsetDateTime odt(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(v.toString());
    }
}
