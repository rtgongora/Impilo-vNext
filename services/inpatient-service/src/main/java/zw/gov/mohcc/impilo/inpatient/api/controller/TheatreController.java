package zw.gov.mohcc.impilo.inpatient.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.inpatient.core.SpecimenCustodyService;
import zw.gov.mohcc.impilo.inpatient.core.SurgicalDischargeService;
import zw.gov.mohcc.impilo.inpatient.core.TheatreService;

import java.util.LinkedHashMap;
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
    private final SurgicalDischargeService surgicalDischargeService;
    private final SpecimenCustodyService specimenCustodyService;

    public TheatreController(TheatreService theatreService,
                             SurgicalDischargeService surgicalDischargeService,
                             SpecimenCustodyService specimenCustodyService) {
        this.theatreService = theatreService;
        this.surgicalDischargeService = surgicalDischargeService;
        this.specimenCustodyService = specimenCustodyService;
    }

    // ── Wave 4 §14 — surgery-specialised discharge summary ──
    @PostMapping("/cases/{id}/discharge")
    public Map<String, Object> saveDischargeDraft(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return surgicalDischargeService.saveDraft(id, body);
    }

    @GetMapping("/cases/{id}/discharge")
    public Map<String, Object> getDischarge(@PathVariable UUID id) {
        return surgicalDischargeService.get(id);
    }

    @PostMapping("/cases/{id}/discharge/complete")
    public Map<String, Object> completeDischarge(@PathVariable UUID id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        return surgicalDischargeService.complete(id, body != null ? body : Map.of());
    }

    // intake + triage
    @PostMapping("/cases")
    public ResponseEntity<Map<String, Object>> intake(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.TRAUMA_EPISODE_ID, required = false) String traumaEpisodeId) {
        Map<String, Object> payload = withTraumaHeader(body, traumaEpisodeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.intakeFromOrosOrder(payload));
    }

    @PostMapping("/cases/{id}/triage")
    public Map<String, Object> triage(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return theatreService.setTriage(id, body);
    }

    /** Read-only theatre case detail (episode detail + theatre-scoped triage/emergency/death state). */
    @GetMapping("/cases/{id}")
    public Map<String, Object> caseDetail(@PathVariable UUID id) {
        return theatreService.caseDetail(id);
    }

    /**
     * Wave B3 — confirm marked anatomical site and laterality (authz resource {@code site-side}).
     * Body: {@code laterality} (required, closed vocab) and optional {@code anatomicalSite}.
     * Actor provenance is taken from the trust context ({@code X-Actor-ID}), not the body.
     */
    @PostMapping("/cases/{id}/site-side")
    public Map<String, Object> confirmSiteAndSide(@PathVariable UUID id,
                                                  @RequestBody Map<String, Object> body) {
        return theatreService.confirmSiteAndSide(id, body != null ? body : Map.of());
    }

    // ── Wave 5b §15 — EMERGENCY SURGERY rapid activation + emergency consent exception ──
    @PostMapping("/cases/emergency")
    public ResponseEntity<Map<String, Object>> activateEmergency(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.TRAUMA_EPISODE_ID, required = false) String traumaEpisodeId) {
        Map<String, Object> payload = withTraumaHeader(body, traumaEpisodeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.activateEmergencySurgery(payload));
    }

    @PostMapping("/cases/{id}/consent/emergency-exception")
    public Map<String, Object> emergencyConsentException(@PathVariable UUID id,
                                                         @RequestBody Map<String, Object> body) {
        return theatreService.recordEmergencyConsentException(id, body);
    }

    // ── Wave 5b §15 — OBSTETRIC EMERGENCY C-SECTION (maternal + fetal + neonatal) ──
    @PostMapping("/cases/{id}/obstetric/context")
    public Map<String, Object> obstetricContext(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return theatreService.recordObstetricContext(id, body);
    }

    @PostMapping("/cases/{id}/obstetric/neonatal-alert")
    public ResponseEntity<Map<String, Object>> neonatalAlert(@PathVariable UUID id,
                                                             @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(theatreService.notifyNeonatalTeam(id, body != null ? body : Map.of()));
    }

    @PostMapping("/cases/{id}/obstetric/delivery")
    public ResponseEntity<Map<String, Object>> delivery(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.recordDelivery(id, body));
    }

    @PostMapping("/cases/{id}/obstetric/neonatal-handover")
    public Map<String, Object> neonatalHandover(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return theatreService.recordNeonatalHandover(id, body);
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

    // ── Wave 4 §12 — PACU recovery depth ──
    @PostMapping("/cases/{id}/pacu/observations")
    public ResponseEntity<Map<String, Object>> recordPacuObservation(@PathVariable UUID id,
                                                                     @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.recordPacuObservation(id, body));
    }

    @GetMapping("/cases/{id}/pacu/observations")
    public List<Map<String, Object>> listPacuObservations(@PathVariable UUID id) {
        return theatreService.listPacuObservations(id);
    }

    @GetMapping("/cases/{id}/pacu/readiness")
    public Map<String, Object> dischargeReadiness(@PathVariable UUID id) {
        return theatreService.dischargeReadiness(id);
    }

    @PostMapping("/cases/{id}/pacu/escalate")
    public ResponseEntity<Map<String, Object>> escalatePacu(@PathVariable UUID id,
                                                            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(theatreService.escalatePacu(id, body != null ? body : Map.of()));
    }

    @PostMapping("/cases/{id}/pacu/discharge")
    public Map<String, Object> pacuDischargeDecision(@PathVariable UUID id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.pacuDischargeDecision(id, body != null ? body : Map.of());
    }

    @PostMapping("/cases/{id}/return-to-theatre")
    public Map<String, Object> returnToTheatre(@PathVariable UUID id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.returnToTheatre(id, body != null ? body : Map.of());
    }

    // ── Wave 4 T&L — tele-PACU/ICU teleconsult link + surgical-case completion (trainee CPD + bill) ──
    @PostMapping("/cases/{id}/teleconsult")
    public ResponseEntity<Map<String, Object>> linkTeleconsult(@PathVariable UUID id,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(theatreService.linkTeleconsult(id, body != null ? body : Map.of()));
    }

    @GetMapping("/cases/{id}/teleconsult")
    public List<Map<String, Object>> listTeleconsult(@PathVariable UUID id) {
        return theatreService.listTeleconsultLinks(id);
    }

    @PostMapping("/cases/{id}/complete")
    public Map<String, Object> completeCase(@PathVariable UUID id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.completeCase(id, body != null ? body : Map.of());
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

    // ── Wave 1 clinical-safety: blood pipeline (MADI-backed) ──
    @PostMapping("/cases/{id}/blood/request")
    public ResponseEntity<Map<String, Object>> requestBlood(@PathVariable UUID id,
                                                            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.requestBlood(id, body != null ? body : Map.of()));
    }

    @PostMapping("/cases/{id}/blood/issue")
    public Map<String, Object> issueBlood(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.issueAndTransport(id, body != null ? body : Map.of());
    }

    @PostMapping("/cases/{id}/blood/administer")
    public Map<String, Object> administerBlood(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.administerBlood(id, body != null ? body : Map.of());
    }

    @PostMapping("/cases/{id}/blood/resolve")
    public Map<String, Object> resolveBlood(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.resolveBlood(id, body != null ? body : Map.of());
    }

    @GetMapping("/cases/{id}/blood")
    public List<Map<String, Object>> listBlood(@PathVariable UUID id) {
        return theatreService.listBloodLinks(id);
    }

    // ── Wave 1 clinical-safety: transport (NHUME-backed) ──
    @PostMapping("/cases/{id}/transport/movement")
    public ResponseEntity<Map<String, Object>> requestMovement(@PathVariable UUID id,
                                                               @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.requestPatientMovement(id, body));
    }

    @PostMapping("/cases/{id}/transport/specimen")
    public ResponseEntity<Map<String, Object>> requestSpecimenTransport(@PathVariable UUID id,
                                                                        @RequestBody Map<String, Object> body) {
        String specimenRef = body != null ? String.valueOf(body.getOrDefault("specimenRef",
                body.getOrDefault("specimen_ref", body.get("orosSpecimenId")))) : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(theatreService.requestSpecimenTransport(id, specimenRef, body != null ? body : Map.of()));
    }

    @PostMapping("/cases/{id}/pacu")
    public Map<String, Object> enterPacu(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.enterPacu(id, body != null ? body : Map.of());
    }

    @GetMapping("/cases/{id}/transport")
    public List<Map<String, Object>> listTransport(@PathVariable UUID id) {
        return theatreService.listTransport(id);
    }

    // ── Wave 1 clinical-safety: specimens (OROS-backed) ──
    @GetMapping("/cases/{id}/specimens")
    public List<Map<String, Object>> listSpecimens(@PathVariable UUID id) {
        return theatreService.listSpecimens(id);
    }

    @PostMapping("/cases/{id}/specimens/{specimenId}/acknowledge-critical")
    public Map<String, Object> acknowledgeCritical(@PathVariable UUID id, @PathVariable UUID specimenId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        return theatreService.acknowledgeCritical(id, specimenId, body != null ? body : Map.of());
    }

    // ── Wave P8 (pipeline §13): chain of custody, label confirmation, adequacy ──
    @PostMapping("/cases/{id}/specimens/{specimenId}/collect")
    public Map<String, Object> recordSpecimenCollection(@PathVariable UUID id, @PathVariable UUID specimenId,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body != null ? body : Map.of();
        return specimenCustodyService.custodyRow(specimenCustodyService.recordCollection(id, specimenId,
                (String) b.get("containerType"), (String) b.get("fixative")));
    }

    @PostMapping("/cases/{id}/specimens/{specimenId}/confirm-label")
    public Map<String, Object> confirmSpecimenLabel(@PathVariable UUID id, @PathVariable UUID specimenId) {
        return specimenCustodyService.custodyRow(specimenCustodyService.confirmLabel(id, specimenId));
    }

    @PostMapping("/cases/{id}/specimens/{specimenId}/receive")
    public Map<String, Object> recordSpecimenReceipt(@PathVariable UUID id, @PathVariable UUID specimenId) {
        return specimenCustodyService.custodyRow(specimenCustodyService.recordReceipt(id, specimenId));
    }

    @PostMapping("/cases/{id}/specimens/{specimenId}/adequacy")
    public Map<String, Object> assessSpecimenAdequacy(@PathVariable UUID id, @PathVariable UUID specimenId,
                                                       @RequestBody Map<String, Object> body) {
        return specimenCustodyService.custodyRow(
                specimenCustodyService.assessAdequacy(id, specimenId, (String) body.get("adequacy")));
    }

    // ── Wave 1 clinical-safety: surgical counts (RITO-backed on discrepancy) ──
    @PostMapping("/cases/{id}/counts")
    public ResponseEntity<Map<String, Object>> recordCount(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.recordCount(id, body));
    }

    @GetMapping("/cases/{id}/counts")
    public List<Map<String, Object>> listCounts(@PathVariable UUID id) {
        return theatreService.listCounts(id);
    }

    @ExceptionHandler(TheatreService.BookingBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleBookingBlocked(TheatreService.BookingBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.detail());
    }

    /**
     * Fold the inbound {@code X-Trauma-Episode-ID} header (the daidzai/PCT-minted trauma episode id,
     * carried as the UUID canonical string) into the intake body so the service stamps it onto
     * {@code procedure_episode.trauma_episode_id}. CONSUME, never mint — a surgery indicated for a
     * trauma patient already carries the episode id on its context. Absent header → elective, unaffected.
     */
    private static Map<String, Object> withTraumaHeader(Map<String, Object> body, String traumaEpisodeId) {
        Map<String, Object> payload = new LinkedHashMap<>(body != null ? body : Map.of());
        if (traumaEpisodeId != null && !traumaEpisodeId.isBlank()) {
            payload.putIfAbsent("traumaEpisodeId", traumaEpisodeId);
        }
        return payload;
    }
}
