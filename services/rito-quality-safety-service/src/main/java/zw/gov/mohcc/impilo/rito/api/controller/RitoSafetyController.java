package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.SafetyIncidentService;
import zw.gov.mohcc.impilo.rito.persistence.entity.SafetyIncidentEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/rito")
public class RitoSafetyController {

    private final SafetyIncidentService safetyIncidentService;

    public RitoSafetyController(SafetyIncidentService safetyIncidentService) {
        this.safetyIncidentService = safetyIncidentService;
    }

    @PostMapping("/cases/{caseId}/safety-incident")
    public ResponseEntity<SafetyIncidentEntity> recordIncident(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID caseId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> contributingFactors = body.get("contributing_factors") instanceof Map
                ? (Map<String, Object>) body.get("contributing_factors") : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(safetyIncidentService.recordIncident(
                tenantId, caseId,
                required(body, "incident_type"), str(body, "harm_level"),
                odt(body, "occurred_at"), str(body, "location_detail"),
                contributingFactors, str(body, "immediate_actions"),
                boolVal(body, "sentinel"), actorId, actorType));
    }

    @PutMapping("/cases/{caseId}/safety-incident/rca")
    public SafetyIncidentEntity updateRca(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID caseId,
            @RequestBody Map<String, Object> body) {
        return safetyIncidentService.updateRca(tenantId, caseId,
                str(body, "rca_method"), str(body, "rca_summary"));
    }

    @GetMapping("/cases/{caseId}/safety-incident")
    public SafetyIncidentEntity get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID caseId) {
        return safetyIncidentService.get(tenantId, caseId);
    }

    @GetMapping("/safety-incidents/sentinel")
    public List<SafetyIncidentEntity> listSentinel(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return safetyIncidentService.listSentinel(tenantId);
    }

    // ---- request-map helpers ----

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static Boolean boolVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            return Boolean.FALSE;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(v.toString());
    }

    private static OffsetDateTime odt(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(v.toString());
    }
}
