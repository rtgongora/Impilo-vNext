package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.daidzai.core.EmsDispatchService;
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

    public EmsMissionController(EmsDispatchService service) {
        this.service = service;
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

    private static String str(Map<String, Object> b, String k) {
        Object v = b == null ? null : b.get(k);
        return v == null ? null : v.toString();
    }
}
