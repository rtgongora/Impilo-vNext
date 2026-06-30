package zw.gov.mohcc.impilo.inpatient.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inpatient.core.TheatreService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Theatre &amp; perioperative depth API. Extends the procedure-episode pipeline with OROS intake, triage,
 * owner-routed booking readiness, signable operative note, cancellation, safety routing and
 * death-in-theatre. Mounted alongside {@link ProcedureEpisodeController} under /internal/v1/procedures.
 */
@RestController
@RequestMapping("/internal/v1/theatre")
public class TheatreController {

    private final TheatreService theatreService;

    public TheatreController(TheatreService theatreService) {
        this.theatreService = theatreService;
    }

    // intake + triage
    @PostMapping("/cases")
    public ResponseEntity<Map<String, Object>> intake(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.intakeFromOrosOrder(body));
    }

    @PostMapping("/cases/{id}/triage")
    public Map<String, Object> triage(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return theatreService.setTriage(id, body);
    }

    @GetMapping("/queue")
    public List<Map<String, Object>> queue() {
        return theatreService.triageQueue();
    }

    // readiness + booking
    @PostMapping("/cases/{id}/readiness")
    public Map<String, Object> evaluateReadiness(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.evaluateReadiness(id, body != null ? body : Map.of());
    }

    @GetMapping("/cases/{id}/readiness")
    public List<Map<String, Object>> listReadiness(@PathVariable UUID id) {
        return theatreService.listReadiness(id);
    }

    @PostMapping("/cases/{id}/book")
    public Map<String, Object> book(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.confirmBooking(id, body != null ? body : Map.of());
    }

    // WHO checklist-gated start
    @PostMapping("/cases/{id}/start")
    public Map<String, Object> start(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.startWithChecklistGate(id, body != null ? body : Map.of());
    }

    // operative note
    @PostMapping("/cases/{id}/note")
    public ResponseEntity<Map<String, Object>> draftNote(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.draftNote(id, body));
    }

    @PostMapping("/cases/{id}/note/sign")
    public Map<String, Object> signNote(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return theatreService.signNote(id, body);
    }

    @GetMapping("/cases/{id}/note")
    public Map<String, Object> getNote(@PathVariable UUID id) {
        return theatreService.getNote(id);
    }

    // PACU disposition (incl. death pathway)
    @PostMapping("/cases/{id}/pacu/disposition")
    public Map<String, Object> pacuDisposition(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return theatreService.recordPacuDisposition(id, body);
    }

    // cancellation
    @PostMapping("/cases/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return theatreService.cancel(id, body);
    }

    // safety + death
    @PostMapping("/cases/{id}/safety-events")
    public ResponseEntity<Map<String, Object>> reportSafety(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.reportSafetyEvent(id, body));
    }

    @GetMapping("/cases/{id}/safety-events")
    public List<Map<String, Object>> listSafety(@PathVariable UUID id) {
        return theatreService.listSafetyEvents(id);
    }

    @PostMapping("/cases/{id}/death")
    public Map<String, Object> death(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.routeDeathInTheatre(id, body != null ? body : Map.of());
    }

    @ExceptionHandler(TheatreService.BookingBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleBookingBlocked(TheatreService.BookingBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.detail());
    }
}
