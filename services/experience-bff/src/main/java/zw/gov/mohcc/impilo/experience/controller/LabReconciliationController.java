package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.util.*;

/**
 * Lab reconciliation endpoints backed by OROS hybrid-mode reconcile queue.
 *
 * <p>GET  /internal/v1/lab-reconciliation — list pending reconciliation entries.</p>
 * <p>POST /internal/v1/lab-reconciliation/{id}/match — match entry to an order.</p>
 * <p>POST /internal/v1/lab-reconciliation/{id}/resolve — resolve entry with notes.</p>
 */
@RestController
@RequestMapping("/internal/v1/lab-reconciliation")
public class LabReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(LabReconciliationController.class);

    private final OrosServiceClient orosClient;

    public LabReconciliationController(OrosServiceClient orosClient) {
        this.orosClient = orosClient;
    }

    public record MatchLabReconciliationRequest(@NotBlank String order_id) {}

    public record ResolveLabReconciliationRequest(String notes) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPendingReconciliations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode upstream = orosClient.getReconcilePending(page, size);
            Map<String, Object> payload = normalizeReconciliationPayload(upstream, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", payload,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("OROS lab reconciliation fetch failed: {}", e.getMessage());
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/match")
    public ResponseEntity<Map<String, Object>> matchReconciliation(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody MatchLabReconciliationRequest request) {
        try {
            JsonNode data = orosClient.matchReconciliation(id, request.order_id());
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of("recId", id, "status", "MATCHED"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("OROS reconciliation match failed for {}: {}", id, e.getMessage());
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveReconciliation(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) ResolveLabReconciliationRequest request) {
        String notes = request != null ? request.notes() : null;
        try {
            JsonNode data = orosClient.resolveReconciliation(id, notes);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of("recId", id, "status", "RESOLVED"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("OROS reconciliation resolve failed for {}: {}", id, e.getMessage());
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    static Map<String, Object> normalizeReconciliationPayload(JsonNode upstream, int page, int size) {
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

        Map<String, Object> summary = summarize(items);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("page", resolvedPage);
        payload.put("size", resolvedSize);
        payload.put("total_elements", totalElements);
        payload.put("summary", summary);
        return payload;
    }

    private static Map<String, Object> summarize(List<Map<String, Object>> items) {
        int pending = 0;
        int matched = 0;
        int resolved = 0;

        for (Map<String, Object> item : items) {
            String status = text(item.get("status")).toUpperCase(Locale.ROOT);
            switch (status) {
                case "MATCHED" -> matched++;
                case "RESOLVED" -> resolved++;
                default -> pending++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pending", pending);
        summary.put("matched", matched);
        summary.put("resolved", resolved);
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
