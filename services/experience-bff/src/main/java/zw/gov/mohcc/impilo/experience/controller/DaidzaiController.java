package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Experience-BFF facade for Daidzai (emergency, disaster &amp; public-health response command).
 * Composition/orchestration only — it never owns emergency truth; every call proxies to
 * daidzai-service with the inbound trust context forwarded. Backs the citizen /emergency
 * surfaces and the provider /work/daidzai command surfaces.
 */
@RestController
@RequestMapping("/internal/v1/daidzai")
public class DaidzaiController {

    private final zw.gov.mohcc.impilo.experience.client.DaidzaiServiceClient daidzai;

    public DaidzaiController(zw.gov.mohcc.impilo.experience.client.DaidzaiServiceClient daidzai) {
        this.daidzai = daidzai;
    }

    // ── SOS / requests ───────────────────────────────────────────────
    @PostMapping("/requests")
    public ResponseEntity<JsonNode> createRequest(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.createRequest(body));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<JsonNode> getRequest(@PathVariable String id) {
        return ResponseEntity.ok(daidzai.getRequest(id));
    }

    @PostMapping("/requests/{id}/triage")
    public ResponseEntity<JsonNode> triage(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.triageRequest(id));
    }

    // ── Incidents ────────────────────────────────────────────────────
    @PostMapping("/incidents")
    public ResponseEntity<JsonNode> createIncident(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.createIncident(body));
    }

    @GetMapping("/incidents")
    public ResponseEntity<JsonNode> listIncidents(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(daidzai.listIncidents(type, status));
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<JsonNode> getIncident(@PathVariable String id) {
        return ResponseEntity.ok(daidzai.getIncident(id));
    }

    @PostMapping("/incidents/{id}/dispatch")
    public ResponseEntity<JsonNode> dispatch(@PathVariable String id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.dispatch(id, body != null ? body : Map.of()));
    }

    @PostMapping("/incidents/{id}/mission-events")
    public ResponseEntity<JsonNode> missionEvent(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.missionEvent(id, body));
    }

    @GetMapping("/incidents/{id}/missions")
    public ResponseEntity<JsonNode> missions(@PathVariable String id) {
        return ResponseEntity.ok(daidzai.missions(id));
    }

    @PostMapping("/incidents/{id}/handoff")
    public ResponseEntity<JsonNode> handoff(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(daidzai.handoff(id, body));
    }

    @PostMapping("/incidents/{id}/resources")
    public ResponseEntity<JsonNode> requestResource(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.requestResource(id, body));
    }

    @GetMapping("/incidents/{id}/resources")
    public ResponseEntity<JsonNode> resources(@PathVariable String id) {
        return ResponseEntity.ok(daidzai.resources(id));
    }

    // ── Disasters / MCI ──────────────────────────────────────────────
    @PostMapping("/disasters")
    public ResponseEntity<JsonNode> declareDisaster(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.declareDisaster(body));
    }

    @GetMapping("/disasters")
    public ResponseEntity<JsonNode> listDisasters() {
        return ResponseEntity.ok(daidzai.listDisasters());
    }

    @PostMapping("/disasters/{id}/sites")
    public ResponseEntity<JsonNode> addSite(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.addSite(id, body));
    }

    @GetMapping("/disasters/{id}/sites")
    public ResponseEntity<JsonNode> sites(@PathVariable String id) {
        return ResponseEntity.ok(daidzai.sites(id));
    }

    @PostMapping("/disasters/{id}/close")
    public ResponseEntity<JsonNode> close(@PathVariable String id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(daidzai.closeDisaster(id, body != null ? body : Map.of()));
    }

    // ── EMS clinical dispatch + prehospital ePCR (responder mobile app) ──────
    @PostMapping("/ems/incidents/{id}/dispatch")
    public ResponseEntity<JsonNode> emsDispatch(@PathVariable String id,
                                                @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.emsDispatch(id, body != null ? body : Map.of()));
    }

    @PostMapping("/ems/missions/{id}/advance")
    public ResponseEntity<JsonNode> emsAdvance(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(daidzai.emsAdvance(id, body));
    }

    @GetMapping("/ems/missions/{id}")
    public ResponseEntity<JsonNode> emsMission(@PathVariable String id) {
        return ResponseEntity.ok(daidzai.emsMission(id));
    }

    @GetMapping("/ems/incidents/{id}/mission")
    public ResponseEntity<JsonNode> emsMissionByIncident(@PathVariable String id) {
        return ResponseEntity.ok(daidzai.emsMissionByIncident(id));
    }

    @PostMapping("/ems/missions/{id}/epcr")
    public ResponseEntity<JsonNode> epcrUpsert(@PathVariable String id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.epcrUpsert(id, body != null ? body : Map.of()));
    }

    @PostMapping("/ems/missions/{id}/epcr/events")
    public ResponseEntity<JsonNode> epcrEvent(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(daidzai.epcrEvent(id, body));
    }

    @GetMapping("/ems/missions/{id}/epcr")
    public ResponseEntity<JsonNode> epcr(@PathVariable String id) {
        return ResponseEntity.ok(daidzai.epcr(id));
    }
}
