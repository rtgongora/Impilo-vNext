package zw.gov.mohcc.impilo.daidzai.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.daidzai.core.EmsDispatchService;
import zw.gov.mohcc.impilo.daidzai.core.EmsEpcrService;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsEpcrEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsEpcrEventEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsMissionEntity;

import java.util.Map;
import java.util.UUID;

/**
 * EMS clinical-dispatch API (architecture decision #2 — EMS lives in DAIDZAI). Dispatch a crew to a
 * patient and walk the mission state machine to a facility handover. Authorization is enforced at
 * the trust plane (Envoy ext_authz → TSHEPO, policy {@code impilo.daidzai}); this controller
 * forwards context. Dispatch is idempotent per incident.
 */
@RestController
@RequestMapping("/internal/v1/daidzai/ems")
public class EmsMissionController {

    private final EmsDispatchService service;
    private final EmsEpcrService epcrService;
    private final ObjectMapper mapper;

    public EmsMissionController(EmsDispatchService service, EmsEpcrService epcrService, ObjectMapper mapper) {
        this.service = service;
        this.epcrService = epcrService;
        this.mapper = mapper;
    }

    /** Dispatch a clinical EMS mission for an incident (idempotent per incident). */
    @PostMapping("/incidents/{incidentId}/dispatch")
    public ResponseEntity<EmsMissionEntity> dispatch(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID incidentId,
            @RequestBody(required = false) Map<String, Object> body) {
        EmsMissionEntity m = service.dispatch(tenantId, incidentId,
                str(body, "callSign"), str(body, "ambulanceAssetId"),
                str(body, "crew"), str(body, "priority"));
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    /** Advance the mission to the next validated state. */
    @PostMapping("/missions/{missionId}/advance")
    public EmsMissionEntity advance(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID missionId,
            @RequestBody Map<String, Object> body) {
        return service.advance(tenantId, missionId,
                str(body, "toState"), str(body, "note"), str(body, "pctEncounterRef"));
    }

    @GetMapping("/missions/{missionId}")
    public EmsMissionEntity get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID missionId) {
        return service.getMission(tenantId, missionId);
    }

    @GetMapping("/incidents/{incidentId}/mission")
    public EmsMissionEntity byIncident(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID incidentId) {
        return service.missionForIncident(tenantId, incidentId);
    }

    // ── Prehospital ePCR (captured on the responder mobile app) ──────────────

    /** Create/update the ePCR for a mission (primary survey + patient binding). */
    @PostMapping("/missions/{missionId}/epcr")
    public ResponseEntity<EmsEpcrEntity> upsertEpcr(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID missionId,
            @RequestBody(required = false) Map<String, Object> body) {
        EmsEpcrEntity epcr = epcrService.upsertEpcr(tenantId, missionId,
                str(body, "patientHealthId"), json(body == null ? null : body.get("primarySurvey")),
                str(body, "narrative"));
        return ResponseEntity.status(HttpStatus.CREATED).body(epcr);
    }

    /** Append a timed prehospital observation (VITALS / GCS / INTERVENTION / MEDICATION / NOTE). */
    @PostMapping("/missions/{missionId}/epcr/events")
    public ResponseEntity<EmsEpcrEventEntity> recordEpcrEvent(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID missionId,
            @RequestBody Map<String, Object> body) {
        EmsEpcrEventEntity ev = epcrService.recordEvent(tenantId, missionId,
                str(body, "eventType"), str(body, "channel"), json(body.get("payload")));
        return ResponseEntity.status(HttpStatus.CREATED).body(ev);
    }

    /** The ePCR + its ordered observation time-series + a compact snapshot. */
    @GetMapping("/missions/{missionId}/epcr")
    public Map<String, Object> getEpcr(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID missionId) {
        return epcrService.getEpcr(tenantId, missionId);
    }

    private static String str(Map<String, Object> b, String k) {
        Object v = b == null ? null : b.get(k);
        return v == null ? null : v.toString();
    }

    /** Serialise a nested JSON object/array from the request into a canonical JSON string for jsonb. */
    private String json(Object node) {
        if (node == null) return null;
        if (node instanceof String s) return s;
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
