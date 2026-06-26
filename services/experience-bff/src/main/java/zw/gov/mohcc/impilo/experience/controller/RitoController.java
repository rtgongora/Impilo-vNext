package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.RitoServiceClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Experience-BFF facade for Rito (quality, safety &amp; client voice). Composition/orchestration
 * only — it never owns case truth; every call proxies to rito-quality-safety-service with the
 * inbound trust context forwarded. Persona-scoped entry points live in {@link RitoPersonaController}.
 */
@RestController
@RequestMapping("/internal/v1/rito")
public class RitoController {

    private final RitoServiceClient rito;

    public RitoController(RitoServiceClient rito) {
        this.rito = rito;
    }

    // ── Cases ────────────────────────────────────────────────────────
    @GetMapping("/cases")
    public ResponseEntity<JsonNode> listCases(
            @RequestParam(required = false) String status,
            @RequestParam(value = "case_type", required = false) String caseType,
            @RequestParam(value = "facility_id", required = false) String facilityId,
            @RequestParam(value = "assigned_to", required = false) String assignedTo) {
        Map<String, String> f = new HashMap<>();
        f.put("status", status);
        f.put("case_type", caseType);
        f.put("facility_id", facilityId);
        f.put("assigned_to", assignedTo);
        return ResponseEntity.ok(rito.listCases(f));
    }

    @GetMapping("/cases/{id}")
    public ResponseEntity<JsonNode> getCase(@PathVariable String id) {
        return ResponseEntity.ok(rito.getCase(id));
    }

    @PostMapping("/cases")
    public ResponseEntity<JsonNode> createCase(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.createCase(body));
    }

    @GetMapping("/cases/{id}/{sub:timeline|parties|links|messages}")
    public ResponseEntity<JsonNode> caseSub(@PathVariable String id, @PathVariable String sub) {
        return ResponseEntity.ok(rito.caseSubResource(id, sub));
    }

    @PostMapping("/cases/{id}/{action:acknowledge|triage|assign|transition|escalate|resolve|close|reopen|cancel|messages|parties|links|attachments}")
    public ResponseEntity<JsonNode> caseAction(
            @PathVariable String id, @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode result = rito.caseAction(id, action, body != null ? body : Map.of());
        boolean created = action.equals("messages") || action.equals("parties")
                || action.equals("links") || action.equals("attachments");
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    // ── Safety incident detail ───────────────────────────────────────
    @PostMapping("/cases/{caseId}/safety-incident")
    public ResponseEntity<JsonNode> recordSafetyIncident(
            @PathVariable String caseId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.recordSafetyIncident(caseId, body));
    }

    @GetMapping("/cases/{caseId}/safety-incident")
    public ResponseEntity<JsonNode> getSafetyIncident(@PathVariable String caseId) {
        return ResponseEntity.ok(rito.getSafetyIncident(caseId));
    }

    @GetMapping("/safety-incidents/sentinel")
    public ResponseEntity<JsonNode> sentinel() {
        return ResponseEntity.ok(rito.sentinelIncidents());
    }

    // ── Quality signals ──────────────────────────────────────────────
    @GetMapping("/quality-signals")
    public ResponseEntity<JsonNode> listSignals(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(rito.listSignals(status));
    }

    @PostMapping("/quality-signals/{id}/{action:review|convert}")
    public ResponseEntity<JsonNode> signalAction(
            @PathVariable String id, @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode r = rito.signalAction(id, action, body != null ? body : Map.of());
        return ResponseEntity.status(action.equals("convert") ? HttpStatus.CREATED : HttpStatus.OK).body(r);
    }

    // ── Audits ───────────────────────────────────────────────────────
    @GetMapping("/audits")
    public ResponseEntity<JsonNode> listAudits(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(rito.listAudits(status));
    }

    @GetMapping("/audits/{id}")
    public ResponseEntity<JsonNode> getAudit(@PathVariable String id) {
        return ResponseEntity.ok(rito.getAudit(id));
    }

    @PostMapping("/audits")
    public ResponseEntity<JsonNode> createAudit(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.createAudit(body));
    }

    @GetMapping("/audits/{id}/findings")
    public ResponseEntity<JsonNode> auditFindings(@PathVariable String id) {
        return ResponseEntity.ok(rito.auditFindings(id));
    }

    @PostMapping("/audits/{id}/{action:start|findings|submit|close}")
    public ResponseEntity<JsonNode> auditAction(
            @PathVariable String id, @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode r = rito.auditAction(id, action, body != null ? body : Map.of());
        return ResponseEntity.status(action.equals("findings") ? HttpStatus.CREATED : HttpStatus.OK).body(r);
    }

    @GetMapping("/audit-tools")
    public ResponseEntity<JsonNode> listAuditTools() {
        return ResponseEntity.ok(rito.listAuditTools());
    }

    @PostMapping("/audit-tools")
    public ResponseEntity<JsonNode> createAuditTool(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.createAuditTool(body));
    }

    // ── Improvement: CAPA + QI ───────────────────────────────────────
    @GetMapping("/corrective-actions")
    public ResponseEntity<JsonNode> listCorrectiveActions(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(rito.listCorrectiveActions(status));
    }

    @PostMapping("/corrective-actions")
    public ResponseEntity<JsonNode> createCorrectiveAction(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.createCorrectiveAction(body));
    }

    @PostMapping("/corrective-actions/{id}/{action:progress|complete|verify|cancel}")
    public ResponseEntity<JsonNode> caAction(
            @PathVariable String id, @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(rito.correctiveActionAction(id, action, body != null ? body : Map.of()));
    }

    @GetMapping("/qi-plans")
    public ResponseEntity<JsonNode> listQiPlans(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(rito.listQiPlans(status));
    }

    @PostMapping("/qi-plans")
    public ResponseEntity<JsonNode> createQiPlan(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.createQiPlan(body));
    }

    // ── Surveys ──────────────────────────────────────────────────────
    @GetMapping("/surveys")
    public ResponseEntity<JsonNode> listSurveys() {
        return ResponseEntity.ok(rito.listSurveys());
    }

    @PostMapping("/surveys")
    public ResponseEntity<JsonNode> createSurvey(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.createSurvey(body));
    }

    @PostMapping("/surveys/{id}/responses")
    public ResponseEntity<JsonNode> submitResponse(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.submitSurveyResponse(id, body));
    }

    @GetMapping("/surveys/{id}/aggregate")
    public ResponseEntity<JsonNode> surveyAggregate(@PathVariable String id) {
        return ResponseEntity.ok(rito.surveyAggregate(id));
    }

    // ── Dashboards ───────────────────────────────────────────────────
    @GetMapping("/dashboards/{kind:facility|above-site|client-voice|safety}")
    public ResponseEntity<JsonNode> dashboard(
            @PathVariable String kind,
            @RequestParam(value = "facility_id", required = false) String facilityId) {
        return ResponseEntity.ok(rito.dashboard(kind, facilityId));
    }

    // ── Config ───────────────────────────────────────────────────────
    @GetMapping("/config/sla-policies")
    public ResponseEntity<JsonNode> listSlaPolicies() {
        return ResponseEntity.ok(rito.listSlaPolicies());
    }

    @PostMapping("/config/sla-policies")
    public ResponseEntity<JsonNode> createSlaPolicy(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.createSlaPolicy(body));
    }

    @GetMapping("/config/escalation-rules")
    public ResponseEntity<JsonNode> listEscalationRules() {
        return ResponseEntity.ok(rito.listEscalationRules());
    }
}
