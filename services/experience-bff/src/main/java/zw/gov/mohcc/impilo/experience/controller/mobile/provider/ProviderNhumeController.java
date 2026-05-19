package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider mobile-app surface for Nhume.
 *
 * <p>Mobile {@code provider/services/nhumeService.ts} calls into this
 * controller. It proxies to nhume-service over the trust-aware RestTemplate
 * so courier actions (accept, decline, pickup, start, location, proof,
 * custody, fail) all carry the v1.1 trust headers downstream.</p>
 *
 * <p>If nhume-service is unreachable, GET returns an empty list and POST
 * returns a 502 envelope. The mobile UI never silently fails — see
 * {@link zw.gov.mohcc.impilo.experience.controller.mobile.citizen.CitizenNhumeController}
 * for the parallel citizen surface.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/nhume")
public class ProviderNhumeController {

    private static final Logger log = LoggerFactory.getLogger(ProviderNhumeController.class);

    private final RestTemplate restTemplate;
    private final String nhumeBaseUrl;

    public ProviderNhumeController(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.nhume-base-url:http://localhost:8340}") String nhumeBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.nhumeBaseUrl = nhumeBaseUrl;
    }

    @GetMapping("/assigned")
    public ResponseEntity<Map<String, Object>> assigned(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        try {
            // Nhume does not expose an actor-scoped "assigned" view today; fall back to listing
            // deliveries in active courier-facing states. Future: replace with a dedicated
            // /internal/v1/nhume/couriers/{id}/assignments endpoint and filter by `actorId`.
            ResponseEntity<JsonNode> resp = restTemplate.getForEntity(
                    nhumeBaseUrl + "/internal/v1/nhume/deliveries?status=ASSIGNED",
                    JsonNode.class);
            JsonNode body = resp.getBody();
            JsonNode items = body != null && body.has("items") ? body.get("items") : null;
            return ResponseEntity.ok(envelope(
                    items != null ? items : Collections.emptyList(),
                    requestId,
                    correlationId));
        } catch (Exception e) {
            log.warn("provider nhume assigned failed: {}", e.getMessage());
            return ResponseEntity.ok(envelope(Collections.emptyList(), requestId, correlationId));
        }
    }

    @PostMapping("/deliveries/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardAction(id, "accept", null, requestId, correlationId);
    }

    @PostMapping("/deliveries/{id}/decline")
    public ResponseEntity<Map<String, Object>> decline(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardAction(id, "decline", body, requestId, correlationId);
    }

    @PostMapping("/deliveries/{id}/pickup")
    public ResponseEntity<Map<String, Object>> pickup(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardAction(id, "pickup", body, requestId, correlationId);
    }

    @PostMapping("/deliveries/{id}/start")
    public ResponseEntity<Map<String, Object>> start(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardAction(id, "start", body, requestId, correlationId);
    }

    @PostMapping("/deliveries/{id}/location")
    public ResponseEntity<Map<String, Object>> location(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardAction(id, "location", body, requestId, correlationId);
    }

    @PostMapping("/deliveries/{id}/proof")
    public ResponseEntity<Map<String, Object>> proof(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardAction(id, "proof", body, requestId, correlationId);
    }

    @PostMapping("/deliveries/{id}/custody")
    public ResponseEntity<Map<String, Object>> custody(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardAction(id, "custody", body, requestId, correlationId);
    }

    @PostMapping("/deliveries/{id}/fail")
    public ResponseEntity<Map<String, Object>> fail(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardAction(id, "fail", body, requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> forwardAction(
            String deliveryId,
            String action,
            Map<String, Object> body,
            String requestId,
            String correlationId) {
        try {
            Map<String, Object> payload = body != null ? body : Map.of();
            ResponseEntity<JsonNode> resp = restTemplate.postForEntity(
                    nhumeBaseUrl + "/internal/v1/nhume/deliveries/" + deliveryId + "/" + action,
                    payload,
                    JsonNode.class);
            return ResponseEntity.ok(envelope(resp.getBody(), requestId, correlationId));
        } catch (Exception e) {
            log.warn("provider nhume {} failed for {}: {}", action, deliveryId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    error("NHUME_UNAVAILABLE",
                            "Delivery service is temporarily unavailable.",
                            requestId,
                            correlationId));
        }
    }

    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", data != null ? data : Collections.emptyList());
        envelope.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return envelope;
    }

    private static Map<String, Object> error(String code, String message, String requestId, String correlationId) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", Map.of("code", code, "message", message));
        err.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return err;
    }
}
