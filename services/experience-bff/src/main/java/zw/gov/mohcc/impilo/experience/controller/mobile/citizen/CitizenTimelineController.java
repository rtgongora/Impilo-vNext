package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.support.PctTimelineRows;

import java.util.*;

/**
 * Citizen health timeline endpoints.
 * GET /internal/v1/mobile/citizen/timeline
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/timeline")
public class CitizenTimelineController {

    private static final Logger log = LoggerFactory.getLogger(CitizenTimelineController.class);

    private final PctServiceClient pctClient;

    public CitizenTimelineController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            List<Map<String, Object>> rows = PctTimelineRows.timelineEntries(
                    pctClient.getPatientTimeline(actorId));
            if (eventType != null && !eventType.isBlank()) {
                rows = rows.stream().filter(row -> {
                    Object attributes = row.get("attributes");
                    return attributes instanceof Map<?, ?> attrs
                            && eventType.equalsIgnoreCase(String.valueOf(attrs.get("eventType")));
                }).toList();
            }

            int safePage = Math.max(0, page);
            int safeSize = Math.min(100, Math.max(1, size));
            int from = Math.min(rows.size(), safePage * safeSize);
            int to = Math.min(rows.size(), from + safeSize);
            int totalPages = rows.isEmpty() ? 0 : (rows.size() + safeSize - 1) / safeSize;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", rows.subList(from, to));
            response.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId,
                    "page", Map.of(
                            "number", safePage,
                            "size", safeSize,
                            "total_elements", rows.size(),
                            "total_pages", totalPages)));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Citizen timeline unavailable for actor={}: {}", actorId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "timeline_unavailable",
                    "message", "Your timeline could not be retrieved. This is not an empty health history.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
