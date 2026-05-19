package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DispatchServiceClient;

import java.util.Map;

/**
 * BFF proxy for the dispatch sovereign service.
 * Manages task dispatch, assignment, and completion.
 */
@RestController
@RequestMapping("/internal/v1/dispatch")
public class DispatchController {

    private static final Logger log = LoggerFactory.getLogger(DispatchController.class);

    private final DispatchServiceClient client;

    public DispatchController(DispatchServiceClient client) {
        this.client = client;
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> listTasks(
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.listTasks(assigneeId, status);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Dispatch task list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> getTask(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.getTask(id);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Dispatch task get failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.createTask(body);
            return ResponseEntity.status(201).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Dispatch task creation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "CREATE_FAILED", "message", e.getMessage())));
        }
    }

    @PatchMapping("/tasks/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignTask(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.assignTask(id, body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Dispatch task assign failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "ASSIGN_FAILED", "message", e.getMessage())));
        }
    }

    @PatchMapping("/tasks/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeTask(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = client.completeTask(id, body != null ? body : Map.of());
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Dispatch task complete failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "COMPLETE_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/deliveries")
    public ResponseEntity<Map<String, Object>> listDeliveries(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = client.listDeliveries();
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/deliveries")
    public ResponseEntity<Map<String, Object>> createDelivery(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        JsonNode data = client.createDelivery(body);
        return ResponseEntity.status(201).body(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/deliveries/{id}")
    public ResponseEntity<Map<String, Object>> getDelivery(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = client.getDelivery(id);
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PatchMapping("/deliveries/{id}")
    public ResponseEntity<Map<String, Object>> patchDelivery(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        JsonNode data = client.patchDelivery(id, body);
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/deliveries/{id}/{action}")
    public ResponseEntity<Map<String, Object>> deliveryAction(
            @PathVariable String id,
            @PathVariable String action,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode data = client.deliveryAction(id, action, body != null ? body : Map.of());
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/deliveries/{id}/tracking")
    public ResponseEntity<Map<String, Object>> tracking(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = client.getDeliveryTracking(id);
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = client.getDashboard();
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/dispatcher-console")
    public ResponseEntity<Map<String, Object>> dispatcherConsole(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = client.getDispatcherConsole();
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/fleet")
    public ResponseEntity<Map<String, Object>> listFleet(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", client.listFleet(), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @PostMapping("/fleet")
    public ResponseEntity<Map<String, Object>> createFleet(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(Map.of("data", client.createFleet(body), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @GetMapping("/fleet/{id}")
    public ResponseEntity<Map<String, Object>> getFleet(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", client.getFleet(id), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @PatchMapping("/fleet/{id}")
    public ResponseEntity<Map<String, Object>> patchFleet(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", client.patchFleet(id, body), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/couriers")
    public ResponseEntity<Map<String, Object>> listCouriers(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", client.listCouriers(), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @PostMapping("/couriers")
    public ResponseEntity<Map<String, Object>> createCourier(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(Map.of("data", client.createCourier(body), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @GetMapping("/couriers/{id}")
    public ResponseEntity<Map<String, Object>> getCourier(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", client.getCourier(id), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @PatchMapping("/couriers/{id}")
    public ResponseEntity<Map<String, Object>> patchCourier(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", client.patchCourier(id, body), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/zones")
    public ResponseEntity<Map<String, Object>> listZones(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", client.listZones(), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @PostMapping("/zones")
    public ResponseEntity<Map<String, Object>> createZone(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(Map.of("data", client.createZone(body), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @GetMapping("/policies")
    public ResponseEntity<Map<String, Object>> listPolicies(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", client.listPolicies(), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @PostMapping("/policies")
    public ResponseEntity<Map<String, Object>> createPolicy(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(Map.of("data", client.createPolicy(body), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @GetMapping("/integrations")
    public ResponseEntity<Map<String, Object>> listIntegrations(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", client.listIntegrations(), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @PostMapping("/integrations")
    public ResponseEntity<Map<String, Object>> createIntegration(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(Map.of("data", client.createIntegration(body), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @GetMapping("/autonomous-missions")
    public ResponseEntity<Map<String, Object>> listMissions(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", client.listMissions(), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @PostMapping("/autonomous-missions")
    public ResponseEntity<Map<String, Object>> createMission(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(Map.of("data", client.createMission(body), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
    @PostMapping("/webhooks/{providerCode}")
    public ResponseEntity<Map<String, Object>> webhook(@PathVariable String providerCode, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", client.webhook(providerCode, body), "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
