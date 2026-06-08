package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NhumeServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical BFF proxy for nhume-service — unifies dispatch/delivery console clients
 * onto {@code /internal/v1/nhume/*} (see {@code ui/one-ui-shell/src/lib/nhume.ts}).
 */
@RestController
@RequestMapping("/internal/v1/nhume")
public class NhumeController {

    private static final Logger log = LoggerFactory.getLogger(NhumeController.class);

    private final NhumeServiceClient client;

    public NhumeController(NhumeServiceClient client) {
        this.client = client;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.getDashboard(), requestId, correlationId);
    }

    @GetMapping("/dispatcher-console")
    public ResponseEntity<Map<String, Object>> dispatcherConsole(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.getDispatcherConsole(), requestId, correlationId);
    }

    @GetMapping("/zones")
    public ResponseEntity<Map<String, Object>> zones(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.getZones(), requestId, correlationId);
    }

    @GetMapping("/map-token")
    public ResponseEntity<Map<String, Object>> mapToken(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.getMapToken(), requestId, correlationId);
    }

    @GetMapping("/deliveries")
    public ResponseEntity<Map<String, Object>> listDeliveries(
            @RequestParam(required = false) String status,
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode body = client.listDeliveries(status, facilityId, page, size);
            return okList(body, requestId, correlationId);
        } catch (Exception e) {
            return nhumeFailure(e, requestId, correlationId);
        }
    }

    @PostMapping("/deliveries")
    public ResponseEntity<Map<String, Object>> createDelivery(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.createDelivery(body), requestId, correlationId, HttpStatus.CREATED);
    }

    @GetMapping("/deliveries/{id}")
    public ResponseEntity<Map<String, Object>> getDelivery(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.getDelivery(id), requestId, correlationId);
    }

    @GetMapping("/deliveries/{id}/{sub}")
    public ResponseEntity<Map<String, Object>> getDeliverySubresource(
            @PathVariable String id,
            @PathVariable String sub,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.getDeliverySubresource(id, sub), requestId, correlationId);
    }

    @PostMapping("/deliveries/{id}/{action}")
    public ResponseEntity<Map<String, Object>> postDeliveryAction(
            @PathVariable String id,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.postDeliveryAction(id, action, body), requestId, correlationId, HttpStatus.OK);
    }

    @GetMapping("/fleet")
    public ResponseEntity<Map<String, Object>> listFleet(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.listFleet(), requestId, correlationId);
    }

    @GetMapping("/fleet/{id}")
    public ResponseEntity<Map<String, Object>> getFleetAsset(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.getFleetAsset(id), requestId, correlationId);
    }

    @PostMapping("/fleet")
    public ResponseEntity<Map<String, Object>> createFleetAsset(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.createFleetAsset(body), requestId, correlationId, HttpStatus.CREATED);
    }

    @PatchMapping("/fleet/{id}")
    public ResponseEntity<Map<String, Object>> patchFleetAsset(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.patchFleetAsset(id, body), requestId, correlationId, HttpStatus.OK);
    }

    @GetMapping("/couriers")
    public ResponseEntity<Map<String, Object>> listCouriers(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.listCouriers(), requestId, correlationId);
    }

    @GetMapping("/couriers/{id}")
    public ResponseEntity<Map<String, Object>> getCourier(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.getCourier(id), requestId, correlationId);
    }

    @PostMapping("/couriers")
    public ResponseEntity<Map<String, Object>> createCourier(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.createCourier(body), requestId, correlationId, HttpStatus.CREATED);
    }

    @PatchMapping("/couriers/{id}")
    public ResponseEntity<Map<String, Object>> patchCourier(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.patchCourier(id, body), requestId, correlationId, HttpStatus.OK);
    }

    @GetMapping("/policies")
    public ResponseEntity<Map<String, Object>> listPolicies(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.listPolicies(), requestId, correlationId);
    }

    @PostMapping("/policies")
    public ResponseEntity<Map<String, Object>> createPolicy(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.createPolicy(body), requestId, correlationId, HttpStatus.CREATED);
    }

    @PatchMapping("/policies/{code}")
    public ResponseEntity<Map<String, Object>> patchPolicy(
            @PathVariable String code,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.patchPolicy(code, body), requestId, correlationId, HttpStatus.OK);
    }

    @GetMapping("/integrations")
    public ResponseEntity<Map<String, Object>> listIntegrations(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.listIntegrations(), requestId, correlationId);
    }

    @PostMapping("/integrations")
    public ResponseEntity<Map<String, Object>> createIntegration(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.createIntegration(body), requestId, correlationId, HttpStatus.CREATED);
    }

    @GetMapping("/autonomous-missions")
    public ResponseEntity<Map<String, Object>> listMissions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.listMissions(), requestId, correlationId);
    }

    @GetMapping("/autonomous-missions/{id}")
    public ResponseEntity<Map<String, Object>> getMission(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyRead(() -> client.getMission(id), requestId, correlationId);
    }

    @PostMapping("/autonomous-missions")
    public ResponseEntity<Map<String, Object>> createMission(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.createMission(body), requestId, correlationId, HttpStatus.CREATED);
    }

    @PostMapping("/autonomous-missions/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelMission(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyWrite(() -> client.cancelMission(id, body), requestId, correlationId, HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> proxyRead(
            java.util.function.Supplier<JsonNode> supplier,
            String requestId,
            String correlationId) {
        try {
            JsonNode body = supplier.get();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", body != null ? body : Map.of());
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return nhumeFailure(e, requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> proxyWrite(
            java.util.function.Supplier<JsonNode> supplier,
            String requestId,
            String correlationId,
            HttpStatus status) {
        try {
            JsonNode body = supplier.get();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", body != null ? body : Map.of());
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            return nhumeFailure(e, requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> okList(JsonNode body, String requestId, String correlationId) {
        Object data = extractItems(body);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "total_elements", body != null && body.has("total_elements") ? body.get("total_elements").asInt() : 0));
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> nhumeFailure(Exception e, String requestId, String correlationId) {
        log.warn("Nhume proxy failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "data", List.of(),
                "error", Map.of("code", "NHUME_UNAVAILABLE", "message", e.getMessage()),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static Object extractItems(JsonNode body) {
        if (body == null) {
            return List.of();
        }
        if (body.has("items") && body.get("items").isArray()) {
            return body.get("items");
        }
        if (body.has("data")) {
            return body.get("data");
        }
        return body.isArray() ? body : List.of();
    }
}
