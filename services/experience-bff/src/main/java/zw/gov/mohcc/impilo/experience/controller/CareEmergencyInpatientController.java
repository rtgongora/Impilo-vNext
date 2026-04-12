package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Care Pathways, Emergency Protocols, and Inpatient Management.
 *
 * Covers: care plans/goals/interventions, fluid balance, emergency activations,
 * resuscitation, APGAR, early warning scores, admissions, ward rounds,
 * observation charts, and patient transfers.
 */
@RestController
@RequestMapping("/internal/v1")
public class CareEmergencyInpatientController {

    private static final Logger log = LoggerFactory.getLogger(CareEmergencyInpatientController.class);

    private final JdbcTemplate jdbc; // TODO: remove after verification
    private final InpatientServiceClient inpatientClient;
    private final PctServiceClient pctClient;

    public CareEmergencyInpatientController(JdbcTemplate jdbc,
                                            InpatientServiceClient inpatientClient,
                                            PctServiceClient pctClient) {
        this.jdbc = jdbc;
        this.inpatientClient = inpatientClient;
        this.pctClient = pctClient;
    }

    // ── Care Plans ──────────────────────────────────────────────────

    @GetMapping("/care-plans")
    public ResponseEntity<Map<String, Object>> listCarePlans(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        // STRANGLER: delegate to PctServiceClient
        try { JsonNode pctData = pctClient.listCarePlans(patientId); if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData)); } catch (Exception e) { log.warn("PCT listCarePlans failed, falling back to local: {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — fallback to local JDBC
        List<Map<String, Object>> plans = jdbc.queryForList(
                "SELECT * FROM care_plans WHERE tenant_id = ? AND patient_id = ?::uuid ORDER BY created_at DESC", tenantId, patientId);
        for (Map<String, Object> plan : plans) {
            UUID planId = (UUID) plan.get("id");
            plan.put("goals", jdbc.queryForList("SELECT * FROM care_plan_goals WHERE care_plan_id = ? ORDER BY priority", planId));
            plan.put("interventions", jdbc.queryForList("SELECT * FROM care_plan_interventions WHERE care_plan_id = ? ORDER BY category", planId));
        }
        return ResponseEntity.ok(Map.of("data", plans));
    }

    @PostMapping("/care-plans")
    @Transactional
    public ResponseEntity<Map<String, Object>> createCarePlan(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.createCarePlan(body); } catch (Exception e) { log.warn("PCT createCarePlan failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO care_plans (id, tenant_id, patient_id, encounter_id, title, plan_type, created_by) VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?)",
                id, tenantId, body.get("patientId"), body.getOrDefault("encounterId", null),
                body.get("title"), body.getOrDefault("planType", "NURSING"), body.getOrDefault("createdBy", ""));

        @SuppressWarnings("unchecked") List<Map<String, Object>> goals = (List<Map<String, Object>>) body.getOrDefault("goals", List.of());
        for (Map<String, Object> g : goals) {
            UUID gId = UUID.randomUUID();
            jdbc.update("INSERT INTO care_plan_goals (id, care_plan_id, category, description, target_value, priority, target_date) VALUES (?, ?, ?, ?, ?, ?, ?::date)",
                    gId, id, g.getOrDefault("category", "CLINICAL"), g.get("description"),
                    g.getOrDefault("targetValue", ""), g.getOrDefault("priority", "MEDIUM"), g.getOrDefault("targetDate", null));
        }

        @SuppressWarnings("unchecked") List<Map<String, Object>> interventions = (List<Map<String, Object>>) body.getOrDefault("interventions", List.of());
        for (Map<String, Object> i : interventions) {
            jdbc.update("INSERT INTO care_plan_interventions (care_plan_id, category, description, frequency, responsible_role) VALUES (?, ?, ?, ?, ?)",
                    id, i.getOrDefault("category", "ASSESSMENT"), i.get("description"),
                    i.getOrDefault("frequency", ""), i.getOrDefault("responsibleRole", ""));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    // ── Fluid Balance ───────────────────────────────────────────────

    @GetMapping("/fluid-balance")
    public ResponseEntity<Map<String, Object>> getFluidBalance(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId, @RequestParam(required = false) String date) {
        // STRANGLER: delegate to PctServiceClient
        try { JsonNode pctData = pctClient.getFluidBalance(patientId, date); if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData)); } catch (Exception e) { log.warn("PCT getFluidBalance failed, falling back to local: {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — fallback to local JDBC
        String d = date != null ? date : java.time.LocalDate.now().toString();
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT * FROM fluid_balance_records WHERE tenant_id = ? AND patient_id = ?::uuid AND record_date = ?::date ORDER BY recorded_at", tenantId, patientId, d);
        int totalIntake = records.stream().filter(r -> "INTAKE".equals(r.get("entry_type"))).mapToInt(r -> (Integer) r.get("volume_ml")).sum();
        int totalOutput = records.stream().filter(r -> "OUTPUT".equals(r.get("entry_type"))).mapToInt(r -> (Integer) r.get("volume_ml")).sum();
        return ResponseEntity.ok(Map.of("data", records, "summary", Map.of("totalIntake", totalIntake, "totalOutput", totalOutput, "balance", totalIntake - totalOutput)));
    }

    @PostMapping("/fluid-balance")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordFluid(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.recordFluidBalance(body); } catch (Exception e) { log.warn("PCT recordFluidBalance failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fluid_balance_records (id, tenant_id, patient_id, encounter_id, entry_type, category, volume_ml, description, recorded_by) VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?)",
                id, tenantId, body.get("patientId"), body.getOrDefault("encounterId", null),
                body.get("entryType"), body.get("category"), Integer.parseInt(body.get("volumeMl").toString()),
                body.getOrDefault("description", ""), body.getOrDefault("recordedBy", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    // ── Emergency Activations ───────────────────────────────────────

    @GetMapping("/emergency/activations")
    public ResponseEntity<Map<String, Object>> listActivations(@RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: delegate to PctServiceClient
        try { JsonNode pctData = pctClient.listEmergencyActivations(); if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData)); } catch (Exception e) { log.warn("PCT listEmergencyActivations failed, falling back to local: {}", e.getMessage()); }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM emergency_activations WHERE tenant_id = ? ORDER BY activation_time DESC LIMIT 20", tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/emergency/activate")
    @Transactional
    public ResponseEntity<Map<String, Object>> activateEmergency(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.activateEmergency(body); } catch (Exception e) { log.warn("PCT activateEmergency failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO emergency_activations (id, tenant_id, encounter_id, patient_id, protocol_type, team_leader, location) VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?)",
                id, tenantId, body.getOrDefault("encounterId", null), body.getOrDefault("patientId", null),
                body.get("protocolType"), body.getOrDefault("teamLeader", ""), body.getOrDefault("location", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "status", "ACTIVE")));
    }

    @PostMapping("/emergency/{id}/action")
    @Transactional
    public ResponseEntity<Map<String, Object>> logAction(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.logEmergencyAction(id.toString(), body); } catch (Exception e) { log.warn("PCT logEmergencyAction failed (non-blocking): {}", e.getMessage()); }
        jdbc.update("INSERT INTO emergency_actions (activation_id, action_type, description, performed_by) VALUES (?, ?, ?, ?)",
                id, body.get("actionType"), body.get("description"), body.getOrDefault("performedBy", ""));
        return ResponseEntity.ok(Map.of("logged", true));
    }

    @PostMapping("/emergency/{id}/end")
    @Transactional
    public ResponseEntity<Map<String, Object>> endEmergency(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.endEmergency(id.toString(), body); } catch (Exception e) { log.warn("PCT endEmergency failed (non-blocking): {}", e.getMessage()); }
        jdbc.update("UPDATE emergency_activations SET status = 'ENDED', outcome = ?, ended_at = NOW(), notes = ? WHERE id = ?",
                body.getOrDefault("outcome", "STABILISED"), body.getOrDefault("notes", ""), id);
        return ResponseEntity.ok(Map.of("status", "ENDED"));
    }

    // ── Resuscitation Records ───────────────────────────────────────

    @PostMapping("/emergency/{activationId}/resuscitation")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordResuscitation(@PathVariable UUID activationId, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.recordResuscitation(activationId.toString(), body); } catch (Exception e) { log.warn("PCT recordResuscitation failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO resuscitation_records (id, activation_id, cpr_cycles, defibrillations, initial_rhythm, final_rhythm, rosc_achieved, medications) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                id, activationId, Integer.parseInt(body.getOrDefault("cprCycles", "0").toString()),
                Integer.parseInt(body.getOrDefault("defibrillations", "0").toString()),
                body.getOrDefault("initialRhythm", ""), body.getOrDefault("finalRhythm", ""),
                Boolean.parseBoolean(body.getOrDefault("roscAchieved", "false").toString()),
                body.getOrDefault("medications", "[]").toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    // ── APGAR Scores ────────────────────────────────────────────────

    @PostMapping("/apgar")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordApgar(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.recordApgar(body); } catch (Exception e) { log.warn("PCT recordApgar failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO apgar_scores (id, tenant_id, patient_id, encounter_id, minute, appearance, pulse, grimace, activity, respiration) VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)",
                id, tenantId, body.get("patientId"), body.getOrDefault("encounterId", null),
                Integer.parseInt(body.get("minute").toString()),
                Integer.parseInt(body.get("appearance").toString()), Integer.parseInt(body.get("pulse").toString()),
                Integer.parseInt(body.get("grimace").toString()), Integer.parseInt(body.get("activity").toString()),
                Integer.parseInt(body.get("respiration").toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/apgar")
    public ResponseEntity<Map<String, Object>> getApgar(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        // STRANGLER: delegate to PctServiceClient
        try { JsonNode pctData = pctClient.getApgar(patientId); if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData)); } catch (Exception e) { log.warn("PCT getApgar failed, falling back to local: {}", e.getMessage()); }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM apgar_scores WHERE tenant_id = ? AND patient_id = ?::uuid ORDER BY minute", tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    // ── Early Warning Scores ────────────────────────────────────────

    @PostMapping("/ews")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordEWS(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.recordEWS(body); } catch (Exception e) { log.warn("PCT recordEWS failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        int totalScore = Integer.parseInt(body.get("totalScore").toString());
        String riskLevel = totalScore >= 7 ? "HIGH" : totalScore >= 5 ? "MEDIUM" : totalScore >= 1 ? "LOW" : "NONE";
        jdbc.update("INSERT INTO early_warning_scores (id, tenant_id, patient_id, encounter_id, score_type, total_score, risk_level, components, escalation_required, recorded_by) VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?::jsonb, ?, ?)",
                id, tenantId, body.get("patientId"), body.getOrDefault("encounterId", null),
                body.getOrDefault("scoreType", "NEWS2"), totalScore, riskLevel,
                body.getOrDefault("components", "{}").toString(), totalScore >= 7, body.getOrDefault("recordedBy", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "riskLevel", riskLevel, "escalationRequired", totalScore >= 7)));
    }

    @GetMapping("/ews")
    public ResponseEntity<Map<String, Object>> getEWS(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        // STRANGLER: delegate to PctServiceClient
        try { JsonNode pctData = pctClient.getEWS(patientId); if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData)); } catch (Exception e) { log.warn("PCT getEWS failed, falling back to local: {}", e.getMessage()); }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM early_warning_scores WHERE tenant_id = ? AND patient_id = ?::uuid ORDER BY recorded_at DESC LIMIT 20", tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    // ── Admissions ──────────────────────────────────────────────────

    @PostMapping("/admissions")
    @Transactional
    public ResponseEntity<Map<String, Object>> createAdmission(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to InpatientServiceClient first
        try { inpatientClient.createAdmission(body); } catch (Exception e) { log.warn("Inpatient createAdmission failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO admissions (id, tenant_id, patient_id, encounter_id, bed_id, ward_id, admission_type, admitting_doctor, admitting_diagnosis, diet_orders, activity_level, isolation_type, allergies_noted, dvt_prophylaxis) VALUES (?, ?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, body.get("patientId"), body.getOrDefault("encounterId", null),
                body.getOrDefault("bedId", null) != null ? UUID.fromString(body.get("bedId").toString()) : null,
                body.getOrDefault("wardId", null) != null ? UUID.fromString(body.get("wardId").toString()) : null,
                body.getOrDefault("admissionType", "EMERGENCY"), body.getOrDefault("admittingDoctor", ""),
                body.getOrDefault("admittingDiagnosis", ""), body.getOrDefault("dietOrders", "REGULAR"),
                body.getOrDefault("activityLevel", "BED_REST"), body.getOrDefault("isolationType", null),
                Boolean.parseBoolean(body.getOrDefault("allergiesNoted", "false").toString()),
                Boolean.parseBoolean(body.getOrDefault("dvtProphylaxis", "false").toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/admissions")
    public ResponseEntity<Map<String, Object>> listAdmissions(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam(required = false) String patientId) {
        // STRANGLER: delegate to InpatientServiceClient
        try { JsonNode data = inpatientClient.listAdmissions(patientId); if (data != null) return ResponseEntity.ok(Map.of("data", data)); } catch (Exception e) { log.warn("Inpatient listAdmissions failed, falling back to local: {}", e.getMessage()); }
        String sql = patientId != null
                ? "SELECT * FROM admissions WHERE tenant_id = ? AND patient_id = ?::uuid AND status = 'ACTIVE' ORDER BY admission_date DESC"
                : "SELECT * FROM admissions WHERE tenant_id = ? AND status = 'ACTIVE' ORDER BY admission_date DESC LIMIT 50";
        List<Map<String, Object>> rows = patientId != null ? jdbc.queryForList(sql, tenantId, patientId) : jdbc.queryForList(sql, tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    // ── Ward Rounds ─────────────────────────────────────────────────

    @PostMapping("/ward-rounds")
    @Transactional
    public ResponseEntity<Map<String, Object>> startWardRound(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ward_rounds (id, tenant_id, ward_id, round_type, led_by) VALUES (?, ?, ?::uuid, ?, ?)",
                id, tenantId, UUID.fromString(body.get("wardId").toString()),
                body.getOrDefault("roundType", "MORNING"), body.getOrDefault("ledBy", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/ward-rounds/{id}/entries")
    @Transactional
    public ResponseEntity<Map<String, Object>> addRoundEntry(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID entryId = UUID.randomUUID();
        jdbc.update("INSERT INTO ward_round_entries (id, round_id, patient_id, bed_id, assessment, plan, new_orders, escalation, reviewed_by) VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?)",
                entryId, id, body.get("patientId"), body.getOrDefault("bedId", null) != null ? UUID.fromString(body.get("bedId").toString()) : null,
                body.getOrDefault("assessment", ""), body.getOrDefault("plan", ""), body.getOrDefault("newOrders", ""),
                body.getOrDefault("escalation", null), body.getOrDefault("reviewedBy", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", entryId)));
    }

    @GetMapping("/ward-rounds")
    public ResponseEntity<Map<String, Object>> listWardRounds(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String wardId) {
        List<Map<String, Object>> rounds = jdbc.queryForList("SELECT * FROM ward_rounds WHERE tenant_id = ? AND ward_id = ?::uuid ORDER BY started_at DESC LIMIT 10", tenantId, wardId);
        return ResponseEntity.ok(Map.of("data", rounds));
    }

    // ── Observation Charts ──────────────────────────────────────────

    @PostMapping("/observations")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordObservation(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.recordObservation(body); } catch (Exception e) { log.warn("PCT recordObservation failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO observation_entries (id, tenant_id, patient_id, encounter_id, chart_type, parameters, recorded_by) VALUES (?, ?, ?::uuid, ?::uuid, ?, ?::jsonb, ?)",
                id, tenantId, body.get("patientId"), body.getOrDefault("encounterId", null),
                body.getOrDefault("chartType", "VITALS"), body.get("parameters").toString(), body.getOrDefault("recordedBy", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/observations")
    public ResponseEntity<Map<String, Object>> getObservations(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        // STRANGLER: delegate to PctServiceClient
        try { JsonNode pctData = pctClient.getObservations(patientId); if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData)); } catch (Exception e) { log.warn("PCT getObservations failed, falling back to local: {}", e.getMessage()); }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM observation_entries WHERE tenant_id = ? AND patient_id = ?::uuid ORDER BY recorded_at DESC LIMIT 50", tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    // ── Patient Transfers ───────────────────────────────────────────

    @PostMapping("/transfers")
    @Transactional
    public ResponseEntity<Map<String, Object>> requestTransfer(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to InpatientServiceClient first
        try { inpatientClient.transferPatient(null, body); } catch (Exception e) { log.warn("Inpatient requestTransfer failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patient_transfers (id, tenant_id, patient_id, from_ward_id, from_bed_id, to_ward_id, to_bed_id, reason, clinical_notes, transfer_type, requested_by) VALUES (?, ?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?)",
                id, tenantId, body.get("patientId"),
                body.getOrDefault("fromWardId", null) != null ? UUID.fromString(body.get("fromWardId").toString()) : null,
                body.getOrDefault("fromBedId", null) != null ? UUID.fromString(body.get("fromBedId").toString()) : null,
                body.getOrDefault("toWardId", null) != null ? UUID.fromString(body.get("toWardId").toString()) : null,
                body.getOrDefault("toBedId", null) != null ? UUID.fromString(body.get("toBedId").toString()) : null,
                body.getOrDefault("reason", "CLINICAL"), body.getOrDefault("clinicalNotes", ""),
                body.getOrDefault("transferType", "INTERNAL"), body.getOrDefault("requestedBy", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "status", "REQUESTED")));
    }

    @PostMapping("/transfers/{id}/accept")
    @Transactional
    public ResponseEntity<Map<String, Object>> acceptTransfer(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE patient_transfers SET status = 'COMPLETED', accepted_by = ?, completed_at = NOW() WHERE id = ?",
                body.getOrDefault("acceptedBy", ""), id);
        return ResponseEntity.ok(Map.of("status", "COMPLETED"));
    }

    @GetMapping("/transfers")
    public ResponseEntity<Map<String, Object>> listTransfers(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam(required = false) String patientId) {
        String sql = patientId != null
                ? "SELECT * FROM patient_transfers WHERE tenant_id = ? AND patient_id = ?::uuid ORDER BY requested_at DESC"
                : "SELECT * FROM patient_transfers WHERE tenant_id = ? ORDER BY requested_at DESC LIMIT 20";
        List<Map<String, Object>> rows = patientId != null ? jdbc.queryForList(sql, tenantId, patientId) : jdbc.queryForList(sql, tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }
}
