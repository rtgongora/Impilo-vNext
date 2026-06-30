package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.daidzai.core.DisasterService;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AffectedSiteEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Disaster / MCI command API. Privileged command actions (declare/close) are gated at the PDP
 * by the COMMAND/RESPONSE_COORDINATOR role rules seeded in tshepo V028; the controller forwards
 * the trust context.
 */
@RestController
@RequestMapping("/internal/v1/daidzai/disasters")
public class DisasterController {

    private final DisasterService service;

    public DisasterController(DisasterService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EmergencyIncidentEntity> declare(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        EmergencyIncidentEntity inc = service.declareDisaster(tenantId,
                str(body, "incidentType"), str(body, "emergencyCategory"), str(body, "severity"),
                str(body, "title"), str(body, "description"), str(body, "commandPost"),
                str(body, "incidentCommander"), str(body, "affectedAreaRef"));
        return ResponseEntity.status(HttpStatus.CREATED).body(inc);
    }

    @GetMapping
    public List<EmergencyIncidentEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return service.listDisasters(tenantId);
    }

    @PostMapping("/{id}/sites")
    public ResponseEntity<AffectedSiteEntity> addSite(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        AffectedSiteEntity s = service.addAffectedSite(tenantId, id, required(body, "siteLabel"),
                str(body, "indawoSiteRef"), str(body, "ndilaAreaRef"),
                dbl(body, "lat"), dbl(body, "lng"), intVal(body, "estimatedAffected"));
        return ResponseEntity.status(HttpStatus.CREATED).body(s);
    }

    @GetMapping("/{id}/sites")
    public List<AffectedSiteEntity> sites(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID id) {
        return service.sites(tenantId, id);
    }

    @PostMapping("/{id}/close")
    public EmergencyIncidentEntity close(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        String ritoCaseRef = body != null ? str(body, "ritoCaseRef") : null;
        return service.closeWithAfterAction(tenantId, id, ritoCaseRef);
    }

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
}
