package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NdilaServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/ndila")
public class NdilaController {

    private static final Logger log = LoggerFactory.getLogger(NdilaController.class);
    private final NdilaServiceClient client;

    public NdilaController(NdilaServiceClient client) {
        this.client = client;
    }

    @GetMapping("/tiles/config")
    public ResponseEntity<Map<String, Object>> tileConfig(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.tileConfig();
            if (data == null || data.isNull()) {
                return unavailable("NDILA_EMPTY_CONFIG", "Ndila returned no tile configuration", requestId, correlationId);
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila tile config failed: {}", e.getMessage());
            return unavailable("NDILA_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/tracking/nearby")
    public ResponseEntity<Map<String, Object>> nearbyAssets(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.nearbyAssets(body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila nearby assets failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", List.of(),
                    "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    private static ResponseEntity<Map<String, Object>> unavailable(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "data", Map.of(),
                "error", Map.of("code", code, "message", message),
                "meta", meta(requestId, correlationId)));
    }

    private static Map<String, String> meta(String requestId, String correlationId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("request_id", requestId);
        m.put("correlation_id", correlationId);
        return m;
    }
}
