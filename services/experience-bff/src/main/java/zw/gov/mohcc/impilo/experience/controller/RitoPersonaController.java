package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.RitoServiceClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Persona-scoped Rito entry points. Each persona surface is a curated, intention-revealing
 * slice of the same downstream service — the BFF adapts <em>visibility</em> by persona, it does
 * not fork case truth. All calls forward the inbound trust context.
 *
 * <ul>
 *   <li>{@code /nompilo/rito} + {@code /mobile/rito} — citizen/caregiver: submit &amp; track feedback, surveys.</li>
 *   <li>{@code /work/rito} — provider workspace: report safety incidents, my assigned cases &amp; actions.</li>
 *   <li>{@code /facility/rito} — facility mode: dashboard, triage/assign/escalate, audits.</li>
 *   <li>{@code /above-site/rito} — district/province/national supervisor: rollup dashboards, escalations.</li>
 * </ul>
 */
@RestController
public class RitoPersonaController {

    private final RitoServiceClient rito;

    public RitoPersonaController(RitoServiceClient rito) {
        this.rito = rito;
    }

    // ── Citizen / caregiver (Nompilo + mobile parity) ────────────────
    @PostMapping({"/internal/v1/nompilo/rito/cases", "/internal/v1/mobile/rito/cases"})
    public ResponseEntity<JsonNode> citizenSubmit(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.createCase(body));
    }

    @GetMapping({"/internal/v1/nompilo/rito/cases/{id}", "/internal/v1/mobile/rito/cases/{id}"})
    public ResponseEntity<JsonNode> citizenTrack(@PathVariable String id) {
        return ResponseEntity.ok(rito.getCase(id));
    }

    @GetMapping({"/internal/v1/nompilo/rito/cases/{id}/timeline", "/internal/v1/mobile/rito/cases/{id}/timeline"})
    public ResponseEntity<JsonNode> citizenTimeline(@PathVariable String id) {
        return ResponseEntity.ok(rito.caseSubResource(id, "timeline"));
    }

    @GetMapping({"/internal/v1/nompilo/rito/surveys", "/internal/v1/mobile/rito/surveys"})
    public ResponseEntity<JsonNode> citizenSurveys() {
        return ResponseEntity.ok(rito.listSurveys());
    }

    @PostMapping({"/internal/v1/nompilo/rito/surveys/{id}/responses", "/internal/v1/mobile/rito/surveys/{id}/responses"})
    public ResponseEntity<JsonNode> citizenSurveyResponse(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.submitSurveyResponse(id, body));
    }

    // ── Provider workspace (Work mode) ───────────────────────────────
    @PostMapping({"/internal/v1/work/rito/cases", "/internal/v1/mobile/rito/work/cases"})
    public ResponseEntity<JsonNode> providerReport(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.createCase(body));
    }

    @PostMapping("/internal/v1/work/rito/cases/{caseId}/safety-incident")
    public ResponseEntity<JsonNode> providerIncidentDetail(
            @PathVariable String caseId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.recordSafetyIncident(caseId, body));
    }

    @GetMapping("/internal/v1/work/rito/cases")
    public ResponseEntity<JsonNode> providerMyCases(
            @RequestParam(value = "assigned_to", required = false) String assignedTo,
            @RequestParam(required = false) String status) {
        Map<String, String> f = new HashMap<>();
        f.put("assigned_to", assignedTo);
        f.put("status", status);
        return ResponseEntity.ok(rito.listCases(f));
    }

    @GetMapping("/internal/v1/work/rito/corrective-actions")
    public ResponseEntity<JsonNode> providerCorrectiveActions(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(rito.listCorrectiveActions(status));
    }

    @PostMapping("/internal/v1/work/rito/corrective-actions/{id}/{action:progress|complete}")
    public ResponseEntity<JsonNode> providerCaAction(
            @PathVariable String id, @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(rito.correctiveActionAction(id, action, body != null ? body : Map.of()));
    }

    // ── Facility mode (quality focal / manager / safety lead) ────────
    @GetMapping({"/internal/v1/facility/rito/dashboard", "/internal/v1/mobile/rito/facility/dashboard"})
    public ResponseEntity<JsonNode> facilityDashboard(
            @RequestParam(value = "facility_id", required = false) String facilityId) {
        return ResponseEntity.ok(rito.dashboard("facility", facilityId));
    }

    @GetMapping("/internal/v1/facility/rito/cases")
    public ResponseEntity<JsonNode> facilityCases(
            @RequestParam(value = "facility_id", required = false) String facilityId,
            @RequestParam(required = false) String status) {
        Map<String, String> f = new HashMap<>();
        f.put("facility_id", facilityId);
        f.put("status", status);
        return ResponseEntity.ok(rito.listCases(f));
    }

    @PostMapping("/internal/v1/facility/rito/cases/{id}/{action:acknowledge|triage|assign|escalate|resolve|close}")
    public ResponseEntity<JsonNode> facilityCaseAction(
            @PathVariable String id, @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(rito.caseAction(id, action, body != null ? body : Map.of()));
    }

    @GetMapping("/internal/v1/facility/rito/audits")
    public ResponseEntity<JsonNode> facilityAudits(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(rito.listAudits(status));
    }

    @GetMapping("/internal/v1/facility/rito/safety-incidents/sentinel")
    public ResponseEntity<JsonNode> facilitySentinel() {
        return ResponseEntity.ok(rito.sentinelIncidents());
    }

    // ── Above-site (district / province / national supervisor) ───────
    @GetMapping("/internal/v1/above-site/rito/dashboard")
    public ResponseEntity<JsonNode> aboveSiteDashboard() {
        return ResponseEntity.ok(rito.dashboard("above-site", null));
    }

    @GetMapping("/internal/v1/above-site/rito/safety")
    public ResponseEntity<JsonNode> aboveSiteSafety() {
        return ResponseEntity.ok(rito.dashboard("safety", null));
    }

    @GetMapping("/internal/v1/above-site/rito/client-voice")
    public ResponseEntity<JsonNode> aboveSiteClientVoice() {
        return ResponseEntity.ok(rito.dashboard("client-voice", null));
    }

    @GetMapping("/internal/v1/above-site/rito/cases")
    public ResponseEntity<JsonNode> aboveSiteCases(
            @RequestParam(required = false) String status,
            @RequestParam(value = "case_type", required = false) String caseType) {
        Map<String, String> f = new HashMap<>();
        f.put("status", status);
        f.put("case_type", caseType);
        return ResponseEntity.ok(rito.listCases(f));
    }

    @PostMapping("/internal/v1/above-site/rito/cases/{id}/escalate")
    public ResponseEntity<JsonNode> aboveSiteEscalate(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(rito.caseAction(id, "escalate", body != null ? body : Map.of()));
    }
}
