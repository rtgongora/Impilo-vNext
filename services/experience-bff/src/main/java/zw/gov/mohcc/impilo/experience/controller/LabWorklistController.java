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
 * Facility lab worklist endpoints backed by OROS.
 *
 * <p>GET  /internal/v1/lab-worklists — list worklist items for a facility.</p>
 * <p>POST /internal/v1/lab-worklists/{id}/accept — accept a pending order.</p>
 * <p>POST /internal/v1/lab-worklists/{id}/reject — reject a pending order.</p>
 */
@RestController
@RequestMapping("/internal/v1/lab-worklists")
public class LabWorklistController {

    private static final Logger log = LoggerFactory.getLogger(LabWorklistController.class);

    private final OrosServiceClient orosClient;

    public LabWorklistController(OrosServiceClient orosClient) {
        this.orosClient = orosClient;
    }

    public record RejectLabWorklistRequest(@NotBlank String reason) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLabWorklists(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "workspace_id") String workspaceId,
            @RequestParam(required = false, defaultValue = "LAB") String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode upstream = orosClient.getWorklists(
                    facilityId, workspaceId, type, status, priority, page, size);
            Map<String, Object> payload = normalizeWorklistPayload(upstream, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", payload,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("OROS lab worklist fetch failed: {}", e.getMessage());
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Map<String, Object>> acceptLabWorklistItem(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        try {
            JsonNode data = orosClient.acceptWorklistItem(id);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of("orderId", id, "status", "ACCEPTED"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("OROS worklist accept failed for {}: {}", id, e.getMessage());
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectLabWorklistItem(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) RejectLabWorklistRequest request) {
        String reason = request != null ? request.reason() : "Rejected from experience UI";
        try {
            JsonNode data = orosClient.rejectWorklistItem(id, reason);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of("orderId", id, "status", "REJECTED"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("OROS worklist reject failed for {}: {}", id, e.getMessage());
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    static Map<String, Object> normalizeWorklistPayload(JsonNode upstream, int page, int size) {
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
        int inProgress = 0;
        int completed = 0;
        int urgent = 0;

        for (Map<String, Object> item : items) {
            String status = text(item.get("status")).toUpperCase(Locale.ROOT);
            String priority = text(item.get("priority")).toUpperCase(Locale.ROOT);

            if (status.equals("PLACED") || status.equals("SCHEDULED")) {
                pending++;
            } else if (status.equals("ACCEPTED") || status.equals("IN_PROGRESS") || status.equals("PARTIAL_RESULT")) {
                inProgress++;
            } else if (status.equals("RESULT_AVAILABLE") || status.equals("REVIEWED")
                    || status.equals("RELEASED") || status.equals("COMPLETED")) {
                completed++;
            }

            if (priority.equals("STAT") || priority.equals("URGENT") || priority.equals("ASAP")) {
                urgent++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pending_collection", pending);
        summary.put("in_progress", inProgress);
        summary.put("completed", completed);
        summary.put("urgent", urgent);
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
