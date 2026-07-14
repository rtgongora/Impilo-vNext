package zw.gov.mohcc.impilo.inpatient.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inpatient.core.TheatreCommoditiesService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wave 3 theatre commodities / traceability API: surgical implants (UDI/serial + recall trace), sterile
 * instrument-set issue/return (TUSO CSSD), and the controlled-drug two-person-witness register. Mounted
 * alongside {@link TheatreController} under /internal/v1/theatre.
 */
@RestController
@RequestMapping("/internal/v1/theatre")
public class TheatreCommoditiesController {

    private final TheatreCommoditiesService service;

    public TheatreCommoditiesController(TheatreCommoditiesService service) {
        this.service = service;
    }

    // ── implants ──
    @PostMapping("/cases/{id}/implants")
    public ResponseEntity<Map<String, Object>> recordImplant(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordImplant(id, body));
    }

    @GetMapping("/cases/{id}/implants")
    public List<Map<String, Object>> listImplants(@PathVariable UUID id) {
        return service.listImplants(id);
    }

    /** Recall trace: given a recalled UDI or lot, list every patient/episode it was implanted in. */
    @GetMapping("/implants/recall")
    public List<Map<String, Object>> traceRecall(@RequestParam(required = false) String udi,
                                                 @RequestParam(required = false) String lot) {
        return service.traceRecall(udi, lot);
    }

    // ── instrument sets (TUSO CSSD) ──
    @PostMapping("/cases/{id}/instrument-sets/issue")
    public ResponseEntity<Map<String, Object>> issueSet(@PathVariable UUID id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issueInstrumentSet(id, body != null ? body : Map.of()));
    }

    @PostMapping("/cases/{id}/instrument-sets/return")
    public Map<String, Object> returnSet(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return service.returnInstrumentSet(id, body != null ? body : Map.of());
    }

    @GetMapping("/cases/{id}/instrument-sets")
    public List<Map<String, Object>> listSets(@PathVariable UUID id) {
        return service.listInstrumentSets(id);
    }

    // ── controlled drugs (two-person witness) ──
    @PostMapping("/cases/{id}/controlled-drugs")
    public ResponseEntity<Map<String, Object>> recordControlledDrug(@PathVariable UUID id,
                                                                    @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordControlledDrug(id, body));
    }

    @GetMapping("/cases/{id}/controlled-drugs")
    public List<Map<String, Object>> listControlledDrugs(@PathVariable UUID id) {
        return service.listControlledDrugs(id);
    }
}
