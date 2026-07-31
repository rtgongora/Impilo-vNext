package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Full ED / casualty workflow — sovereign state in pct-service; BFF orchestrates
 * pathway sessions, clinical paging (notification + support tickets), and CKP reference.
 */
@RestController
@RequestMapping("/internal/v1/ed")
public class EdWorkflowController {

    private static final Logger log = LoggerFactory.getLogger(EdWorkflowController.class);

    private final PctServiceClient pctClient;
    private final ClinicalKnowledgePlatformClient ckpClient;
    private final NotificationServiceClient notificationClient;
    private final SupportServiceClient supportClient;
    private final zw.gov.mohcc.impilo.experience.client.InpatientServiceClient inpatientClient;

    public EdWorkflowController(PctServiceClient pctClient,
                                ClinicalKnowledgePlatformClient ckpClient,
                                NotificationServiceClient notificationClient,
                                SupportServiceClient supportClient,
                                zw.gov.mohcc.impilo.experience.client.InpatientServiceClient inpatientClient) {
        this.pctClient = pctClient;
        this.ckpClient = ckpClient;
        this.notificationClient = notificationClient;
        this.supportClient = supportClient;
        this.inpatientClient = inpatientClient;
    }

    private static JsonNode requirePayload(JsonNode node, String operation) {
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + ": upstream returned no payload");
        }
        return node;
    }

    private static ResponseStatusException upstreamFailure(String operation, Exception cause) {
        log.warn("{} failed: {}", operation, cause.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + " failed", cause);
    }

    @PostMapping("/visits")
    public ResponseEntity<Map<String, Object>> openVisit(@RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.openEdVisit(body), "PCT openEdVisit");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT openEdVisit", e);
        }
    }

    @GetMapping("/visits")
    public ResponseEntity<Map<String, Object>> listVisits(
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(required = false) String status) {
        try {
            JsonNode data = requirePayload(pctClient.listEdVisits(facilityId, status), "PCT listEdVisits");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT listEdVisits", e);
        }
    }

    @GetMapping("/visits/{id}")
    public ResponseEntity<Map<String, Object>> getVisit(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(pctClient.getEdVisit(id.toString()), "PCT getEdVisit");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT getEdVisit", e);
        }
    }

    @GetMapping("/triage/discriminators")
    public ResponseEntity<Map<String, Object>> triageDiscriminators() {
        try {
            JsonNode data = requirePayload(pctClient.edTriageDiscriminators(), "PCT edTriageDiscriminators");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edTriageDiscriminators", e);
        }
    }

    @PostMapping("/triage/score")
    public ResponseEntity<Map<String, Object>> scoreTriage(@RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(pctClient.edTriageScore(body), "PCT edTriageScore");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edTriageScore", e);
        }
    }

    @PostMapping("/visits/{id}/triage")
    public ResponseEntity<Map<String, Object>> triage(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return proxyVisitPost(id, "/triage", body, "PCT edTriage");
    }

    @PostMapping("/visits/{id}/zone")
    public ResponseEntity<Map<String, Object>> zone(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return proxyVisitPost(id, "/zone", body, "PCT edZone");
    }

    @PostMapping("/visits/{id}/encounter")
    public ResponseEntity<Map<String, Object>> startEncounter(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return proxyVisitPost(id, "/encounter", body != null ? body : Map.of(), "PCT edEncounter");
    }

    @PostMapping("/visits/{id}/protocol")
    public ResponseEntity<Map<String, Object>> bindProtocol(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> enriched = new LinkedHashMap<>(body);
            if (!enriched.containsKey("pathwaySessionId") && enriched.get("pathwayRef") != null) {
                Map<String, Object> sessionBody = new LinkedHashMap<>();
                sessionBody.put("pathway_id", enriched.get("pathwayRef"));
                if (enriched.get("patientCpid") != null) sessionBody.put("patient_id", enriched.get("patientCpid"));
                sessionBody.put("encounter_id", enriched.get("encounterId"));
                try {
                    JsonNode session = ckpClient.startPathwaySession(sessionBody);
                    if (session != null && session.has("id")) {
                        enriched.put("pathwaySessionId", session.get("id").asText());
                    } else if (session != null && session.has("session_id")) {
                        enriched.put("pathwaySessionId", session.get("session_id").asText());
                    }
                } catch (Exception ex) {
                    log.warn("CKP pathway session start failed (non-blocking): {}", ex.getMessage());
                }
            }
            JsonNode data = requirePayload(pctClient.edVisitPost(id.toString(), "/protocol", enriched), "PCT edProtocol");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edProtocol", e);
        }
    }

    @GetMapping("/visits/{id}/protocol-suggestions")
    public ResponseEntity<Map<String, Object>> protocolSuggestions(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(pctClient.edProtocolSuggestions(id.toString()), "PCT edProtocolSuggestions");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edProtocolSuggestions", e);
        }
    }

    @PostMapping("/visits/{id}/trauma/activate")
    public ResponseEntity<Map<String, Object>> activateTrauma(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> response = proxyVisitPost(id, "/trauma/activate", body, "PCT edTraumaActivate");
        maybePageTraumaTeam(id, body);
        return response;
    }

    @PostMapping("/visits/{id}/trauma/survey")
    public ResponseEntity<Map<String, Object>> traumaSurvey(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return proxyVisitPost(id, "/trauma/survey", body, "PCT edTraumaSurvey");
    }

    @PostMapping("/visits/{id}/trauma/end")
    public ResponseEntity<Map<String, Object>> endTrauma(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return proxyVisitPost(id, "/trauma/end", body != null ? body : Map.of(), "PCT edTraumaEnd");
    }

    @PostMapping("/visits/{id}/page")
    public ResponseEntity<Map<String, Object>> pageTeam(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode pending = requirePayload(pctClient.edVisitPost(id.toString(), "/page", body), "PCT edPage");
            return deliverPage(id, pending, body);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edPage", e);
        }
    }

    @PostMapping("/visits/{id}/disposition")
    public ResponseEntity<Map<String, Object>> disposition(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return proxyVisitPost(id, "/disposition", body, "PCT edDisposition");
    }

    /**
     * Legacy parallel case path beside {@code ed_visit} / emergency episode spine.
     *
     * @deprecated Prefer {@code POST /internal/v1/ed/visits} (open ED visit) or
     * {@code POST /internal/v1/emergency-episodes}. Kept for continuity; new UI must not call this.
     */
    @Deprecated
    @PostMapping("/emergency-cases")
    public ResponseEntity<Map<String, Object>> openEmergencyCase(@RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.openEmergencyCase(body), "PCT openEmergencyCase");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT openEmergencyCase", e);
        }
    }

    // ── Resuscitation, folded under the single ED surface (W4 ED unification) ────────
    // ED resuscitation is inpatient-backed but belongs to the ED journey; the UI now reaches it
    // under /internal/v1/ed/resuscitation/*. /internal/v1/emergency/* remains as a deprecated
    // forwarding alias on CareEmergencyInpatientController until UI cutover.

    @GetMapping("/resuscitation/activations")
    public ResponseEntity<Map<String, Object>> edResusActivations() {
        try {
            return ResponseEntity.ok(Map.of("data",
                    requirePayload(inpatientClient.listEmergencyActivations(), "Inpatient listEmergencyActivations")));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listEmergencyActivations", e);
        }
    }

    @PostMapping("/resuscitation/activate")
    public ResponseEntity<Map<String, Object>> edResusActivate(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data",
                    requirePayload(inpatientClient.activateEmergency(body), "Inpatient activateEmergency")));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient activateEmergency", e);
        }
    }

    @PostMapping("/resuscitation/{activationId}/action")
    public ResponseEntity<Map<String, Object>> edResusAction(@PathVariable UUID activationId,
                                                             @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data",
                    requirePayload(inpatientClient.logEmergencyAction(activationId.toString(), body), "Inpatient logEmergencyAction")));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient logEmergencyAction", e);
        }
    }

    @PostMapping("/resuscitation/{activationId}/record")
    public ResponseEntity<Map<String, Object>> edResusRecord(@PathVariable UUID activationId,
                                                             @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data",
                    requirePayload(inpatientClient.recordResuscitation(activationId.toString(), body), "Inpatient recordResuscitation")));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient recordResuscitation", e);
        }
    }

    @GetMapping("/resuscitation/{activationId}")
    public ResponseEntity<Map<String, Object>> edResusGet(@PathVariable UUID activationId) {
        try {
            return ResponseEntity.ok(Map.of("data",
                    requirePayload(inpatientClient.getResuscitation(activationId.toString()), "Inpatient getResuscitation")));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getResuscitation", e);
        }
    }

    @PostMapping("/resuscitation/{activationId}/end")
    public ResponseEntity<Map<String, Object>> edResusEnd(@PathVariable UUID activationId,
                                                          @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(Map.of("data",
                    requirePayload(inpatientClient.endEmergency(activationId.toString(), body), "Inpatient endEmergency")));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient endEmergency", e);
        }
    }

    /** Canonical ED surface for phase / CPR / med depth (previously only on /emergency/* ClinicalDepth). */
    @PostMapping("/resuscitation/{activationId}/phases")
    public ResponseEntity<Map<String, Object>> edResusStartPhase(@PathVariable UUID activationId,
                                                                 @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data",
                    requirePayload(inpatientClient.startResuscitationPhase(activationId.toString(), body),
                            "Inpatient startResuscitationPhase")));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient startResuscitationPhase", e);
        }
    }

    @PostMapping("/resuscitation/{activationId}/phases/{phaseId}/end")
    public ResponseEntity<Map<String, Object>> edResusEndPhase(@PathVariable UUID activationId,
                                                               @PathVariable UUID phaseId,
                                                               @RequestBody Map<String, Object> body) {
        try {
            JsonNode ended = inpatientClient.endResuscitationPhase(activationId.toString(), phaseId.toString(), body);
            return ResponseEntity.ok(ended != null && ended.isObject()
                    ? Map.of("data", ended, "ended", true)
                    : Map.of("ended", true));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient endResuscitationPhase", e);
        }
    }

    @GetMapping("/resuscitation/{activationId}/phases")
    public ResponseEntity<Map<String, Object>> edResusPhases(@PathVariable UUID activationId) {
        try {
            return ResponseEntity.ok(Map.of("data",
                    requirePayload(inpatientClient.listResuscitationPhases(activationId.toString()),
                            "Inpatient listResuscitationPhases")));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listResuscitationPhases", e);
        }
    }

    @PostMapping("/resuscitation/{activationId}/cpr-cycles")
    public ResponseEntity<Map<String, Object>> edResusCpr(@PathVariable UUID activationId,
                                                          @RequestBody Map<String, Object> body) {
        Map<String, Object> action = new LinkedHashMap<>(body != null ? body : Map.of());
        action.put("actionType", "CPR_CYCLE");
        action.putIfAbsent("description", "CPR cycle " + action.getOrDefault("cycleNumber", action.get("cycle_number")));
        JsonNode created = inpatientClient.logEmergencyAction(activationId.toString(), action);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created != null ? created : Map.of()));
    }

    @GetMapping("/resuscitation/{activationId}/cpr-cycles")
    public ResponseEntity<Map<String, Object>> edResusCprList(@PathVariable UUID activationId) {
        JsonNode data = inpatientClient.listEmergencyActions(activationId.toString(), "CPR_CYCLE");
        return ResponseEntity.ok(Map.of("data", data != null ? data : java.util.List.of()));
    }

    @PostMapping("/resuscitation/{activationId}/medications")
    public ResponseEntity<Map<String, Object>> edResusMed(@PathVariable UUID activationId,
                                                          @RequestBody Map<String, Object> body) {
        Map<String, Object> action = new LinkedHashMap<>(body != null ? body : Map.of());
        action.put("actionType", "RESUS_MEDICATION");
        String drug = action.getOrDefault("name", action.getOrDefault("medication", "Resuscitation medication")).toString();
        action.putIfAbsent("description", drug + " " + action.getOrDefault("dose", ""));
        JsonNode created = inpatientClient.logEmergencyAction(activationId.toString(), action);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created != null ? created : Map.of()));
    }

    @GetMapping("/resuscitation/{activationId}/medications")
    public ResponseEntity<Map<String, Object>> edResusMedList(@PathVariable UUID activationId) {
        JsonNode data = inpatientClient.listEmergencyActions(activationId.toString(), "RESUS_MEDICATION");
        return ResponseEntity.ok(Map.of("data", data != null ? data : java.util.List.of()));
    }

    // ── Trauma-team roster / ack / escalate (WU2) — passthrough to pct-service ────────
    // The sovereign roster (resolved from the VASHANDI on-call pool at activation) is
    // authoritative; the BFF only forwards the trust context and returns pct's view.

    @GetMapping("/trauma/{traumaId}/team")
    public ResponseEntity<Map<String, Object>> traumaTeam(@PathVariable UUID traumaId) {
        try {
            return ResponseEntity.ok(Map.of("data",
                    requirePayload(pctClient.edTraumaTeam(traumaId.toString()), "PCT edTraumaTeam")));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edTraumaTeam", e);
        }
    }

    @PostMapping("/trauma/{traumaId}/team/{memberId}/ack")
    public ResponseEntity<Map<String, Object>> traumaTeamAck(@PathVariable UUID traumaId,
                                                             @PathVariable UUID memberId,
                                                             @RequestBody(required = false) Map<String, Object> body) {
        try {
            return ResponseEntity.ok(Map.of("data",
                    requirePayload(pctClient.edTraumaTeamAck(traumaId.toString(), memberId.toString(),
                            body != null ? body : Map.of()), "PCT edTraumaTeamAck")));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edTraumaTeamAck", e);
        }
    }

    @PostMapping("/trauma/{traumaId}/team/escalate")
    public ResponseEntity<Map<String, Object>> traumaTeamEscalate(@PathVariable UUID traumaId,
                                                                  @RequestBody(required = false) Map<String, Object> body) {
        try {
            return ResponseEntity.ok(Map.of("data",
                    requirePayload(pctClient.edTraumaTeamEscalate(traumaId.toString(),
                            body != null ? body : Map.of()), "PCT edTraumaTeamEscalate")));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edTraumaTeamEscalate", e);
        }
    }

    // ── Blood-readiness gate + ED pre-arrival board (WU4) — passthrough to pct-service ──

    @GetMapping("/blood-readiness")
    public ResponseEntity<Map<String, Object>> bloodReadiness(@RequestParam String orderId) {
        try {
            return ResponseEntity.ok(Map.of("data",
                    requirePayload(pctClient.edBloodReadiness(orderId), "PCT edBloodReadiness")));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edBloodReadiness", e);
        }
    }

    @GetMapping("/pre-arrival")
    public ResponseEntity<Map<String, Object>> preArrival(@RequestParam(required = false) UUID facilityId) {
        try {
            return ResponseEntity.ok(Map.of("data",
                    requirePayload(pctClient.edPreArrival(facilityId), "PCT edPreArrival")));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edPreArrival", e);
        }
    }

    /**
     * The pre-arrival board was readable but not writable through the BFF: pct has served
     * {@code POST /v1/ed/pre-arrival} since the ED lane was built and nothing could call it, so the
     * board could only ever show rows some other path had created.
     */
    @PostMapping("/pre-arrival")
    public ResponseEntity<Map<String, Object>> upsertPreArrival(@RequestBody Map<String, Object> body) {
        return proxyPct(() -> pctClient.upsertEdPreArrival(body), "PCT upsertEdPreArrival", HttpStatus.CREATED);
    }

    /** EMS arrival: converts a pre-arrival notification into a real ED visit. */
    @PostMapping("/arrival")
    public ResponseEntity<Map<String, Object>> arrival(@RequestBody Map<String, Object> body) {
        return proxyPct(() -> pctClient.edArrivalFromEms(body), "PCT edArrivalFromEms", HttpStatus.CREATED);
    }

    // ── Diagnostics: the ten-state machine's acting and closing transitions ────────────────────
    //
    // Ordering a diagnostic, acting on a critical result and closing an order were all pct-only. The
    // estate's one ED safety event (pct.ed.critical_result) is raised by this lane, and the clinician
    // who must act on it had no route to record that they did.

    @GetMapping("/visits/{visitId}/diagnostics")
    public ResponseEntity<Map<String, Object>> listDiagnostics(@PathVariable UUID visitId) {
        return proxyPct(() -> pctClient.listEdDiagnostics(visitId), "PCT listEdDiagnostics", HttpStatus.OK);
    }

    @PostMapping("/visits/{visitId}/diagnostics")
    public ResponseEntity<Map<String, Object>> orderDiagnostic(@PathVariable UUID visitId,
                                                                @RequestBody(required = false) Map<String, Object> body) {
        return proxyPct(() -> pctClient.orderEdDiagnostic(visitId, body != null ? body : Map.of()),
                "PCT orderEdDiagnostic", HttpStatus.CREATED);
    }

    @PostMapping("/diagnostics/reconcile-critical")
    public ResponseEntity<Map<String, Object>> reconcileCritical(@RequestBody Map<String, Object> body) {
        return proxyPct(() -> pctClient.reconcileEdCriticalResult(body), "PCT reconcileEdCriticalResult", HttpStatus.OK);
    }

    @PostMapping("/diagnostics/{linkId}/act")
    public ResponseEntity<Map<String, Object>> actOnDiagnostic(@PathVariable UUID linkId,
                                                                @RequestBody(required = false) Map<String, Object> body) {
        return proxyPct(() -> pctClient.actOnEdDiagnostic(linkId, body != null ? body : Map.of()),
                "PCT actOnEdDiagnostic", HttpStatus.OK);
    }

    /** pct refuses to close a critical order that was never acted on; that 4xx must reach the clinician. */
    @PostMapping("/diagnostics/{linkId}/close")
    public ResponseEntity<Map<String, Object>> closeDiagnostic(@PathVariable UUID linkId,
                                                                @RequestBody Map<String, Object> body) {
        return proxyPct(() -> pctClient.closeEdDiagnostic(linkId, body), "PCT closeEdDiagnostic", HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> proxyPct(java.util.function.Supplier<JsonNode> call,
                                                          String op, HttpStatus successStatus) {
        try {
            return ResponseEntity.status(successStatus).body(Map.of("data", requirePayload(call.get(), op)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString(), e);
            }
            throw upstreamFailure(op, e);
        } catch (Exception e) {
            throw upstreamFailure(op, e);
        }
    }

    @GetMapping("/pathways")
    public ResponseEntity<Map<String, Object>> listEdPathways() {
        try {
            JsonNode data = requirePayload(ckpClient.listPathways(), "CKP listPathways");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("CKP listPathways", e);
        }
    }

    @PostMapping("/pathways/sessions/{sessionId}/advance")
    public ResponseEntity<Map<String, Object>> advancePathway(
            @PathVariable UUID sessionId, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(ckpClient.advancePathwaySession(sessionId, body), "CKP advancePathway");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("CKP advancePathway", e);
        }
    }

    private ResponseEntity<Map<String, Object>> proxyVisitPost(
            UUID id, String action, Map<String, Object> body, String op) {
        try {
            JsonNode data = requirePayload(pctClient.edVisitPost(id.toString(), action, body), op);
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // A 4xx from pct is a client/validation error, not an upstream outage. The triage
            // endpoint now returns 422 for an unassessed patient (NOT_TRIAGEABLE) instead of
            // defaulting to acuity 3 — surface that status and the upstream message so a clinician
            // is told to complete the assessment, rather than collapsing it to a 502 that reads as
            // "the service is down". Only genuine 5xx / transport failures fall through to 502.
            if (e.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString(), e);
            }
            throw upstreamFailure(op, e);
        } catch (Exception e) {
            throw upstreamFailure(op, e);
        }
    }

    private void maybePageTraumaTeam(UUID visitId, Map<String, Object> body) {
        Map<String, Object> pageBody = new LinkedHashMap<>();
        pageBody.put("pageType", "TRAUMA_TEAM");
        pageBody.put("recipientRole", "TRAUMA_TEAM");
        pageBody.put("message", "Trauma activation Level " + body.getOrDefault("traumaLevel", "2")
                + " — " + body.getOrDefault("mechanism", "unspecified"));
        pageBody.put("traumaLevel", body.get("traumaLevel"));
        pageBody.put("mechanism", body.get("mechanism"));
        try {
            JsonNode pending = pctClient.edVisitPost(visitId.toString(), "/page", pageBody);
            deliverPage(visitId, pending, pageBody);
        } catch (Exception ex) {
            log.warn("Trauma team auto-page failed (non-blocking): {}", ex.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> deliverPage(UUID visitId, JsonNode pending, Map<String, Object> body) {
        String pageId = pending.path("page").path("page_id").asText(null);
        if (pageId == null) pageId = pending.path("page_id").asText(null);

        String pageType = String.valueOf(body.getOrDefault("pageType", "TEAM")).toUpperCase();
        String templateKey = switch (pageType) {
            case "CODE_BLUE" -> "ED_CODE_BLUE";
            case "TRAUMA", "TRAUMA_TEAM" -> "ED_TRAUMA_TEAM";
            case "CONSULT", "STAT_CONSULT" -> "ED_STAT_CONSULT";
            default -> "ED_TEAM_PAGE";
        };

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("message", body.getOrDefault("message", "ED clinical page"));
        variables.put("patientId", body.getOrDefault("patientId", visitId.toString()));
        variables.put("location", body.getOrDefault("location", "ED"));
        variables.put("zone", body.getOrDefault("zone", ""));
        variables.put("teamLeader", body.getOrDefault("teamLeader", ""));
        variables.put("traumaLevel", body.getOrDefault("traumaLevel", ""));
        variables.put("mechanism", body.getOrDefault("mechanism", ""));
        variables.put("recipientRole", body.getOrDefault("recipientRole", "ED_TEAM"));
        variables.put("pageType", pageType);

        String notificationRef = null;
        String supportRef = null;
        try {
            String recipient = "ed:" + body.getOrDefault("recipientRole", "TEAM");
            Map<String, Object> notify = Map.of(
                    "templateKey", templateKey,
                    "channel", "CODE_BLUE".equals(pageType) || pageType.contains("TRAUMA") ? "PUSH" : "SMS",
                    "to", recipient,
                    "variables", variables,
                    "messageKind", "CLINICAL_PAGE");
            JsonNode sent = notificationClient.sendNotification(notify);
            if (sent != null && sent.has("id")) notificationRef = sent.get("id").asText();
        } catch (Exception ex) {
            log.warn("ED page notification failed: {}", ex.getMessage());
        }

        try {
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("subject", "ED " + pageType + " — " + visitId);
            ticket.put("description", String.valueOf(variables.get("message")));
            ticket.put("category", "CLINICAL_PAGE");
            ticket.put("priority", "STAT");
            JsonNode created = supportClient.createTicket(ticket);
            if (created != null && created.has("id")) supportRef = created.get("id").asText();
        } catch (Exception ex) {
            log.warn("ED support ticket page failed: {}", ex.getMessage());
        }

        if (pageId != null) {
            Map<String, Object> delivered = new LinkedHashMap<>();
            if (notificationRef != null) delivered.put("notificationRef", notificationRef);
            if (supportRef != null) delivered.put("supportTicketRef", supportRef);
            try {
                pctClient.markEdPageDelivered(pageId, delivered);
            } catch (Exception ex) {
                log.warn("Mark ED page delivered failed: {}", ex.getMessage());
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", pending,
                "notification_ref", notificationRef != null ? notificationRef : "",
                "support_ticket_ref", supportRef != null ? supportRef : ""));
    }
}
