package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

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
