package zw.gov.mohcc.impilo.inpatient.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inpatient.core.InpatientClinicalService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1")
public class InpatientClinicalController {

    private final InpatientClinicalService clinicalService;
    private final zw.gov.mohcc.impilo.inpatient.core.InpatientSafetyService safetyService;

    public InpatientClinicalController(InpatientClinicalService clinicalService,
                                       zw.gov.mohcc.impilo.inpatient.core.InpatientSafetyService safetyService) {
        this.clinicalService = clinicalService;
        this.safetyService = safetyService;
    }

    @GetMapping("/care-plans")
    public List<Map<String, Object>> listCarePlans(@RequestParam String patientId) {
        return clinicalService.listCarePlans(patientId);
    }

    @PostMapping("/care-plans")
    public ResponseEntity<Map<String, Object>> createCarePlan(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.createCarePlan(body));
    }

    @PostMapping("/care-plans/{planId}/goals")
    public ResponseEntity<Map<String, Object>> addGoal(@PathVariable UUID planId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.addGoal(planId, body));
    }

    @PostMapping("/care-plans/{planId}/goals/{goalId}/update")
    public Map<String, Object> updateGoal(@PathVariable UUID planId, @PathVariable UUID goalId,
                                          @RequestBody Map<String, Object> body) {
        clinicalService.updateGoal(planId, goalId, body);
        return Map.of("updated", true);
    }

    @PostMapping("/care-plans/{planId}/interventions")
    public ResponseEntity<Map<String, Object>> addIntervention(@PathVariable UUID planId,
                                                               @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.addIntervention(planId, body));
    }

    @PostMapping("/care-plans/{planId}/interventions/{intId}/perform")
    public Map<String, Object> performIntervention(@PathVariable UUID planId, @PathVariable UUID intId) {
        clinicalService.performIntervention(planId, intId);
        return Map.of("performed", true);
    }

    @GetMapping("/fluid-balance")
    public Map<String, Object> getFluidBalance(@RequestParam String patientId,
                                               @RequestParam(required = false) String date) {
        return clinicalService.getFluidBalance(patientId, date);
    }

    @PostMapping("/fluid-balance")
    public ResponseEntity<Map<String, Object>> recordFluid(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.recordFluid(body));
    }

    @PostMapping("/observations")
    public ResponseEntity<Map<String, Object>> recordObservation(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.recordObservation(body));
    }

    @GetMapping("/observations")
    public List<Map<String, Object>> getObservations(@RequestParam String patientId) {
        return clinicalService.getObservations(patientId);
    }

    @PostMapping("/ward-charts/{chartType}/entries")
    public ResponseEntity<Map<String, Object>> recordChartEntry(@PathVariable String chartType,
                                                                @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.recordChartEntry(body, chartType));
    }

    @GetMapping("/ward-charts/{chartType}/entries")
    public List<Map<String, Object>> getChartEntries(@PathVariable String chartType,
                                                       @RequestParam String patientId) {
        return clinicalService.getChartEntries(patientId, chartType);
    }

    @GetMapping("/ward-charts/activity")
    public Map<String, Object> getChartActivity(@RequestParam String patientId) {
        return clinicalService.getChartActivity(patientId);
    }

    @GetMapping("/mar")
    public List<Map<String, Object>> listMar(@RequestParam String patientId) {
        return clinicalService.listMar(patientId);
    }

    @PostMapping("/mar/sync")
    public Map<String, Object> syncMar(@RequestBody Map<String, Object> body) {
        Object patientRef = body.get("patientId") != null ? body.get("patientId") : body.get("patient_id");
        if (patientRef == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "patientId is required");
        }
        String patientId = patientRef.toString();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> prescriptions = body.get("prescriptions") instanceof List<?> raw
                ? (List<Map<String, Object>>) raw : List.of();
        int synced = clinicalService.syncMarSchedule(patientId, prescriptions);
        return Map.of("synced", synced, "patient_id", patientId);
    }

    @PostMapping("/mar/administer")
    public ResponseEntity<Map<String, Object>> administer(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.administerMedication(body));
    }

    @PostMapping("/ews")
    public ResponseEntity<Map<String, Object>> recordEws(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.recordEws(body));
    }

    @GetMapping("/ews")
    public List<Map<String, Object>> getEws(@RequestParam String patientId) {
        return clinicalService.getEws(patientId);
    }

    @GetMapping("/emergency/activations")
    public List<Map<String, Object>> listEmergency() {
        return clinicalService.listEmergencyActivations();
    }

    @PostMapping("/emergency/activate")
    public ResponseEntity<Map<String, Object>> activateEmergency(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.activateEmergency(body));
    }

    @PostMapping("/emergency/{id}/end")
    public Map<String, Object> endEmergency(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return clinicalService.endEmergency(id, body);
    }

    @PostMapping("/emergency/{id}/action")
    public ResponseEntity<Map<String, Object>> logEmergencyAction(@PathVariable UUID id,
                                                                  @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.logEmergencyAction(id, body));
    }

    @GetMapping("/emergency/{id}/actions")
    public List<Map<String, Object>> listEmergencyActions(@PathVariable UUID id,
                                                          @RequestParam(required = false) String actionType) {
        return clinicalService.listEmergencyActions(id, actionType);
    }

    @PostMapping("/emergency/{activationId}/resuscitation")
    public ResponseEntity<Map<String, Object>> recordResuscitation(@PathVariable UUID activationId,
                                                                   @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.recordResuscitation(activationId, body));
    }

    @GetMapping("/emergency/{activationId}/resuscitation")
    public Map<String, Object> getResuscitation(@PathVariable UUID activationId) {
        return clinicalService.getResuscitation(activationId);
    }

    @PostMapping("/apgar")
    public ResponseEntity<Map<String, Object>> recordApgar(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.recordApgar(body));
    }

    @GetMapping("/apgar")
    public List<Map<String, Object>> listApgar(@RequestParam String patientId) {
        return clinicalService.listApgar(patientId);
    }

    @PostMapping("/handover")
    public ResponseEntity<Map<String, Object>> submitHandover(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.submitHandover(body));
    }

    @PostMapping("/handover/{id}/takeover")
    public Map<String, Object> acceptTakeover(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return clinicalService.acceptTakeover(id, body);
    }

    @GetMapping("/handover")
    public List<Map<String, Object>> listHandovers(@RequestParam UUID facilityId,
                                                   @RequestParam(required = false) String status) {
        return clinicalService.listHandovers(facilityId, status);
    }

    @PostMapping("/ward-alerts")
    public ResponseEntity<Map<String, Object>> createWardAlert(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.createWardAlert(body));
    }

    @GetMapping("/ward-alerts")
    public List<Map<String, Object>> listWardAlerts(@RequestParam UUID wardId,
                                                    @RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        return clinicalService.listWardAlerts(wardId, status);
    }

    @PostMapping("/ward-alerts/{id}/acknowledge")
    public Map<String, Object> acknowledgeAlert(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return clinicalService.acknowledgeWardAlert(id, body);
    }

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Object>> requestTransfer(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.requestTransfer(body));
    }

    @PostMapping("/transfers/{id}/accept")
    public Map<String, Object> acceptTransfer(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return clinicalService.acceptTransfer(id, body);
    }

    @GetMapping("/transfers")
    public List<Map<String, Object>> listTransfers(@RequestParam(required = false) String patientId) {
        return clinicalService.listTransfers(patientId);
    }

    @PostMapping("/discharge-clearances/init")
    public ResponseEntity<List<Map<String, Object>>> initDischargeClearances(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.initDischargeClearances(body));
    }

    @GetMapping("/discharge-clearances")
    public Map<String, Object> getDischargeClearances(@RequestParam UUID encounterId) {
        return clinicalService.getDischargeClearances(encounterId);
    }

    @PostMapping("/discharge-clearances/{id}/clear")
    public Map<String, Object> clearDischargeClearance(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return clinicalService.clearDischargeClearance(id, body);
    }

    @PostMapping("/discharge-clearances/{id}/waive")
    public Map<String, Object> waiveDischargeClearance(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return clinicalService.waiveDischargeClearance(id, body);
    }

    @PostMapping("/emergency/{activationId}/phases")
    public ResponseEntity<Map<String, Object>> startResuscitationPhase(@PathVariable UUID activationId,
                                                                      @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicalService.startResuscitationPhase(activationId, body));
    }

    @PostMapping("/emergency/{activationId}/phases/{phaseId}/end")
    public Map<String, Object> endResuscitationPhase(@PathVariable UUID activationId,
                                                     @PathVariable UUID phaseId,
                                                     @RequestBody Map<String, Object> body) {
        return clinicalService.endResuscitationPhase(activationId, phaseId, body);
    }

    @GetMapping("/emergency/{activationId}/phases")
    public List<Map<String, Object>> listResuscitationPhases(@PathVariable UUID activationId) {
        return clinicalService.listResuscitationPhases(activationId);
    }

    // ── Deterioration escalations (EWS-triggered) ───────────────────

    @GetMapping("/escalations")
    public List<Map<String, Object>> listEscalations(@RequestParam(required = false) String patientId,
                                                     @RequestParam(required = false) UUID wardId) {
        if (wardId != null) {
            return safetyService.listOpenForWard(wardId);
        }
        if (patientId != null) {
            return safetyService.listForPatient(patientId);
        }
        return List.of();
    }

    @PostMapping("/escalations/{escalationId}/acknowledge")
    public Map<String, Object> acknowledgeEscalation(@PathVariable UUID escalationId) {
        var esc = safetyService.acknowledge(escalationId);
        return Map.of("id", esc.getEscalationId().toString(), "status", esc.getStatus());
    }

    @PostMapping("/escalations/{escalationId}/respond")
    public Map<String, Object> respondEscalation(@PathVariable UUID escalationId,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        String note = body == null ? null : String.valueOf(body.getOrDefault("note", body.get("response_note")));
        var esc = safetyService.respond(escalationId, note);
        return Map.of("id", esc.getEscalationId().toString(), "status", esc.getStatus());
    }
}
