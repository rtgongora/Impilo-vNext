package zw.gov.mohcc.impilo.inpatient.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inpatient.core.ProcedureEpisodeService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/procedures")
public class ProcedureEpisodeController {

    private final ProcedureEpisodeService procedureService;

    public ProcedureEpisodeController(ProcedureEpisodeService procedureService) {
        this.procedureService = procedureService;
    }

    @PostMapping("/episodes")
    public ResponseEntity<Map<String, Object>> createEpisode(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procedureService.createEpisode(body));
    }

    @GetMapping("/episodes")
    public List<Map<String, Object>> listEpisodes(@RequestParam String patientId) {
        return procedureService.listEpisodes(patientId);
    }

    @GetMapping("/episodes/history")
    public List<Map<String, Object>> listHistory(@RequestParam(name = "patient_id") String patientId) {
        return procedureService.listEpisodesForHistory(patientId);
    }

    @GetMapping("/episodes/{id}")
    public Map<String, Object> getEpisode(@PathVariable UUID id) {
        return procedureService.getEpisode(id);
    }

    @PostMapping("/episodes/{id}/preop")
    public Map<String, Object> submitPreop(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return procedureService.submitPreop(id, body);
    }

    @PostMapping("/episodes/{id}/checklist/{phase}")
    public Map<String, Object> completeChecklist(@PathVariable UUID id, @PathVariable String phase,
                                                 @RequestBody Map<String, Object> body) {
        return procedureService.completeChecklistItems(id, phase, body);
    }

    @PostMapping("/episodes/{id}/start")
    public Map<String, Object> startProcedure(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return procedureService.startProcedure(id, body);
    }

    @PostMapping("/episodes/{id}/intraop/events")
    public ResponseEntity<Map<String, Object>> recordIntraop(@PathVariable UUID id,
                                                             @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procedureService.recordIntraopEvent(id, body));
    }

    @GetMapping("/episodes/{id}/intraop/events")
    public List<Map<String, Object>> listIntraop(@PathVariable UUID id) {
        return procedureService.listIntraopEvents(id);
    }

    @PostMapping("/episodes/{id}/pacu")
    public Map<String, Object> enterPacu(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return procedureService.enterPacu(id, body);
    }

    @PostMapping("/episodes/{id}/postop")
    public Map<String, Object> completePostop(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return procedureService.completePostop(id, body);
    }

    @PostMapping("/episodes/{id}/complete")
    public Map<String, Object> completeEpisode(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return procedureService.completeEpisode(id, body);
    }

    @PostMapping("/episodes/{id}/consent-evidence")
    public Map<String, Object> bindConsentEvidence(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return procedureService.bindConsentEvidence(id, body);
    }

    @GetMapping("/episodes/{id}/consent")
    public Map<String, Object> getConsentStatus(@PathVariable UUID id) {
        return procedureService.getConsentStatus(id);
    }

    @PostMapping("/episodes/{id}/documents")
    public ResponseEntity<Map<String, Object>> linkDocument(@PathVariable UUID id,
                                                            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procedureService.linkDocument(id, body));
    }

    @GetMapping("/episodes/{id}/documents")
    public List<Map<String, Object>> listDocuments(@PathVariable UUID id) {
        return procedureService.listDocuments(id);
    }

    @PostMapping("/episodes/{id}/consumables")
    public ResponseEntity<Map<String, Object>> recordConsumable(@PathVariable UUID id,
                                                                @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procedureService.recordConsumable(id, body));
    }

    @GetMapping("/episodes/{id}/consumables")
    public List<Map<String, Object>> listConsumables(@PathVariable UUID id) {
        return procedureService.listConsumables(id);
    }

    @GetMapping("/episodes/{id}/anaesthesia/score-suggestions")
    public List<Map<String, Object>> anaesthesiaScoreSuggestions(@PathVariable UUID id) {
        return procedureService.suggestAnaesthesiaScores(id);
    }

    @GetMapping("/episodes/{id}/anaesthesia/scores")
    public List<Map<String, Object>> listAnaesthesiaScores(@PathVariable UUID id) {
        return procedureService.listAnaesthesiaScores(id);
    }

    @PostMapping("/episodes/{id}/anaesthesia/scores")
    public ResponseEntity<Map<String, Object>> recordAnaesthesiaScore(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procedureService.recordAnaesthesiaScore(id, body));
    }

    @PostMapping("/episodes/{id}/intraop/vitals")
    public ResponseEntity<Map<String, Object>> recordIntraopVitals(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procedureService.recordIntraopVitals(id, body));
    }
}
