package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Theatre & perioperative depth BFF surface (WS#6 theatre seam). Composes inpatient-service's
 * /internal/v1/theatre/** module (the SoR for the procedure episode) and OROS via that service.
 * Stateless: this BFF persists nothing — it forwards trust headers (via the RestTemplate interceptor)
 * and wraps the sovereign-service response in the standard {data, meta} envelope.
 */
@RestController
@RequestMapping("/internal/v1/theatre")
public class TheatreController {

    private static final Logger log = LoggerFactory.getLogger(TheatreController.class);

    private final InpatientServiceClient inpatientClient;

    public TheatreController(InpatientServiceClient inpatientClient) {
        this.inpatientClient = inpatientClient;
    }

    private Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("request_id", requestId);
        m.put("correlation_id", correlationId);
        return m;
    }

    private ResponseEntity<Map<String, Object>> ok(Object data, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data != null ? data : List.of());
        body.put("meta", meta(requestId, correlationId));
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> created(Object data, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", meta(requestId, correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> queue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.theatreQueue(), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre queue failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    @PostMapping("/cases")
    public ResponseEntity<Map<String, Object>> intake(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(inpatientClient.intakeTheatreCase(body), requestId, correlationId);
    }

    @PostMapping("/cases/{id}/triage")
    public ResponseEntity<Map<String, Object>> triage(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(inpatientClient.setTheatreTriage(id, body), requestId, correlationId);
    }

    @PostMapping("/cases/{id}/readiness")
    public ResponseEntity<Map<String, Object>> evaluateReadiness(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(inpatientClient.evaluateTheatreReadiness(id, body != null ? body : Map.of()),
                requestId, correlationId);
    }

    @GetMapping("/cases/{id}/readiness")
    public ResponseEntity<Map<String, Object>> listReadiness(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.listTheatreReadiness(id), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre readiness list failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    @PostMapping("/cases/{id}/book")
    public ResponseEntity<Map<String, Object>> book(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(inpatientClient.bookTheatreCase(id, body != null ? body : Map.of()),
                requestId, correlationId);
    }

    @PostMapping("/cases/{id}/start")
    public ResponseEntity<Map<String, Object>> start(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(inpatientClient.startTheatreCase(id, body != null ? body : Map.of()),
                requestId, correlationId);
    }

    @PostMapping("/cases/{id}/note")
    public ResponseEntity<Map<String, Object>> draftNote(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(inpatientClient.draftTheatreNote(id, body), requestId, correlationId);
    }

    @PostMapping("/cases/{id}/note/sign")
    public ResponseEntity<Map<String, Object>> signNote(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(inpatientClient.signTheatreNote(id, body), requestId, correlationId);
    }

    @GetMapping("/cases/{id}/note")
    public ResponseEntity<Map<String, Object>> getNote(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.getTheatreNote(id), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre note get failed: {}", e.getMessage());
            return ok(Map.of("status", "NONE"), requestId, correlationId);
        }
    }

    @PostMapping("/cases/{id}/pacu/disposition")
    public ResponseEntity<Map<String, Object>> pacuDisposition(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(inpatientClient.recordTheatrePacuDisposition(id, body), requestId, correlationId);
    }

    @PostMapping("/cases/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(inpatientClient.cancelTheatreCase(id, body), requestId, correlationId);
    }

    @PostMapping("/cases/{id}/safety-events")
    public ResponseEntity<Map<String, Object>> reportSafety(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(inpatientClient.reportTheatreSafetyEvent(id, body), requestId, correlationId);
    }

    @GetMapping("/cases/{id}/safety-events")
    public ResponseEntity<Map<String, Object>> listSafety(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.listTheatreSafetyEvents(id), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre safety list failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    @PostMapping("/cases/{id}/death")
    public ResponseEntity<Map<String, Object>> death(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(inpatientClient.routeTheatreDeath(id, body != null ? body : Map.of()),
                requestId, correlationId);
    }

    // ── Wave 2: theatre-day readiness board ───────────────────────────────────────
    /**
     * Composes the theatre-day readiness board: the inpatient theatre queue, and for
     * each case the inpatient board-readiness (which itself folds in TUSO space,
     * VASHANDI team and the MVUMO consent bundle via evaluateReadiness + the Wave-2
     * domains). Filterable by facility/date/room.
     */
    @GetMapping("/readiness-board")
    public ResponseEntity<Map<String, Object>> readinessBoard(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String room) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            JsonNode queue = inpatientClient.theatreQueue();
            JsonNode cases = queue != null && queue.has("data") ? queue.get("data") : queue;
            if (cases != null && cases.isArray()) {
                for (JsonNode c : cases) {
                    String caseId = c.has("episode_id") ? c.get("episode_id").asText()
                            : (c.has("id") ? c.get("id").asText() : null);
                    if (caseId == null) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("case", c);
                    try {
                        row.put("board", inpatientClient.theatreBoardReadiness(caseId, Map.of()));
                    } catch (Exception e) {
                        log.warn("board-readiness failed for case {}: {}", caseId, e.getMessage());
                        row.put("board", Map.of("board_state", "Unknown"));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("Readiness board composition failed: {}", e.getMessage());
        }
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("facility_id", facilityId);
        board.put("date", date);
        board.put("room", room);
        board.put("rows", rows);
        return ok(board, requestId, correlationId);
    }

    @PostMapping("/cases/{id}/resolve-blocker")
    public ResponseEntity<Map<String, Object>> resolveBlocker(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(inpatientClient.resolveTheatreBlocker(id, body), requestId, correlationId);
    }

    // ── Perioperative depth passthrough (UI-completion) ────────────────────────────────────────────
    // Stateless forwarders composing inpatient-service's theatre clinical-safety, commodities/
    // traceability, emergency + obstetric activation and case-detail endpoints. {data,meta} envelope,
    // trust headers forwarded by the RestTemplate interceptor; this BFF persists/decides nothing.

    /** Read-only theatre case detail (episode + theatre-scoped triage/emergency/death state). */
    @GetMapping("/cases/{id}")
    public ResponseEntity<Map<String, Object>> caseDetail(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ok(inpatientClient.getTheatreCase(id), requestId, correlationId);
    }

    // ── blood (MADI-backed) ──
    @GetMapping("/cases/{id}/blood")
    public ResponseEntity<Map<String, Object>> listBlood(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.listTheatreBlood(id), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre blood list failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    @PostMapping("/cases/{id}/blood/{action}")
    public ResponseEntity<Map<String, Object>> bloodAction(
            @PathVariable String id, @PathVariable String action,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return created(inpatientClient.theatreBloodAction(id, action, body != null ? body : Map.of()),
                requestId, correlationId);
    }

    // ── specimens (OROS-backed) + specimen transport (NHUME-backed) ──
    @GetMapping("/cases/{id}/specimens")
    public ResponseEntity<Map<String, Object>> listSpecimens(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.listTheatreSpecimens(id), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre specimen list failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    @PostMapping("/cases/{id}/specimens/{specimenId}/acknowledge-critical")
    public ResponseEntity<Map<String, Object>> acknowledgeSpecimen(
            @PathVariable String id, @PathVariable String specimenId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(inpatientClient.acknowledgeTheatreSpecimen(id, specimenId, body != null ? body : Map.of()),
                requestId, correlationId);
    }

    @PostMapping("/cases/{id}/transport/specimen")
    public ResponseEntity<Map<String, Object>> dispatchSpecimen(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(inpatientClient.requestTheatreSpecimenTransport(id, body), requestId, correlationId);
    }

    // ── surgical counts (WHO Sign-Out gate; RITO sentinel on discrepancy) ──
    @GetMapping("/cases/{id}/counts")
    public ResponseEntity<Map<String, Object>> listCounts(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.listTheatreCounts(id), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre count list failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    @PostMapping("/cases/{id}/counts")
    public ResponseEntity<Map<String, Object>> recordCount(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(inpatientClient.recordTheatreCount(id, body), requestId, correlationId);
    }

    // ── emergency activation + emergency consent exception ──
    @PostMapping("/cases/emergency")
    public ResponseEntity<Map<String, Object>> activateEmergency(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(inpatientClient.activateEmergencyCase(body), requestId, correlationId);
    }

    @PostMapping("/cases/{id}/consent/emergency-exception")
    public ResponseEntity<Map<String, Object>> emergencyConsentException(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(inpatientClient.recordEmergencyConsentException(id, body), requestId, correlationId);
    }

    // ── obstetric emergency caesarean (context / neonatal-alert / delivery / neonatal-handover) ──
    @PostMapping("/cases/{id}/obstetric/{action}")
    public ResponseEntity<Map<String, Object>> obstetricAction(
            @PathVariable String id, @PathVariable String action,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return created(inpatientClient.theatreObstetricAction(id, action, body != null ? body : Map.of()),
                requestId, correlationId);
    }

    // ── implants (UDI/serial/lot traceability + recall trace) ──
    @GetMapping("/cases/{id}/implants")
    public ResponseEntity<Map<String, Object>> listImplants(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.listTheatreImplants(id), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre implant list failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    @PostMapping("/cases/{id}/implants")
    public ResponseEntity<Map<String, Object>> recordImplant(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(inpatientClient.recordTheatreImplant(id, body), requestId, correlationId);
    }

    @GetMapping("/implants/recall")
    public ResponseEntity<Map<String, Object>> traceRecall(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String udi,
            @RequestParam(required = false) String lot) {
        try {
            return ok(inpatientClient.traceImplantRecall(udi, lot), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Implant recall trace failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    // ── sterile instrument sets (TUSO CSSD) ──
    @GetMapping("/cases/{id}/instrument-sets")
    public ResponseEntity<Map<String, Object>> listInstrumentSets(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.listInstrumentSets(id), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre instrument-set list failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    @PostMapping("/cases/{id}/instrument-sets/issue")
    public ResponseEntity<Map<String, Object>> issueInstrumentSet(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(inpatientClient.issueInstrumentSet(id, body), requestId, correlationId);
    }

    @PostMapping("/cases/{id}/instrument-sets/return")
    public ResponseEntity<Map<String, Object>> returnInstrumentSet(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(inpatientClient.returnInstrumentSet(id, body != null ? body : Map.of()),
                requestId, correlationId);
    }

    // ── controlled-drug register (two-person witness) ──
    @GetMapping("/cases/{id}/controlled-drugs")
    public ResponseEntity<Map<String, Object>> listControlledDrugs(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(inpatientClient.listControlledDrugs(id), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Theatre controlled-drug list failed: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    @PostMapping("/cases/{id}/controlled-drugs")
    public ResponseEntity<Map<String, Object>> recordControlledDrug(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(inpatientClient.recordControlledDrug(id, body), requestId, correlationId);
    }
}
