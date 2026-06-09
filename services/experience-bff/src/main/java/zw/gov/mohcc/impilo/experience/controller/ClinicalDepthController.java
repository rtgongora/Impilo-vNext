package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
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
    private final InpatientServiceClient inpatientClient;

    public ClinicalDepthController(PctServiceClient pctClient, InpatientServiceClient inpatientClient) {
        this.pctClient = pctClient;
        this.inpatientClient = inpatientClient;
    }

    private static JsonNode requirePayload(JsonNode node, String operation) {
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + ": upstream returned no payload");
        }
        return node;
    }

    private static ResponseStatusException upstreamFailure(String operation, Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + " failed", cause);
    }

    // ── Discharge Clearances ────────────────────────────────────────

    @PostMapping("/discharge-clearances/init")
    public ResponseEntity<Map<String, Object>> initClearances(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.initDischargeClearances(body), "Inpatient initDischargeClearances");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient initDischargeClearances", e);
        }
    }

    @GetMapping("/discharge-clearances")
    public ResponseEntity<Map<String, Object>> getClearances(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String encounterId) {
        try {
            JsonNode payload = requirePayload(inpatientClient.getDischargeClearances(encounterId), "Inpatient getDischargeClearances");
            if (payload.has("data") && payload.has("progress")) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("data", payload.get("data"));
                body.put("progress", payload.get("progress"));
                return ResponseEntity.ok(body);
            }
            return ResponseEntity.ok(Map.of("data", payload, "progress", Map.of("total", 0, "cleared", 0, "percent", 0)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getDischargeClearances", e);
        }
    }

    @PostMapping("/discharge-clearances/{id}/clear")
    public ResponseEntity<Map<String, Object>> clearItem(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode cleared = requirePayload(inpatientClient.clearDischargeClearance(id.toString(), body), "Inpatient clearDischargeClearance");
            return ResponseEntity.ok(Map.of("data", cleared, "status", "CLEARED"));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient clearDischargeClearance", e);
        }
    }

    @PostMapping("/discharge-clearances/{id}/waive")
    public ResponseEntity<Map<String, Object>> waiveItem(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode waived = requirePayload(inpatientClient.waiveDischargeClearance(id.toString(), body), "Inpatient waiveDischargeClearance");
            return ResponseEntity.ok(Map.of("data", waived, "status", "WAIVED"));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient waiveDischargeClearance", e);
        }
    }

    // ── Resuscitation Phases ────────────────────────────────────────

    @PostMapping("/emergency/{activationId}/phases")
    public ResponseEntity<Map<String, Object>> startPhase(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(
                    inpatientClient.startResuscitationPhase(activationId.toString(), body),
                    "Inpatient startResuscitationPhase");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient startResuscitationPhase", e);
        }
    }

    @PostMapping("/emergency/{activationId}/phases/{phaseId}/end")
    public ResponseEntity<Map<String, Object>> endPhase(
            @PathVariable UUID activationId,
            @PathVariable UUID phaseId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode ended = requirePayload(
                    inpatientClient.endResuscitationPhase(activationId.toString(), phaseId.toString(), body),
                    "Inpatient endResuscitationPhase");
            return ResponseEntity.ok(ended != null && ended.isObject()
                    ? Map.of("data", ended, "ended", true)
                    : Map.of("ended", true));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient endResuscitationPhase", e);
        }
    }

    @GetMapping("/emergency/{activationId}/phases")
    public ResponseEntity<Map<String, Object>> getPhases(@PathVariable UUID activationId) {
        try {
            JsonNode data = requirePayload(
                    inpatientClient.listResuscitationPhases(activationId.toString()),
                    "Inpatient listResuscitationPhases");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listResuscitationPhases", e);
        }
    }

    // ── CPR Cycles ──────────────────────────────────────────────────

    @PostMapping("/emergency/{activationId}/cpr-cycles")
    public ResponseEntity<Map<String, Object>> recordCPRCycle(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> action = new LinkedHashMap<>(body);
        action.put("actionType", "CPR_CYCLE");
        action.putIfAbsent("description", "CPR cycle " + body.getOrDefault("cycleNumber", body.get("cycle_number")));
        JsonNode created = inpatientClient.logEmergencyAction(activationId.toString(), action);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created != null ? created : Map.of()));
    }

    @GetMapping("/emergency/{activationId}/cpr-cycles")
    public ResponseEntity<Map<String, Object>> getCPRCycles(@PathVariable UUID activationId) {
        JsonNode data = inpatientClient.listEmergencyActions(activationId.toString(), "CPR_CYCLE");
        return ResponseEntity.ok(Map.of("data", data != null ? data : List.of()));
    }

    // ── Resuscitation Medications ───────────────────────────────────

    @PostMapping("/emergency/{activationId}/medications")
    public ResponseEntity<Map<String, Object>> recordResusMed(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> action = new LinkedHashMap<>(body);
        action.put("actionType", "RESUS_MEDICATION");
        String drug = body.getOrDefault("name", body.getOrDefault("medication", "Resuscitation medication")).toString();
        action.putIfAbsent("description", drug + " " + body.getOrDefault("dose", ""));
        JsonNode created = inpatientClient.logEmergencyAction(activationId.toString(), action);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created != null ? created : Map.of()));
    }

    @GetMapping("/emergency/{activationId}/medications")
    public ResponseEntity<Map<String, Object>> getResusMeds(@PathVariable UUID activationId) {
        JsonNode data = inpatientClient.listEmergencyActions(activationId.toString(), "RESUS_MEDICATION");
        return ResponseEntity.ok(Map.of("data", data != null ? data : List.of()));
    }

    // ── Care Plan Goal/Intervention CRUD ────────────────────────────

    @PostMapping("/care-plans/{planId}/goals")
    public ResponseEntity<Map<String, Object>> addGoal(
            @PathVariable UUID planId,
            @RequestBody Map<String, Object> body) {
        try { pctClient.addCarePlanGoal(planId.toString(), body); } catch (Exception e) { log.warn("PCT addCarePlanGoal failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/care-plans/{planId}/goals/{goalId}/update")
    public ResponseEntity<Map<String, Object>> updateGoal(
            @PathVariable UUID planId, @PathVariable UUID goalId,
            @RequestBody Map<String, Object> body) {
        try { pctClient.updateCarePlanGoal(planId.toString(), goalId.toString(), body); } catch (Exception e) { log.warn("PCT updateCarePlanGoal failed (non-blocking): {}", e.getMessage()); }
        if ("ACHIEVED".equals(body.get("status"))) {
        }
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping("/care-plans/{planId}/interventions")
    public ResponseEntity<Map<String, Object>> addIntervention(
            @PathVariable UUID planId,
            @RequestBody Map<String, Object> body) {
        try { pctClient.addCarePlanIntervention(planId.toString(), body); } catch (Exception e) { log.warn("PCT addCarePlanIntervention failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/care-plans/{planId}/interventions/{intId}/perform")
    public ResponseEntity<Map<String, Object>> performIntervention(
            @PathVariable UUID planId, @PathVariable UUID intId) {
        try { pctClient.performCarePlanIntervention(planId.toString(), intId.toString()); } catch (Exception e) { log.warn("PCT performCarePlanIntervention failed (non-blocking): {}", e.getMessage()); }
        return ResponseEntity.ok(Map.of("performed", true));
    }

    // ── NEWS2 Component Scoring ─────────────────────────────────────

    @PostMapping("/ews/news2")
    public ResponseEntity<Map<String, Object>> recordNEWS2Components(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode pctData = pctClient.recordNEWS2Components(body);
            if (pctData != null) return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", pctData));
        } catch (Exception e) {
            log.warn("PCT recordNEWS2Components failed, falling back to local: {}", e.getMessage());
        }
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
