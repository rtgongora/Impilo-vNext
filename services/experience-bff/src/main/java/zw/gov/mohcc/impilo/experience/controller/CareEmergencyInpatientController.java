package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

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

    private final InpatientServiceClient inpatientClient;
    private final PctServiceClient pctClient;

    public CareEmergencyInpatientController(InpatientServiceClient inpatientClient,
                                            PctServiceClient pctClient) {
        this.inpatientClient = inpatientClient;
        this.pctClient = pctClient;
    }

    // ── Care Plans ──────────────────────────────────────────────────

    @GetMapping("/care-plans")
    public ResponseEntity<Map<String, Object>> listCarePlans(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        try { JsonNode pctData = pctClient.listCarePlans(patientId); if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData)); } catch (Exception e) { log.warn("PCT listCarePlans failed, falling back to local: {}", e.getMessage()); }
        for (Map<String, Object> plan : plans) {
            UUID planId = (UUID) plan.get("id");
        }
        return ResponseEntity.ok(Map.of("data", plans));
    }

    @PostMapping("/care-plans")
    public ResponseEntity<Map<String, Object>> createCarePlan(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        try { pctClient.createCarePlan(body); } catch (Exception e) { log.warn("PCT createCarePlan failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();

        @SuppressWarnings("unchecked") List<Map<String, Object>> goals = (List<Map<String, Object>>) body.getOrDefault("goals", List.of());
        for (Map<String, Object> g : goals) {
            UUID gId = UUID.randomUUID();
        }

        @SuppressWarnings("unchecked") List<Map<String, Object>> interventions = (List<Map<String, Object>>) body.getOrDefault("interventions", List.of());
        for (Map<String, Object> i : interventions) {
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    // ── Fluid Balance ───────────────────────────────────────────────

    @GetMapping("/fluid-balance")
    public ResponseEntity<Map<String, Object>> getFluidBalance(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId, @RequestParam(required = false) String date) {
        try { JsonNode pctData = pctClient.getFluidBalance(patientId, date); if (pctData != null) return ResponseEntity.ok(Map.of("data", pctData)); } catch (Exception e) { log.warn("PCT getFluidBalance failed, falling back to local: {}", e.getMessage()); }
        String d = date != null ? date : java.time.LocalDate.now().toString();
        int totalIntake = records.stream().filter(r -> "INTAKE".equals(r.get("entry_type"))).mapToInt(r -> (Integer) r.get("volume_ml")).sum();
        int totalOutput = records.stream().filter(r -> "OUTPUT".equals(r.get("entry_type"))).mapToInt(r -> (Integer) r.get("volume_ml")).sum();
        return ResponseEntity.ok(Map.of("data", records, "summary", Map.of("totalIntake", totalIntake, "totalOutput", totalOutput, "balance", totalIntake - totalOutput)));
    }

    @PostMapping("/fluid-balance")
    public ResponseEntity<Map<String, Object>> recordFluid(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        try { pctClient.recordFluidBalance(body); } catch (Exception e) { log.warn("PCT recordFluidBalance failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    // ── Emergency Activations ───────────────────────────────────────

    @GetMapping("/emergency/activations")
    public ResponseEntity<Map<String, Object>> listActivations(@RequestHeader("X-Tenant-ID") String tenantId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/emergency/activate")
    public ResponseEntity<Map<String, Object>> activateEmergency(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        try { pctClient.activateEmergency(body); } catch (Exception e) { log.warn("PCT activateEmergency failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "status", "ACTIVE")));
    }

    @PostMapping("/emergency/{id}/action")
    public ResponseEntity<Map<String, Object>> logAction(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try { pctClient.logEmergencyAction(id.toString(), body); } catch (Exception e) { log.warn("PCT logEmergencyAction failed (non-blocking): {}", e.getMessage()); }
        return ResponseEntity.ok(Map.of("logged", true));
    }

    @PostMapping("/emergency/{id}/end")
    public ResponseEntity<Map<String, Object>> endEmergency(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try { pctClient.endEmergency(id.toString(), body); } catch (Exception e) { log.warn("PCT endEmergency failed (non-blocking): {}", e.getMessage()); }
        return ResponseEntity.ok(Map.of("status", "ENDED"));
    }

    // ── Resuscitation Records ───────────────────────────────────────

    @PostMapping("/emergency/{activationId}/resuscitation")
    public ResponseEntity<Map<String, Object>> recordResuscitation(@PathVariable UUID activationId, @RequestBody Map<String, Object> body) {
        try { pctClient.recordResuscitation(activationId.toString(), body); } catch (Exception e) { log.warn("PCT recordResuscitation failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    // ── APGAR Scores ────────────────────────────────────────────────

    @PostMapping("/apgar")
    public ResponseEntity<Map<String, Object>> recordApgar(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        try { pctClient.recordApgar(body); } catch (Exception e) { log.warn("PCT recordApgar failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/apgar")
    public ResponseEntity<Map<String, Object>> getApgar(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    // ── Early Warning Scores ────────────────────────────────────────

    @PostMapping("/ews")
    public ResponseEntity<Map<String, Object>> recordEWS(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        try { pctClient.recordEWS(body); } catch (Exception e) { log.warn("PCT recordEWS failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        int totalScore = Integer.parseInt(body.get("totalScore").toString());
        String riskLevel = totalScore >= 7 ? "HIGH" : totalScore >= 5 ? "MEDIUM" : totalScore >= 1 ? "LOW" : "NONE";
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "riskLevel", riskLevel, "escalationRequired", totalScore >= 7)));
    }

    @GetMapping("/ews")
    public ResponseEntity<Map<String, Object>> getEWS(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    // ── Admissions ──────────────────────────────────────────────────

    @PostMapping("/admissions")
    public ResponseEntity<Map<String, Object>> createAdmission(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        try { inpatientClient.createAdmission(body); } catch (Exception e) { log.warn("Inpatient createAdmission failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/admissions")
    public ResponseEntity<Map<String, Object>> listAdmissions(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam(required = false) String patientId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    // ── Ward Rounds ─────────────────────────────────────────────────

    @PostMapping("/ward-rounds")
    public ResponseEntity<Map<String, Object>> startWardRound(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/ward-rounds/{id}/entries")
    public ResponseEntity<Map<String, Object>> addRoundEntry(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID entryId = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", entryId)));
    }

    @GetMapping("/ward-rounds")
    public ResponseEntity<Map<String, Object>> listWardRounds(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String wardId) {
        return ResponseEntity.ok(Map.of("data", rounds));
    }

    // ── Observation Charts ──────────────────────────────────────────

    @PostMapping("/observations")
    public ResponseEntity<Map<String, Object>> recordObservation(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        try { pctClient.recordObservation(body); } catch (Exception e) { log.warn("PCT recordObservation failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/observations")
    public ResponseEntity<Map<String, Object>> getObservations(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    // ── Patient Transfers ───────────────────────────────────────────

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Object>> requestTransfer(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        try { inpatientClient.transferPatient(null, body); } catch (Exception e) { log.warn("Inpatient requestTransfer failed (non-blocking): {}", e.getMessage()); }
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "status", "REQUESTED")));
    }

    @PostMapping("/transfers/{id}/accept")
    public ResponseEntity<Map<String, Object>> acceptTransfer(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("status", "COMPLETED"));
    }

    @GetMapping("/transfers")
    public ResponseEntity<Map<String, Object>> listTransfers(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam(required = false) String patientId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }
}
