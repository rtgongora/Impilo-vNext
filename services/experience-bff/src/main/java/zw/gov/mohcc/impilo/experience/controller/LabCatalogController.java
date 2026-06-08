package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.util.*;

/**
 * Facility lab catalog endpoints backed by OROS.
 *
 * <p>GET /internal/v1/lab-catalog — list orderable catalog items.</p>
 */
@RestController
@RequestMapping("/internal/v1/lab-catalog")
public class LabCatalogController {

    private static final Logger log = LoggerFactory.getLogger(LabCatalogController.class);

    private final OrosServiceClient orosClient;

    public LabCatalogController(OrosServiceClient orosClient) {
        this.orosClient = orosClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLabCatalog(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "order_type", defaultValue = "LAB") String orderType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode upstream = orosClient.getCatalog(orderType, page, size);
            Map<String, Object> payload = normalizeCatalogPayload(upstream, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", payload,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("OROS lab catalog fetch failed: {}", e.getMessage());
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    static Map<String, Object> normalizeCatalogPayload(JsonNode upstream, int page, int size) {
        List<Map<String, Object>> items = new ArrayList<>();
        int resolvedPage = page;
        int resolvedSize = size;
        long totalElements = 0;

        if (upstream != null) {
            if (upstream.isArray()) {
                upstream.forEach(node -> items.add(jsonNodeToMap(node)));
                totalElements = items.size();
            } else if (upstream.has("items") && upstream.get("items").isArray()) {
                upstream.get("items").forEach(node -> items.add(jsonNodeToMap(node)));
                resolvedPage = upstream.path("page").asInt(page);
                resolvedSize = upstream.path("size").asInt(size);
                totalElements = upstream.path("totalElements").asLong(items.size());
            }
        }

        Map<String, Object> summary = summarizeByCategory(items);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("page", resolvedPage);
        payload.put("size", resolvedSize);
        payload.put("total_elements", totalElements);
        payload.put("summary", summary);
        return payload;
    }

    private static Map<String, Object> summarizeByCategory(List<Map<String, Object>> items) {
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        int active = 0;

        for (Map<String, Object> item : items) {
            String category = text(item.get("category"));
            if (category.isBlank()) {
                category = "Uncategorized";
            }
            byCategory.merge(category, 1, Integer::sum);

            Object activeFlag = item.get("active");
            if (activeFlag == null || Boolean.TRUE.equals(activeFlag) || "true".equalsIgnoreCase(String.valueOf(activeFlag))) {
                active++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("by_category", byCategory);
        summary.put("active", active);
        summary.put("total", items.size());
        return summary;
    }

    private static Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                map.put(entry.getKey(), null);
            } else if (value.isTextual()) {
                map.put(entry.getKey(), value.asText());
            } else if (value.isNumber()) {
                map.put(entry.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                map.put(entry.getKey(), value.asBoolean());
            } else {
                map.put(entry.getKey(), value.toString());
            }
        });
        return map;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "OROS upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
