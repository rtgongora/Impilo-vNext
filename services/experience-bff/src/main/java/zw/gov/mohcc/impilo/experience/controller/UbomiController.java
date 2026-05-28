package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.UbomiServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Experience BFF bridge for UBOMI civil registry (birth / death notifications).
 */
@RestController
@RequestMapping("/internal/v1/ubomi")
public class UbomiController {

    private static final Logger log = LoggerFactory.getLogger(UbomiController.class);

    private final UbomiServiceClient client;

    public UbomiController(UbomiServiceClient client) {
        this.client = client;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            client.listBirthNotifications(0, 1);
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(
                            "available", true,
                            "message", "UBOMI civil registry is reachable through the Experience BFF.",
                            "module", "ubomi-service"),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("UBOMI status probe failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(
                            "available", false,
                            "message", "UBOMI service is unavailable: " + e.getMessage(),
                            "module", "ubomi-service"),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @GetMapping("/births")
    public ResponseEntity<Map<String, Object>> listBirths(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listBirthNotifications(page, size), requestId, correlationId, false);
    }

    @PostMapping("/births")
    public ResponseEntity<Map<String, Object>> submitBirth(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(
                () -> client.submitBirthNotification(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body)),
                requestId,
                correlationId,
                true);
    }

    @PostMapping("/births/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveBirth(
            @PathVariable long id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.approveBirthNotification(id), requestId, correlationId, false);
    }

    @GetMapping("/deaths")
    public ResponseEntity<Map<String, Object>> listDeaths(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listDeathNotifications(page, size), requestId, correlationId, false);
    }

    @PostMapping("/deaths")
    public ResponseEntity<Map<String, Object>> submitDeath(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(
                () -> client.submitDeathNotification(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body)),
                requestId,
                correlationId,
                true);
    }

    @PostMapping("/deaths/{id}/certify")
    public ResponseEntity<Map<String, Object>> certifyDeath(
            @PathVariable long id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.certifyDeathNotification(id), requestId, correlationId, false);
    }

    @GetMapping("/verifications/{registrationNumber}")
    public ResponseEntity<Map<String, Object>> verify(
            @PathVariable String registrationNumber,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.verifyRegistration(registrationNumber), requestId, correlationId, false);
    }

    private ResponseEntity<Map<String, Object>> proxy(
            java.util.function.Supplier<JsonNode> call,
            String requestId,
            String correlationId,
            boolean created) {
        try {
            JsonNode data = call.get();
            if (created) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "data", data != null ? data : Map.of(),
                        "meta", meta(requestId, correlationId)));
            }
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("UBOMI operation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "UBOMI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    private static Map<String, String> meta(String requestId, String correlationId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("request_id", requestId);
        m.put("correlation_id", correlationId);
        return m;
    }
}
