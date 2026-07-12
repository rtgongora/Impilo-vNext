package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.daidzai.core.EmergencyService;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emergency spine API. Authorization is enforced at the trust plane (Envoy ext_authz -> TSHEPO
 * PDP, policy {@code impilo.daidzai}); this controller forwards the trust context. SOS create is
 * intentionally low-friction (citizen/bystander life-safety); privileged dispatch/command actions
 * are gated by the PDP role rules seeded in tshepo V028.
 */
@RestController
@RequestMapping("/internal/v1/daidzai")
public class EmergencyController {

    private final EmergencyService service;

    public EmergencyController(EmergencyService service) {
        this.service = service;
    }

    // ---- SOS request intake ----
    @PostMapping("/requests")
    public ResponseEntity<EmergencyRequestEntity> createRequest(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        EmergencyRequestEntity r = service.createRequest(tenantId,
                str(body, "requesterType"), actorId,
                str(body, "subjectIdentityMode"), str(body, "subjectHealthId"),
                str(body, "subjectTempRef"), str(body, "subjectLabel"),
                str(body, "emergencyCategory"), str(body, "severity"), str(body, "description"),
                dbl(body, "lat"), dbl(body, "lng"), str(body, "locationDescription"),
                str(body, "attachmentsRef"), str(body, "channel"), str(body, "callbackNumber"));
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    @GetMapping("/requests/{id}")
    public EmergencyRequestEntity getRequest(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID id) {
        return service.getRequest(tenantId, id);
    }

    // ---- Dispatcher callback verification (PD-3 dispatch gate release) ----
    // Operator/dispatcher-only: rides the authenticated daidzai lane through the PDP (NOT the public
    // lane). Sets the request's callback as verified and releases it for triage/dispatch.
    @PostMapping("/requests/{id}/verify-callback")
    public EmergencyRequestEntity verifyCallback(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id) {
        return service.verifyCallback(tenantId, id, actorId);
    }

    // ---- Triage a request into an incident ----
    @PostMapping("/requests/{id}/triage")
    public ResponseEntity<EmergencyIncidentEntity> triage(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.triageRequestToIncident(tenantId, id, actorId));
    }

    // ---- Provider / facility-triggered escalation: incident directly ----
    @PostMapping("/incidents")
    public ResponseEntity<EmergencyIncidentEntity> escalate(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        EmergencyIncidentEntity inc = service.escalateToIncident(tenantId,
                str(body, "emergencyCategory"), str(body, "severity"), str(body, "description"),
                uuid(body, "facilityId"), str(body, "subjectIdentityMode"), str(body, "subjectHealthId"),
                dbl(body, "lat"), dbl(body, "lng"), str(body, "locationDescription"), actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(inc);
    }

    @GetMapping("/incidents")
    public List<EmergencyIncidentEntity> listIncidents(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return service.listIncidents(tenantId, type, status);
    }

    @GetMapping("/incidents/{id}")
    public EmergencyIncidentEntity getIncident(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID id) {
        return service.getIncident(tenantId, id);
    }

    // ---- Dispatch need + mission tracking ----
    @PostMapping("/incidents/{id}/dispatch")
    public ResponseEntity<MissionEventEntity> dispatch(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        String note = body != null ? str(body, "note") : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestDispatch(tenantId, id, note, actorId));
    }

    @PostMapping("/incidents/{id}/mission-events")
    public ResponseEntity<MissionEventEntity> missionEvent(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordMissionEvent(tenantId, id,
                required(body, "status"), str(body, "nhumeMissionRef"), str(body, "note"), actorId, actorType));
    }

    @GetMapping("/incidents/{id}/missions")
    public List<MissionEventEntity> missions(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID id) {
        return service.missionTimeline(tenantId, id);
    }

    // ---- Clinical handoff to PCT ----
    @PostMapping("/incidents/{id}/handoff")
    public EmergencyIncidentEntity handoff(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return service.recordClinicalHandoff(tenantId, id, required(body, "pctEncounterRef"), actorId);
    }

    // ---- Death outcome → Death & Post-Death Pathway (WS#7 → WS#8 seam) ----
    @PostMapping("/incidents/{id}/death-outcome")
    public EmergencyIncidentEntity deathOutcome(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        String placeContext = body != null ? str(body, "placeOfDeathContext") : null;
        return service.recordDeathOutcome(tenantId, id, placeContext, actorId);
    }

    // ---- Resource requests ----
    @PostMapping("/incidents/{id}/resources")
    public ResponseEntity<ResourceRequestEntity> requestResource(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestResource(tenantId, id,
                required(body, "resourceType"), str(body, "resourceOwner"), intVal(body, "quantity"),
                str(body, "detail"), actorId));
    }

    @GetMapping("/incidents/{id}/resources")
    public List<ResourceRequestEntity> resources(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID id) {
        return service.resources(tenantId, id);
    }

    // ---- helpers ----
    private static String required(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || v.toString().isBlank()) throw new IllegalArgumentException("Missing " + k);
        return v.toString();
    }
    private static String str(Map<String, Object> b, String k) {
        Object v = b.get(k); return v != null ? v.toString() : null;
    }
    private static Double dbl(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || v.toString().isBlank()) return null;
        return Double.valueOf(v.toString());
    }
    private static Integer intVal(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || v.toString().isBlank()) return null;
        return Integer.valueOf(v.toString());
    }
    private static UUID uuid(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || v.toString().isBlank()) return null;
        return UUID.fromString(v.toString());
    }
}
