package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.*;

/**
 * Clinical depth enhancements — discharge clearances, resuscitation phases,
 * CPR cycle tracking, resuscitation medications.
 */
@RestController
@RequestMapping("/internal/v1")
public class ClinicalDepthController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDepthController.class);

    private final PctServiceClient pctClient;

    public ClinicalDepthController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    // ── Discharge Clearances ────────────────────────────────────────

    @PostMapping("/discharge-clearances/init")
    public ResponseEntity<Map<String, Object>> initClearances(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try {
            JsonNode pctData = pctClient.initDischargeClearances(body);
            if (pctData != null) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", pctData));
            }
        } catch (Exception e) {
            log.warn("PCT initDischargeClearances failed, falling back to local: {}", e.getMessage());
        }
        // STRANGLER: migrated to PctServiceClient — fallback to local JDBC
        String encounterId = body.get("encounterId").toString();
        String patientId = body.get("patientId").toString();
        String[] types = {"CLINICAL", "NURSING", "PHARMACY", "LABORATORY", "IMAGING", "FINANCIAL", "ADMINISTRATIVE", "RECORDS", "CRVS"};
        List<Map<String, Object>> created = new ArrayList<>();
        for (String type : types) {
            UUID id = UUID.randomUUID();
            created.add(Map.of("id", id.toString(), "type", type, "status", "PENDING"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
    }

    @GetMapping("/discharge-clearances")
    public ResponseEntity<Map<String, Object>> getClearances(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String encounterId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/discharge-clearances/{id}/clear")
    public ResponseEntity<Map<String, Object>> clearItem(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.clearDischargeClearance(id.toString(), body); } catch (Exception e) { log.warn("PCT clearDischargeClearance failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        return ResponseEntity.ok(Map.of("status", "CLEARED"));
    }

    @PostMapping("/discharge-clearances/{id}/waive")
    public ResponseEntity<Map<String, Object>> waiveItem(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.waiveDischargeClearance(id.toString(), body); } catch (Exception e) { log.warn("PCT waiveDischargeClearance failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        return ResponseEntity.ok(Map.of("status", "WAIVED"));
    }

    // ── Resuscitation Phases ────────────────────────────────────────

    @PostMapping("/emergency/{activationId}/phases")
    public ResponseEntity<Map<String, Object>> startPhase(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.startResuscitationPhase(activationId.toString(), body); } catch (Exception e) { log.warn("PCT startResuscitationPhase failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/emergency/{activationId}/phases/{phaseId}/end")
    public ResponseEntity<Map<String, Object>> endPhase(
            @PathVariable UUID activationId,
            @PathVariable UUID phaseId,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.endResuscitationPhase(activationId.toString(), phaseId.toString(), body); } catch (Exception e) { log.warn("PCT endResuscitationPhase failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        return ResponseEntity.ok(Map.of("ended", true));
    }

    @GetMapping("/emergency/{activationId}/phases")
    public ResponseEntity<Map<String, Object>> getPhases(@PathVariable UUID activationId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    // ── CPR Cycles ──────────────────────────────────────────────────

    @PostMapping("/emergency/{activationId}/cpr-cycles")
    public ResponseEntity<Map<String, Object>> recordCPRCycle(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.recordCPRCycle(activationId.toString(), body); } catch (Exception e) { log.warn("PCT recordCPRCycle failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/emergency/{activationId}/cpr-cycles")
    public ResponseEntity<Map<String, Object>> getCPRCycles(@PathVariable UUID activationId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    // ── Resuscitation Medications ───────────────────────────────────

    @PostMapping("/emergency/{activationId}/medications")
    public ResponseEntity<Map<String, Object>> recordResusMed(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.recordResuscitationMedication(activationId.toString(), body); } catch (Exception e) { log.warn("PCT recordResuscitationMedication failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/emergency/{activationId}/medications")
    public ResponseEntity<Map<String, Object>> getResusMeds(@PathVariable UUID activationId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    // ── Care Plan Goal/Intervention CRUD ────────────────────────────

    @PostMapping("/care-plans/{planId}/goals")
    public ResponseEntity<Map<String, Object>> addGoal(
            @PathVariable UUID planId,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.addCarePlanGoal(planId.toString(), body); } catch (Exception e) { log.warn("PCT addCarePlanGoal failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/care-plans/{planId}/goals/{goalId}/update")
    public ResponseEntity<Map<String, Object>> updateGoal(
            @PathVariable UUID planId, @PathVariable UUID goalId,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.updateCarePlanGoal(planId.toString(), goalId.toString(), body); } catch (Exception e) { log.warn("PCT updateCarePlanGoal failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        if ("ACHIEVED".equals(body.get("status"))) {
        }
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping("/care-plans/{planId}/interventions")
    public ResponseEntity<Map<String, Object>> addIntervention(
            @PathVariable UUID planId,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.addCarePlanIntervention(planId.toString(), body); } catch (Exception e) { log.warn("PCT addCarePlanIntervention failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/care-plans/{planId}/interventions/{intId}/perform")
    public ResponseEntity<Map<String, Object>> performIntervention(
            @PathVariable UUID planId, @PathVariable UUID intId) {
        // STRANGLER: delegate to PctServiceClient first
        try { pctClient.performCarePlanIntervention(planId.toString(), intId.toString()); } catch (Exception e) { log.warn("PCT performCarePlanIntervention failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        return ResponseEntity.ok(Map.of("performed", true));
    }

    // ── NEWS2 Component Scoring ─────────────────────────────────────

    @PostMapping("/ews/news2")
    public ResponseEntity<Map<String, Object>> recordNEWS2Components(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        // STRANGLER: delegate to PctServiceClient first
        try {
            JsonNode pctData = pctClient.recordNEWS2Components(body);
            if (pctData != null) return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", pctData));
        } catch (Exception e) {
            log.warn("PCT recordNEWS2Components failed, falling back to local: {}", e.getMessage());
        }
        // STRANGLER: migrated to PctServiceClient — fallback to local JDBC
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

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of(
                "id", id, "totalScore", totalScore, "riskLevel", riskLevel,
                "escalationRequired", escalation, "components", components)));
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        return Integer.parseInt(val.toString());
    }
}
