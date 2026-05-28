package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NhumeServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/nhume")
public class NhumeController {

    private static final Logger log = LoggerFactory.getLogger(NhumeController.class);

    private final NhumeServiceClient client;

    public NhumeController(NhumeServiceClient client) {
        this.client = client;
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
            Object data = extractItems(body);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            response.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId,
                    "total_elements", body != null && body.has("total_elements") ? body.get("total_elements").asInt() : 0));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Nhume deliveries list failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", List.of(),
                    "error", Map.of("code", "NHUME_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
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
