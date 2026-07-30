package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.support.EmergencyHonesty;

import java.util.Map;
import java.util.UUID;

/**
 * BFF proxy for pct's {@code EmergencyEpisodeController} (/v1/emergency/**) — the episode spine, the
 * FSM, and the acceptance handshake had no BFF surface at all until this wave, matching the exact gap
 * {@code PctServiceClient}'s own comment names ("the emergency pack is about to serve the emergency
 * EPISODE at /v1/emergency on pct").
 *
 * <p>Deliberately mounted at {@code /internal/v1/emergency-episodes}, NOT {@code /internal/v1/emergency},
 * to avoid colliding with {@code CareEmergencyInpatientController}'s existing (deprecated) resuscitation
 * aliases already squatting on that namespace.
 *
 * <p>pct is sovereign here — this controller orchestrates nothing extra, it only forwards. A 4xx from
 * pct (an invalid FSM transition, a handover already resolved, a disposition that contradicts the
 * episode's state) is a real client-facing validation error and is surfaced as-is; only a genuine
 * upstream outage collapses to 502.
 */
@RestController
@RequestMapping("/internal/v1/emergency-episodes")
public class EmergencyEpisodeController {

    private static final Logger log = LoggerFactory.getLogger(EmergencyEpisodeController.class);

    private final PctServiceClient pctClient;

    public EmergencyEpisodeController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> open(@RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.openEmergencyEpisode(body), "PCT openEmergencyEpisode", HttpStatus.CREATED);
    }

    @GetMapping("/{episodeId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID episodeId) {
        return proxyGet(() -> pctClient.getEmergencyEpisode(episodeId), "PCT getEmergencyEpisode");
    }

    /** The facility board: every open episode. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> board(@RequestParam UUID facilityId) {
        return proxyGet(() -> pctClient.emergencyEpisodeBoard(facilityId), "PCT emergencyEpisodeBoard");
    }

    @PostMapping("/{episodeId}/arrive")
    public ResponseEntity<Map<String, Object>> arrive(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.arriveEmergencyEpisode(episodeId, body), "PCT arriveEmergencyEpisode", HttpStatus.OK);
    }

    @PostMapping("/{episodeId}/transition")
    public ResponseEntity<Map<String, Object>> transition(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.transitionEmergencyEpisode(episodeId, body), "PCT transitionEmergencyEpisode", HttpStatus.OK);
    }

    @PostMapping("/{episodeId}/location")
    public ResponseEntity<Map<String, Object>> confirmLocation(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.confirmEmergencyEpisodeLocation(episodeId, body), "PCT confirmEmergencyEpisodeLocation", HttpStatus.OK);
    }

    // ── The acceptance handshake ─────────────────────────────────────────────────────────────

    @PostMapping("/{episodeId}/handover")
    public ResponseEntity<Map<String, Object>> requestHandover(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.requestEmergencyHandover(episodeId, body), "PCT requestEmergencyHandover", HttpStatus.CREATED);
    }

    @GetMapping("/{episodeId}/handovers")
    public ResponseEntity<Map<String, Object>> handoverHistory(@PathVariable UUID episodeId) {
        return proxyGet(() -> pctClient.emergencyHandoverHistory(episodeId), "PCT emergencyHandoverHistory");
    }

    @PostMapping("/handovers/{handoverId}/accept")
    public ResponseEntity<Map<String, Object>> acceptHandover(@PathVariable UUID handoverId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.acceptEmergencyHandover(handoverId, body), "PCT acceptEmergencyHandover", HttpStatus.OK);
    }

    @PostMapping("/handovers/{handoverId}/decline")
    public ResponseEntity<Map<String, Object>> declineHandover(@PathVariable UUID handoverId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.declineEmergencyHandover(handoverId, body), "PCT declineEmergencyHandover", HttpStatus.OK);
    }

    @PostMapping("/handovers/{handoverId}/expire")
    public ResponseEntity<Map<String, Object>> expireHandover(@PathVariable UUID handoverId,
                                                                @RequestBody(required = false) Map<String, Object> body) {
        return proxyPost(() -> pctClient.expireEmergencyHandover(handoverId, body != null ? body : Map.of()),
                "PCT expireEmergencyHandover", HttpStatus.OK);
    }

    // ── Disposition + observation stay ───────────────────────────────────────────────────────

    @PostMapping("/{episodeId}/disposition")
    public ResponseEntity<Map<String, Object>> recordDisposition(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.recordEmergencyDisposition(episodeId, body), "PCT recordEmergencyDisposition", HttpStatus.CREATED);
    }

    @GetMapping("/{episodeId}/disposition")
    public ResponseEntity<Map<String, Object>> getDisposition(@PathVariable UUID episodeId) {
        return proxyGet(() -> pctClient.getEmergencyDisposition(episodeId), "PCT getEmergencyDisposition");
    }

    // ── Alerts ────────────────────────────────────────────────────────────────────────────────

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> alertBoard(@RequestParam UUID facilityId) {
        return proxyGet(() -> pctClient.emergencyAlertBoard(facilityId), "PCT emergencyAlertBoard");
    }

    @GetMapping("/{episodeId}/alerts")
    public ResponseEntity<Map<String, Object>> episodeAlerts(@PathVariable UUID episodeId) {
        return proxyGet(() -> pctClient.emergencyEpisodeAlerts(episodeId), "PCT emergencyEpisodeAlerts");
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAlert(@PathVariable UUID alertId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.acknowledgeEmergencyAlert(alertId, body), "PCT acknowledgeEmergencyAlert", HttpStatus.OK);
    }

    @PostMapping("/alerts/{alertId}/respond")
    public ResponseEntity<Map<String, Object>> respondAlert(@PathVariable UUID alertId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.respondEmergencyAlert(alertId, body), "PCT respondEmergencyAlert", HttpStatus.OK);
    }

    /**
     * Acknowledging says a human saw it; responding says a human acted; closing says the condition is
     * gone. Only the close lifts pct's partial unique index on open alerts, so until it is reachable the
     * same hazard can never re-raise on the same episode.
     */
    @PostMapping("/alerts/{alertId}/close")
    public ResponseEntity<Map<String, Object>> closeAlert(@PathVariable UUID alertId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.closeEmergencyAlert(alertId, body), "PCT closeEmergencyAlert", HttpStatus.OK);
    }

    // ── Order sets (W7b) ──────────────────────────────────────────────────────────────────────

    @PostMapping("/{episodeId}/order-sets")
    public ResponseEntity<Map<String, Object>> invokeOrderSet(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.invokeEmergencyOrderSet(episodeId, body), "PCT invokeEmergencyOrderSet", HttpStatus.CREATED);
    }

    @GetMapping("/{episodeId}/order-sets")
    public ResponseEntity<Map<String, Object>> orderSets(@PathVariable UUID episodeId) {
        return proxyGet(() -> pctClient.emergencyOrderSets(episodeId), "PCT emergencyOrderSets");
    }

    @GetMapping("/order-sets/{instanceId}")
    public ResponseEntity<Map<String, Object>> orderSet(@PathVariable UUID instanceId) {
        return proxyGet(() -> pctClient.getEmergencyOrderSet(instanceId), "PCT getEmergencyOrderSet");
    }

    @PostMapping("/order-sets/items/{itemId}/order")
    public ResponseEntity<Map<String, Object>> orderItem(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.orderEmergencyOrderSetItem(itemId, body), "PCT orderEmergencyOrderSetItem", HttpStatus.OK);
    }

    /** pct rejects a decline that carries no reason; that 4xx must reach the clinician, not become a 502. */
    @PostMapping("/order-sets/items/{itemId}/decline")
    public ResponseEntity<Map<String, Object>> declineItem(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.declineEmergencyOrderSetItem(itemId, body), "PCT declineEmergencyOrderSetItem", HttpStatus.OK);
    }

    // ── Medication administration (W8a) ────────────────────────────────────────────────────────

    @PostMapping("/{episodeId}/medications")
    public ResponseEntity<Map<String, Object>> recordMedication(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.recordEmergencyMedication(episodeId, body), "PCT recordEmergencyMedication", HttpStatus.CREATED);
    }

    @GetMapping("/{episodeId}/medications")
    public ResponseEntity<Map<String, Object>> medications(@PathVariable UUID episodeId) {
        return proxyGet(() -> pctClient.emergencyMedications(episodeId), "PCT emergencyMedications");
    }

    // ── Observation / short stay (W9b) ─────────────────────────────────────────────────────────

    @PostMapping("/{episodeId}/observation-stays")
    public ResponseEntity<Map<String, Object>> startObservationStay(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.startEmergencyObservationStay(episodeId, body),
                "PCT startEmergencyObservationStay", HttpStatus.CREATED);
    }

    @GetMapping("/{episodeId}/observation-stays")
    public ResponseEntity<Map<String, Object>> observationStays(@PathVariable UUID episodeId) {
        return proxyGet(() -> pctClient.emergencyObservationStays(episodeId), "PCT emergencyObservationStays");
    }

    @PostMapping("/observation-stays/{stayId}/end")
    public ResponseEntity<Map<String, Object>> endObservationStay(@PathVariable UUID stayId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.endEmergencyObservationStay(stayId, body),
                "PCT endEmergencyObservationStay", HttpStatus.OK);
    }

    // ── Identity link (W12) ────────────────────────────────────────────────────────────────────

    /** Append-only: a resolution links, it never overwrites, and both identities are retained forever. */
    @PostMapping("/{episodeId}/identity-link")
    public ResponseEntity<Map<String, Object>> linkIdentity(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> pctClient.linkEmergencyIdentity(episodeId, body), "PCT linkEmergencyIdentity", HttpStatus.CREATED);
    }

    @GetMapping("/{episodeId}/identity-link")
    public ResponseEntity<Map<String, Object>> identityLinks(@PathVariable UUID episodeId) {
        return proxyGet(() -> pctClient.emergencyIdentityLinks(episodeId), "PCT emergencyIdentityLinks");
    }

    /** Episode-by-state + alert-by-severity counts for one facility (W10 command view). */
    @GetMapping("/command-summary")
    public ResponseEntity<Map<String, Object>> commandSummary(@RequestParam UUID facilityId) {
        return proxyGet(() -> pctClient.emergencyCommandSummary(facilityId), "PCT emergencyCommandSummary");
    }

    /**
     * The command board in one call: state counts, the alert queue and the open episodes.
     *
     * Composed rather than left to three parallel client calls because the degradation rule differs
     * from the rest of this controller. Elsewhere an unreachable pct is a 502 and the caller retries.
     * Here a 502 would blank a board a charge nurse is standing in front of, on the strength of one
     * blind source. Instead each source is read independently and any that fails is named in
     * {@code failures} — the board still renders, and the tile that cannot be read says so rather
     * than showing a zero.
     */
    @GetMapping("/command-board")
    public ResponseEntity<Map<String, Object>> commandBoard(@RequestParam UUID facilityId) {
        EmergencyHonesty.Composite board = EmergencyHonesty.composite();

        try {
            board.put("summary", pctClient.emergencyCommandSummary(facilityId));
        } catch (Exception e) {
            board.failed("summary", "emergency_command_summary_unavailable", "the emergency command summary", e);
        }

        try {
            board.put("alerts", pctClient.emergencyAlertBoard(facilityId));
        } catch (Exception e) {
            board.failed("alerts", "emergency_alerts_unavailable", "emergency alerts", e);
        }

        try {
            board.put("episodes", pctClient.emergencyEpisodeBoard(facilityId));
        } catch (Exception e) {
            board.failed("episodes", "emergency_episodes_unavailable", "open emergency episodes", e);
        }

        return board.build();
    }

    /** MCI bulk-mint (W11): mint one emergency_episode per not-yet-minted casualty on an incident. */
    @PostMapping("/mci/{incidentId}/bulk-mint")
    public ResponseEntity<Map<String, Object>> mciBulkMint(@PathVariable UUID incidentId, @RequestParam UUID facilityId) {
        return proxyPost(() -> pctClient.mciBulkMint(incidentId, facilityId), "PCT mciBulkMint", HttpStatus.OK);
    }

    // ── Proxy plumbing ────────────────────────────────────────────────────────────────────────

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

    private ResponseEntity<Map<String, Object>> proxyGet(java.util.function.Supplier<JsonNode> call, String op) {
        try {
            JsonNode data = requirePayload(call.get(), op);
            return ResponseEntity.ok(Map.of("data", data));
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

    private ResponseEntity<Map<String, Object>> proxyPost(java.util.function.Supplier<JsonNode> call, String op, HttpStatus successStatus) {
        try {
            JsonNode data = requirePayload(call.get(), op);
            return ResponseEntity.status(successStatus).body(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // A 4xx from pct is a real client-facing validation error (an invalid FSM transition,
            // a handover already resolved, a disposition that contradicts the episode's state) —
            // surfaced as-is, matching EdWorkflowController's own established rule. Only a genuine
            // upstream outage (5xx / transport failure) collapses to 502.
            if (e.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString(), e);
            }
            throw upstreamFailure(op, e);
        } catch (Exception e) {
            throw upstreamFailure(op, e);
        }
    }
}
