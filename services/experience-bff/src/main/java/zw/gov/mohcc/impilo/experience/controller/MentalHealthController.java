package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.MentalHealthServiceClient;

import java.util.Map;

/**
 * BFF proxy for mental-health-service's referral acceptance surface (W13) — the accepting side of
 * a psychiatric emergency handover. Scoped to intake/get/list/accept/decline this wave; the fuller
 * clinical-record surface (assessment, safety plan, involuntary episode, restraint events,
 * admission requests, follow-up) is deferred to W15's experience layer.
 *
 * <p>Same 4xx-passthrough rule as {@code EmergencyEpisodeController} (pct proxy): a 4xx from
 * mental-health-service is a real client-facing validation error (e.g. a non-PENDING referral, a
 * missing decline reason) and is surfaced as-is; only a genuine upstream outage collapses to 502.
 */
@RestController
@RequestMapping("/internal/v1/mental-health")
public class MentalHealthController {

    private static final Logger log = LoggerFactory.getLogger(MentalHealthController.class);

    private final MentalHealthServiceClient client;

    public MentalHealthController(MentalHealthServiceClient client) {
        this.client = client;
    }

    @PostMapping("/referrals")
    public ResponseEntity<Map<String, Object>> intake(@RequestBody Map<String, Object> body) {
        return proxyPost(() -> client.intakeReferral(toJson(body)), "mental-health intake", HttpStatus.CREATED);
    }

    @GetMapping("/referrals/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        return proxyGet(() -> client.getReferral(id), "mental-health getReferral");
    }

    @GetMapping("/referrals")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status) {
        return proxyGet(() -> client.listReferrals(status), "mental-health listReferrals");
    }

    @PostMapping("/referrals/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> client.acceptReferral(id, toJson(body)), "mental-health acceptReferral", HttpStatus.OK);
    }

    @PostMapping("/referrals/{id}/decline")
    public ResponseEntity<Map<String, Object>> decline(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return proxyPost(() -> client.declineReferral(id, toJson(body)), "mental-health declineReferral", HttpStatus.OK);
    }

    private JsonNode toJson(Map<String, Object> body) {
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body != null ? body : Map.of());
    }

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
            if (e.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString(), e);
            }
            throw upstreamFailure(op, e);
        } catch (Exception e) {
            throw upstreamFailure(op, e);
        }
    }
}
