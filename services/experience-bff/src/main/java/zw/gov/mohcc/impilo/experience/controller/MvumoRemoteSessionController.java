package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/mvumo/remote-sessions")
public class MvumoRemoteSessionController {

    private static final Logger log = LoggerFactory.getLogger(MvumoRemoteSessionController.class);
    private final MvumoServiceClient mvumoClient;

    public MvumoRemoteSessionController(MvumoServiceClient mvumoClient) {
        this.mvumoClient = mvumoClient;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = mvumoClient.createRemoteSession(body);
            return ok(data, requestId, correlationId, HttpStatus.CREATED);
        } catch (Exception e) {
            log.warn("MVUMO remote session create failed: {}", e.getMessage());
            return failure("MVUMO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(mvumoClient.getRemoteSession(id), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return failure("MVUMO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return mutate(id, body, mvumoClient::verifyRemoteSession, requestId, correlationId);
    }

    @PostMapping("/{id}/grant")
    public ResponseEntity<Map<String, Object>> grant(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return mutate(id, body, mvumoClient::grantRemoteSession, requestId, correlationId);
    }

    @PostMapping("/{id}/refuse")
    public ResponseEntity<Map<String, Object>> refuse(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return mutate(id, body, mvumoClient::refuseRemoteSession, requestId, correlationId);
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return mutate(id, body, mvumoClient::withdrawRemoteSession, requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> mutate(
            String id,
            Map<String, Object> body,
            BiMutator mutator,
            String requestId,
            String correlationId) {
        try {
            JsonNode data = mutator.apply(id, body != null ? body : Map.of());
            return ok(data, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            log.warn("MVUMO remote session action failed for {}: {}", id, e.getMessage());
            return failure("MVUMO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId, HttpStatus status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<Map<String, Object>> failure(String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : code),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @FunctionalInterface
    private interface BiMutator {
        JsonNode apply(String id, Map<String, Object> body);
    }
}
