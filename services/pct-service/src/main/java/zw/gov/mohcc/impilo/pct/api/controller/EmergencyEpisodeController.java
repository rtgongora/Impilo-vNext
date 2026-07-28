package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.EmergencyEpisodeService;
import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The HTTP surface for the emergency episode spine.
 *
 * <p>{@link EmergencyEpisodeService} has carried the episode FSM (W2), the six safety alerts' clock
 * source (W5) and the acceptance handshake (W9) since it was written — fully unit-tested (27 cases)
 * and verified against real Postgres — with no controller ever wired to it. Every one of those waves
 * was reachable from a repository test and from nowhere else: nothing could open an episode, move it
 * through the FSM, or invoke the handshake over the network. This is that missing wiring.
 *
 * <p>Row-mapping to {@code Map<String, Object>} is deliberately done HERE, not by changing
 * {@link EmergencyEpisodeService}'s return types — those 27 tests assert on entity getters directly,
 * and this controller can be added with zero risk to already-verified code.
 */
@RestController
@RequestMapping("/v1/emergency")
public class EmergencyEpisodeController {

    private final EmergencyEpisodeService episodeService;

    public EmergencyEpisodeController(EmergencyEpisodeService episodeService) {
        this.episodeService = episodeService;
    }

    @PostMapping("/episodes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> open(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var result = episodeService.open(toOpenCommand(body, ctx));
        Map<String, Object> out = episodeRow(result.episode());
        out.put("created", result.created());
        out.put("degraded", result.degraded());
        String correlationId = ctx.correlationId().toString();
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(out, correlationId));
    }

    @GetMapping("/episodes/{episodeId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID episodeId) {
        TrustContext ctx = TrustContextHolder.require();
        var episode = episodeService.get(episodeId, ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(episodeRow(episode), ctx.correlationId().toString()));
    }

    /** The operational board: every open episode at a facility. */
    @GetMapping("/episodes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> board(@RequestParam UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        var episodes = episodeService.board(ctx.tenantId(), facilityId);
        return ResponseEntity.ok(ApiResponse.ok(episodes.stream().map(this::episodeRow).toList(),
                ctx.correlationId().toString()));
    }

    /** Resolve the CC-5 anchor on arrival — the moment a pre-alert becomes a real PCT-anchored episode. */
    @PostMapping("/episodes/{episodeId}/arrive")
    public ResponseEntity<ApiResponse<Map<String, Object>>> arrive(@PathVariable UUID episodeId,
                                                                    @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String journeyId = str(body, "journeyId", "journey_id");
        Long encounterId = longVal(body, "encounterId", "encounter_id");
        var episode = episodeService.resolveAnchorOnArrival(episodeId, ctx.tenantId(), journeyId, encounterId);
        return ResponseEntity.ok(ApiResponse.ok(episodeRow(episode), ctx.correlationId().toString()));
    }

    @PostMapping("/episodes/{episodeId}/transition")
    public ResponseEntity<ApiResponse<Map<String, Object>>> transition(@PathVariable UUID episodeId,
                                                                        @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String targetRaw = str(body, "state", "target", "targetState", "target_state");
        if (targetRaw == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "state is required");
        }
        EmergencyEpisodeState target;
        try {
            target = EmergencyEpisodeState.valueOf(targetRaw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unknown episode state: " + targetRaw);
        }
        String outcome = str(body, "outcome");
        var episode = episodeService.transition(episodeId, ctx.tenantId(), target, outcome);
        return ResponseEntity.ok(ApiResponse.ok(episodeRow(episode), ctx.correlationId().toString()));
    }

    /** Confirm where the patient is — resets the staleness clock the LOCATION_UNKNOWN alert (W5) reads. */
    @PostMapping("/episodes/{episodeId}/location")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmLocation(@PathVariable UUID episodeId,
                                                                             @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String location = str(body, "location", "currentLocation", "current_location");
        var episode = episodeService.confirmLocation(episodeId, ctx.tenantId(), location);
        return ResponseEntity.ok(ApiResponse.ok(episodeRow(episode), ctx.correlationId().toString()));
    }

    // ── The acceptance handshake (W9) ───────────────────────────────────────────────────────

    @PostMapping("/episodes/{episodeId}/handover")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestHandover(@PathVariable UUID episodeId,
                                                                             @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String requestedBy = str(body, "requestedBy", "requested_by");
        var handover = episodeService.requestHandover(episodeId, ctx.tenantId(),
                str(body, "targetType", "target_type"),
                str(body, "targetService", "target_service"),
                uuidVal(body, "targetFacilityId", "target_facility_id"),
                requestedBy != null ? requestedBy : ctx.actorId(),
                str(body, "reason", "requestReason", "request_reason"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(handoverRow(handover), ctx.correlationId().toString()));
    }

    @GetMapping("/episodes/{episodeId}/handovers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> handoverHistory(@PathVariable UUID episodeId) {
        TrustContext ctx = TrustContextHolder.require();
        var handovers = episodeService.handoverHistory(episodeId, ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(handovers.stream().map(this::handoverRow).toList(),
                ctx.correlationId().toString()));
    }

    @PostMapping("/handovers/{handoverId}/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acceptHandover(@PathVariable UUID handoverId,
                                                                            @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String acceptedBy = str(body, "acceptedBy", "accepted_by");
        var episode = episodeService.acceptHandover(handoverId, ctx.tenantId(),
                acceptedBy != null ? acceptedBy : ctx.actorId(),
                str(body, "acceptingRef", "accepting_ref"),
                str(body, "acceptingService", "accepting_service"));
        return ResponseEntity.ok(ApiResponse.ok(episodeRow(episode), ctx.correlationId().toString()));
    }

    @PostMapping("/handovers/{handoverId}/decline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> declineHandover(@PathVariable UUID handoverId,
                                                                             @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String declinedBy = str(body, "declinedBy", "declined_by");
        var episode = episodeService.declineHandover(handoverId, ctx.tenantId(),
                declinedBy != null ? declinedBy : ctx.actorId(),
                str(body, "reason", "declineReason", "decline_reason"));
        return ResponseEntity.ok(ApiResponse.ok(episodeRow(episode), ctx.correlationId().toString()));
    }

    @PostMapping("/handovers/{handoverId}/expire")
    public ResponseEntity<ApiResponse<Map<String, Object>>> expireHandover(@PathVariable UUID handoverId,
                                                                            @RequestBody(required = false) Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> b = body != null ? body : Map.of();
        String expiredBy = str(b, "expiredBy", "expired_by");
        var episode = episodeService.expireHandover(handoverId, ctx.tenantId(),
                expiredBy != null ? expiredBy : ctx.actorId(),
                str(b, "ritoCaseRef", "rito_case_ref"));
        return ResponseEntity.ok(ApiResponse.ok(episodeRow(episode), ctx.correlationId().toString()));
    }

    // ── Mapping + parsing ────────────────────────────────────────────────────────────────────

    private EmergencyEpisodeService.OpenEpisodeCommand toOpenCommand(Map<String, Object> body, TrustContext ctx) {
        UUID facilityId = uuidVal(body, "facilityId", "facility_id");
        if (facilityId == null) facilityId = ctx.facilityId();
        String actorId = str(body, "actorId", "actor_id");
        return new EmergencyEpisodeService.OpenEpisodeCommand(
                ctx.tenantId(),
                facilityId,
                str(body, "entryRoute", "entry_route"),
                str(body, "entrySourceService", "entry_source_service"),
                str(body, "entrySourceRef", "entry_source_ref"),
                str(body, "entryContextJson", "entry_context_json"),
                str(body, "episodeClass", "episode_class"),
                str(body, "subjectCpid", "subject_cpid"),
                str(body, "journeyId", "journey_id"),
                longVal(body, "encounterId", "encounter_id"),
                uuidVal(body, "traumaEpisodeId", "trauma_episode_id"),
                uuidVal(body, "emergencyCaseId", "emergency_case_id"),
                Boolean.TRUE.equals(body.get("sensitive")),
                OffsetDateTime.now(),
                actorId != null ? actorId : ctx.actorId());
    }

    private Map<String, Object> episodeRow(EmergencyEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("episode_id", e.getEpisodeId().toString());
        m.put("episode_reference", e.getEpisodeReference());
        m.put("tenant_id", e.getTenantId().toString());
        m.put("facility_id", e.getFacilityId().toString());
        m.put("state", e.getState());
        m.put("entry_route", e.getEntryRoute());
        m.put("episode_class", e.getEpisodeClass());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("trauma_episode_id", e.getTraumaEpisodeId() != null ? e.getTraumaEpisodeId().toString() : null);
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("identity_mode", e.getIdentityMode());
        m.put("sensitive", e.isSensitive());
        m.put("arrived_at", e.getArrivedAt() != null ? e.getArrivedAt().toString() : null);
        m.put("anchor_resolved_at", e.getAnchorResolvedAt() != null ? e.getAnchorResolvedAt().toString() : null);
        m.put("current_location", e.getCurrentLocation());
        m.put("location_confirmed_at", e.getLocationConfirmedAt() != null ? e.getLocationConfirmedAt().toString() : null);
        m.put("handover_id", e.getHandoverId() != null ? e.getHandoverId().toString() : null);
        m.put("outcome", e.getOutcome());
        m.put("closed_at", e.getClosedAt() != null ? e.getClosedAt().toString() : null);
        return m;
    }

    private Map<String, Object> handoverRow(EmergencyHandoverEntity h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("handover_id", h.getHandoverId().toString());
        m.put("episode_id", h.getEpisodeId().toString());
        m.put("target_type", h.getTargetType());
        m.put("target_service", h.getTargetService());
        m.put("status", h.getStatus());
        m.put("requested_by", h.getRequestedBy());
        m.put("requested_at", h.getRequestedAt() != null ? h.getRequestedAt().toString() : null);
        m.put("accepted_by", h.getAcceptedBy());
        m.put("accepting_ref", h.getAcceptingRef());
        m.put("accepting_service", h.getAcceptingService());
        m.put("declined_by", h.getDeclinedBy());
        m.put("decline_reason", h.getDeclineReason());
        m.put("expired_at", h.getExpiredAt() != null ? h.getExpiredAt().toString() : null);
        m.put("rito_case_ref", h.getRitoCaseRef());
        return m;
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    private static UUID uuidVal(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try { return UUID.fromString(s.trim()); } catch (IllegalArgumentException e) { return null; }
    }

    private static Long longVal(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try { return Long.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
