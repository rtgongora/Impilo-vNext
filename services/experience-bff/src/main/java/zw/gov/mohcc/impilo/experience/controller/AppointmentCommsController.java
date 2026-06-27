package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit/history surface for appointment comms — mirrors telemedicine workflow history pattern.
 *
 * <p>The experience BFF holds no datasource of its own; durable comms history lives in the
 * TSHEPO audit sovereign. This endpoint queries the lifecycle and notification events back and
 * maps them to the response envelope previously served from the in-memory projection.</p>
 */
@RestController
@RequestMapping("/internal/v1/appointment-comms")
public class AppointmentCommsController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentCommsController.class);
    private static final String EVENT_LIFECYCLE = "APPOINTMENT_COMMS_LIFECYCLE";
    private static final String EVENT_NOTIFICATION = "APPOINTMENT_COMMS_NOTIFICATION";

    private final TshepoAuditServiceClient auditClient;

    public AppointmentCommsController(TshepoAuditServiceClient auditClient) {
        this.auditClient = auditClient;
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        String source = "TSHEPO_AUDIT";
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            rows.addAll(mapEvents(auditClient.queryEvents(null, null, EVENT_LIFECYCLE, null, null, 0, capped)));
            rows.addAll(mapEvents(auditClient.queryEvents(null, null, EVENT_NOTIFICATION, null, null, 0, capped)));
            if (rows.size() > capped) {
                rows = new ArrayList<>(rows.subList(0, capped));
            }
        } catch (Exception e) {
            log.debug("Appointment comms history query failed: {}", e.getMessage());
            source = "UNAVAILABLE";
            rows = List.of();
        }
        return ResponseEntity.ok(Map.of(
                "data", rows,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                        "count", rows.size(), "source", source)));
    }

    private List<Map<String, Object>> mapEvents(JsonNode data) {
        if (data == null || data.isNull()) {
            return List.of();
        }
        JsonNode items = data.has("items") ? data.path("items") : data;
        if (!items.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode ev : items) {
            JsonNode detail = ev.path("detail");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ev.path("id").asText(ev.path("eventId").asText("")));
            row.put("kind", detail.path("kind").asText(""));
            row.put("eventType", ev.path("action").asText(""));
            row.put("status", detail.path("status").asText(ev.path("outcome").asText("")));
            row.put("channel", detail.path("channel").isMissingNode() ? null : detail.path("channel").asText());
            row.put("templateKey", detail.path("template_key").isMissingNode() ? null : detail.path("template_key").asText());
            row.put("payload", detail);
            row.put("occurredAt", ev.path("createdAt").asText(ev.path("occurredAt").asText("")));
            out.add(row);
        }
        return out;
    }
}
