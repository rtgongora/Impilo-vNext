package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Clinical depth enhancements — discharge clearances, resuscitation phases,
 * CPR cycle tracking, resuscitation medications.
 */
@RestController
@RequestMapping("/internal/v1")
public class ClinicalDepthController {

    private final JdbcTemplate jdbc;

    public ClinicalDepthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Discharge Clearances ────────────────────────────────────────

    @PostMapping("/discharge-clearances/init")
    @Transactional
    public ResponseEntity<Map<String, Object>> initClearances(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        String encounterId = body.get("encounterId").toString();
        String patientId = body.get("patientId").toString();
        String[] types = {"CLINICAL", "NURSING", "PHARMACY", "LABORATORY", "IMAGING", "FINANCIAL", "ADMINISTRATIVE", "RECORDS", "CRVS"};
        List<Map<String, Object>> created = new ArrayList<>();
        for (String type : types) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO discharge_clearances (id, tenant_id, encounter_id, patient_id, clearance_type) VALUES (?, ?, ?::uuid, ?::uuid, ?)",
                    id, tenantId, encounterId, patientId, type);
            created.add(Map.of("id", id.toString(), "type", type, "status", "PENDING"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
    }

    @GetMapping("/discharge-clearances")
    public ResponseEntity<Map<String, Object>> getClearances(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String encounterId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM discharge_clearances WHERE tenant_id = ? AND encounter_id = ?::uuid ORDER BY created_at",
                tenantId, encounterId);
        long total = rows.size();
        long cleared = rows.stream().filter(r -> "CLEARED".equals(r.get("status")) || "WAIVED".equals(r.get("status"))).count();
        return ResponseEntity.ok(Map.of("data", rows, "progress", Map.of("total", total, "cleared", cleared, "percent", total > 0 ? Math.round((double) cleared / total * 100) : 0)));
    }

    @PostMapping("/discharge-clearances/{id}/clear")
    @Transactional
    public ResponseEntity<Map<String, Object>> clearItem(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE discharge_clearances SET status = 'CLEARED', cleared_by = ?, cleared_at = NOW(), notes = ? WHERE id = ?",
                body.getOrDefault("clearedBy", ""), body.getOrDefault("notes", ""), id);
        return ResponseEntity.ok(Map.of("status", "CLEARED"));
    }

    @PostMapping("/discharge-clearances/{id}/waive")
    @Transactional
    public ResponseEntity<Map<String, Object>> waiveItem(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE discharge_clearances SET status = 'WAIVED', waived = true, waived_reason = ?, cleared_by = ?, cleared_at = NOW() WHERE id = ?",
                body.getOrDefault("reason", ""), body.getOrDefault("clearedBy", ""), id);
        return ResponseEntity.ok(Map.of("status", "WAIVED"));
    }

    // ── Resuscitation Phases ────────────────────────────────────────

    @PostMapping("/emergency/{activationId}/phases")
    @Transactional
    public ResponseEntity<Map<String, Object>> startPhase(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO resuscitation_phases (id, activation_id, phase, rhythm) VALUES (?, ?, ?, ?)",
                id, activationId, body.get("phase"), body.getOrDefault("rhythm", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/emergency/{activationId}/phases/{phaseId}/end")
    @Transactional
    public ResponseEntity<Map<String, Object>> endPhase(
            @PathVariable UUID activationId,
            @PathVariable UUID phaseId,
            @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE resuscitation_phases SET ended_at = NOW(), cpr_cycles = ?, defibrillations = ?, notes = ? WHERE id = ?",
                Integer.parseInt(body.getOrDefault("cprCycles", "0").toString()),
                Integer.parseInt(body.getOrDefault("defibrillations", "0").toString()),
                body.getOrDefault("notes", ""), phaseId);
        return ResponseEntity.ok(Map.of("ended", true));
    }

    @GetMapping("/emergency/{activationId}/phases")
    public ResponseEntity<Map<String, Object>> getPhases(@PathVariable UUID activationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM resuscitation_phases WHERE activation_id = ? ORDER BY started_at", activationId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    // ── CPR Cycles ──────────────────────────────────────────────────

    @PostMapping("/emergency/{activationId}/cpr-cycles")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordCPRCycle(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cpr_cycles (id, activation_id, cycle_number, duration_secs, quality, compressor) VALUES (?, ?, ?, ?, ?, ?)",
                id, activationId, Integer.parseInt(body.get("cycleNumber").toString()),
                Integer.parseInt(body.getOrDefault("durationSecs", "120").toString()),
                body.getOrDefault("quality", "ADEQUATE"), body.getOrDefault("compressor", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/emergency/{activationId}/cpr-cycles")
    public ResponseEntity<Map<String, Object>> getCPRCycles(@PathVariable UUID activationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM cpr_cycles WHERE activation_id = ? ORDER BY cycle_number", activationId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    // ── Resuscitation Medications ───────────────────────────────────

    @PostMapping("/emergency/{activationId}/medications")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordResusMed(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO resuscitation_medications (id, activation_id, medication, dose, route, administered_by) VALUES (?, ?, ?, ?, ?, ?)",
                id, activationId, body.get("medication"), body.get("dose"),
                body.getOrDefault("route", "IV"), body.getOrDefault("administeredBy", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/emergency/{activationId}/medications")
    public ResponseEntity<Map<String, Object>> getResusMeds(@PathVariable UUID activationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM resuscitation_medications WHERE activation_id = ? ORDER BY administered_at", activationId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    // ── Care Plan Goal/Intervention CRUD ────────────────────────────

    @PostMapping("/care-plans/{planId}/goals")
    @Transactional
    public ResponseEntity<Map<String, Object>> addGoal(
            @PathVariable UUID planId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO care_plan_goals (id, care_plan_id, category, description, target_value, priority, target_date) VALUES (?, ?, ?, ?, ?, ?, ?::date)",
                id, planId, body.getOrDefault("category", "CLINICAL"), body.get("description"),
                body.getOrDefault("targetValue", ""), body.getOrDefault("priority", "MEDIUM"), body.getOrDefault("targetDate", null));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/care-plans/{planId}/goals/{goalId}/update")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateGoal(
            @PathVariable UUID planId, @PathVariable UUID goalId,
            @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE care_plan_goals SET current_value = ?, status = ? WHERE id = ? AND care_plan_id = ?",
                body.getOrDefault("currentValue", ""), body.getOrDefault("status", "IN_PROGRESS"), goalId, planId);
        if ("ACHIEVED".equals(body.get("status"))) {
            jdbc.update("UPDATE care_plan_goals SET achieved_at = NOW() WHERE id = ?", goalId);
        }
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping("/care-plans/{planId}/interventions")
    @Transactional
    public ResponseEntity<Map<String, Object>> addIntervention(
            @PathVariable UUID planId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO care_plan_interventions (id, care_plan_id, goal_id, category, description, frequency, responsible_role) VALUES (?, ?, ?::uuid, ?, ?, ?, ?)",
                id, planId, body.getOrDefault("goalId", null), body.getOrDefault("category", "ASSESSMENT"),
                body.get("description"), body.getOrDefault("frequency", ""), body.getOrDefault("responsibleRole", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/care-plans/{planId}/interventions/{intId}/perform")
    @Transactional
    public ResponseEntity<Map<String, Object>> performIntervention(
            @PathVariable UUID planId, @PathVariable UUID intId) {
        jdbc.update("UPDATE care_plan_interventions SET last_performed = NOW() WHERE id = ? AND care_plan_id = ?", intId, planId);
        return ResponseEntity.ok(Map.of("performed", true));
    }

    // ── NEWS2 Component Scoring ─────────────────────────────────────

    @PostMapping("/ews/news2")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordNEWS2Components(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        // NEWS2 component scoring: each parameter 0-3 points
        int rrScore = toInt(body.getOrDefault("respiratoryRateScore", 0));
        int spo2Score = toInt(body.getOrDefault("spo2Score", 0));
        int spo2ScaleScore = toInt(body.getOrDefault("spo2ScaleScore", 0));
        int airO2Score = toInt(body.getOrDefault("airOrOxygenScore", 0));
        int bpScore = toInt(body.getOrDefault("systolicBPScore", 0));
        int hrScore = toInt(body.getOrDefault("heartRateScore", 0));
        int consScore = toInt(body.getOrDefault("consciousnessScore", 0));
        int tempScore = toInt(body.getOrDefault("temperatureScore", 0));

        int totalScore = rrScore + spo2Score + spo2ScaleScore + airO2Score + bpScore + hrScore + consScore + tempScore;
        String riskLevel = totalScore >= 7 ? "HIGH" : totalScore >= 5 ? "MEDIUM" : consScore == 3 ? "MEDIUM" : totalScore >= 1 ? "LOW" : "NONE";
        boolean escalation = totalScore >= 7 || consScore == 3;

        String components = String.format(
                "{\"respiratoryRate\":%d,\"spo2\":%d,\"spo2Scale\":%d,\"airOrOxygen\":%d,\"systolicBP\":%d,\"heartRate\":%d,\"consciousness\":%d,\"temperature\":%d}",
                rrScore, spo2Score, spo2ScaleScore, airO2Score, bpScore, hrScore, consScore, tempScore);

        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO early_warning_scores (id, tenant_id, patient_id, encounter_id, score_type, total_score, risk_level, components, escalation_required, recorded_by) VALUES (?, ?, ?::uuid, ?::uuid, 'NEWS2', ?, ?, ?::jsonb, ?, ?)",
                id, tenantId, body.get("patientId"), body.getOrDefault("encounterId", null),
                totalScore, riskLevel, components, escalation, body.getOrDefault("recordedBy", ""));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of(
                "id", id, "totalScore", totalScore, "riskLevel", riskLevel,
                "escalationRequired", escalation, "components", components)));
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        return Integer.parseInt(val.toString());
    }
}
