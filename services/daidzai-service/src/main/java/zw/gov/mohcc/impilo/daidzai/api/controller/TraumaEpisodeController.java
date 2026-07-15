package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.daidzai.core.TraumaEpisodeService;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodeEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodePhaseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Canonical trauma-episode spine API (architecture decision #1). Internal service-plane surface —
 * DAIDZAI mints on incident triage; PCT mints on ED-first walk-in; phase owners register their
 * phase onto the read-model timeline. Authorization is enforced at the trust plane
 * (Envoy ext_authz → TSHEPO, policy {@code impilo.daidzai}); this controller forwards context.
 */
@RestController
@RequestMapping("/internal/v1/daidzai/trauma-episodes")
public class TraumaEpisodeController {

    private final TraumaEpisodeService service;

    public TraumaEpisodeController(TraumaEpisodeService service) {
        this.service = service;
    }

    /**
     * Idempotent dual-entry mint. Body:
     * {@code {originService, originKind, originKey, incidentId?, subjectIdentityMode?,
     *         subjectHealthId?, subjectTempRef?, firstPhase?, ownerRef?}}.
     * Returns 200 when the {@code (tenant, originKey)} episode already exists, 201 when newly minted.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> mint(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        String originKey = str(body, "originKey");
        boolean existed = service.episodeByOriginExists(tenantId, originKey);
        TraumaEpisodeEntity ep = service.mint(tenantId,
                str(body, "originService"), str(body, "originKind"), originKey,
                uuid(body, "incidentId"), str(body, "subjectIdentityMode"), str(body, "subjectHealthId"),
                str(body, "subjectTempRef"), str(body, "firstPhase"), str(body, "ownerRef"));
        HttpStatus code = existed ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(code).body(service.episodeView(tenantId, ep.getId()));
    }

    /** Register a phase-owner event onto the timeline (idempotent). */
    @PostMapping("/{episodeId}/phases")
    public ResponseEntity<TraumaEpisodePhaseEntity> registerPhase(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID episodeId,
            @RequestBody Map<String, Object> body) {
        TraumaEpisodePhaseEntity row = service.registerPhase(tenantId, episodeId,
                str(body, "phase"), str(body, "ownerService"), str(body, "ownerRef"),
                str(body, "status"), str(body, "eventType"), str(body, "payloadJson"));
        return ResponseEntity.status(HttpStatus.CREATED).body(row);
    }

    /** Episode + ordered phase timeline (the resolvable read-model view). */
    @GetMapping("/{episodeId}")
    public Map<String, Object> get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID episodeId) {
        return service.episodeView(tenantId, episodeId);
    }

    @PostMapping("/{episodeId}/close")
    public TraumaEpisodeEntity close(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID episodeId,
            @RequestBody(required = false) Map<String, Object> body) {
        return service.close(tenantId, episodeId, body != null ? str(body, "reason") : null);
    }

    private static String str(Map<String, Object> b, String k) {
        Object v = b == null ? null : b.get(k);
        return v == null ? null : v.toString();
    }

    private static UUID uuid(Map<String, Object> b, String k) {
        String s = str(b, k);
        if (s == null || s.isBlank()) return null;
        return UUID.fromString(s);
    }
}
