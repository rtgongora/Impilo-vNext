package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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
     *         subjectCpid?, subjectTempRef?, firstPhase?, ownerRef?}}.
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
                uuid(body, "incidentId"), str(body, "subjectIdentityMode"), str(body, "subjectCpid"),
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

    /**
     * Resolve the episode to its PCT anchor on facility arrival — the flow that closes CC-5 V-3.
     * Body: {@code {pctJourneyId, pctEmergencyEpisodeId?}}. Idempotent (same journey = no-op); a
     * different journey is a 409 (one facility visit is one journey), not a silent overwrite.
     */
    @PostMapping("/{episodeId}/continuum-link")
    public ResponseEntity<Map<String, Object>> continuumLink(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID episodeId,
            @RequestBody Map<String, Object> body) {
        try {
            service.continuumLink(tenantId, episodeId, str(body, "pctJourneyId"), uuid(body, "pctEmergencyEpisodeId"));
            return ResponseEntity.ok(service.episodeView(tenantId, episodeId));
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        }
    }

    /**
     * Absorb one episode into another (the two-entry-path duplicate). Body:
     * {@code {absorbedEpisodeId, reason?}} — the path id is the survivor. Idempotent on re-adopting
     * the same pair; re-adopting an already-merged episode into a different survivor is a 409.
     */
    @PostMapping("/{episodeId}/adopt")
    public ResponseEntity<Map<String, Object>> adopt(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID episodeId,
            @RequestBody Map<String, Object> body) {
        try {
            service.adopt(tenantId, episodeId, uuid(body, "absorbedEpisodeId"), str(body, "reason"));
            return ResponseEntity.ok(service.episodeView(tenantId, episodeId));
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        }
    }

    /**
     * The V-3 enforcement sweep: OPEN episodes that reached a facility phase without a PCT anchor.
     * The monitoring that replaced the schema CHECK V201 withdrew.
     */
    @GetMapping("/unanchored")
    public java.util.List<Map<String, Object>> unanchored(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return service.unanchoredInFacility(tenantId).stream()
                .map(e -> service.episodeView(tenantId, e.getId()))
                .toList();
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
