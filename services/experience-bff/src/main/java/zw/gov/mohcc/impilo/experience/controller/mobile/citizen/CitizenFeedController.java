package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;

import java.util.*;

/**
 * Citizen-facing thin wrapper around the social timeline. Existing mobile
 * shells already call {@code /internal/v1/mobile/citizen/feed}; this now
 * delegates to {@code community-service /internal/v1/community/social/feed}
 * and mirrors the legacy {@code data + meta} envelope so the existing
 * {@code citizen-app/src/services/feedService.ts} continues to work.
 *
 * Like/unlike endpoints map onto post reactions (LIKE).
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/feed")
public class CitizenFeedController {

    private static final Logger log = LoggerFactory.getLogger(CitizenFeedController.class);

    private final CommunityServiceClient client;

    public CitizenFeedController(CommunityServiceClient client) {
        this.client = client;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            JsonNode feed = client.getFeed("home", null, size, page * size);
            return ResponseEntity.ok(Map.of(
                    "data", feed != null && feed.has("items") ? feed.get("items") : List.of(),
                    "meta", Map.of(
                            "request_id", requestId == null ? "" : requestId,
                            "correlation_id", correlationId == null ? "" : correlationId,
                            "page", Map.of(
                                    "number", page,
                                    "size", size,
                                    "total_elements", feed != null && feed.has("items") ? feed.get("items").size() : 0,
                                    "total_pages", feed != null && feed.has("nextCursor") && !feed.get("nextCursor").isNull() ? page + 2 : page + 1
                            )
                    )));
        } catch (Exception e) {
            log.warn("citizen feed degraded: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId == null ? "" : requestId,
                            "correlation_id", correlationId == null ? "" : correlationId,
                            "page", Map.of("number", page, "size", size, "total_elements", 0, "total_pages", 1),
                            "degraded", true)));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode post = client.getSocialPost(id.toString());
            return ResponseEntity.ok(Map.of(
                    "data", post == null ? Map.of() : post,
                    "meta", Map.of("request_id", requestId == null ? "" : requestId,
                            "correlation_id", correlationId == null ? "" : correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("data", Map.of(),
                    "meta", Map.of("request_id", requestId == null ? "" : requestId, "degraded", true)));
        }
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(
            @PathVariable UUID id,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId) {
        try {
            client.reactToPost(id.toString(), Map.of("type", "LIKE"));
        } catch (Exception e) {
            log.warn("citizen like failed id={} err={}", id, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlike(
            @PathVariable UUID id,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId) {
        try {
            client.unreactPost(id.toString());
        } catch (Exception e) {
            log.warn("citizen unlike failed id={} err={}", id, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
