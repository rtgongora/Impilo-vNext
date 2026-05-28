package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical Experience BFF surface for the community social timeline.
 * Proxies {@code community-service} {@code /internal/v1/community/social/**}.
 */
@RestController
@RequestMapping("/internal/v1/social")
public class SocialController {

    private static final Logger log = LoggerFactory.getLogger(SocialController.class);

    private final CommunityServiceClient client;

    public SocialController(CommunityServiceClient client) {
        this.client = client;
    }

    @GetMapping("/feed")
    public ResponseEntity<Map<String, Object>> feed(
            @RequestParam(defaultValue = "home") String scope,
            @RequestParam(required = false) String scopeId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getFeed(scope, scopeId, limit, offset), requestId, correlationId);
    }

    @PostMapping("/posts")
    public ResponseEntity<Map<String, Object>> createPost(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyCreated(() -> client.createSocialPost(body), requestId, correlationId);
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<Map<String, Object>> getPost(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getSocialPost(id), requestId, correlationId);
    }

    @PostMapping("/posts/{id}/reactions")
    public ResponseEntity<Map<String, Object>> react(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.reactToPost(id, body), requestId, correlationId);
    }

    @DeleteMapping("/posts/{id}/reactions")
    public ResponseEntity<Map<String, Object>> unreact(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.unreactPost(id), requestId, correlationId);
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<Map<String, Object>> listComments(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listPostComments(id), requestId, correlationId);
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyCreated(() -> client.addComment(id, body), requestId, correlationId);
    }

    @PostMapping("/posts/{id}/bookmark")
    public ResponseEntity<Map<String, Object>> bookmark(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.bookmarkPost(id), requestId, correlationId);
    }

    @DeleteMapping("/posts/{id}/bookmark")
    public ResponseEntity<Map<String, Object>> unbookmark(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.unbookmarkPost(id), requestId, correlationId);
    }

    @PostMapping("/posts/{id}/report")
    public ResponseEntity<Map<String, Object>> report(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.reportPost(id, body), requestId, correlationId);
    }

    @PostMapping("/composer/assist")
    public ResponseEntity<Map<String, Object>> composerAssist(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of(
                "data", Map.of("text", body.getOrDefault("content", "")),
                "meta", meta(requestId, correlationId)));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> suggestions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.socialSuggestions(), requestId, correlationId);
    }

    @GetMapping("/communities")
    public ResponseEntity<Map<String, Object>> listCommunities(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listSocialCommunities(category, q), requestId, correlationId);
    }

    @GetMapping("/communities/{id}")
    public ResponseEntity<Map<String, Object>> getCommunity(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getSocialCommunity(id), requestId, correlationId);
    }

    @GetMapping("/groups")
    public ResponseEntity<Map<String, Object>> listGroups(
            @RequestParam(required = false) String communityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listSocialGroups(communityId), requestId, correlationId);
    }

    @GetMapping("/groups/{id}")
    public ResponseEntity<Map<String, Object>> getGroup(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getSocialGroup(id), requestId, correlationId);
    }

    @PostMapping("/communities/{id}/join")
    public ResponseEntity<Map<String, Object>> joinCommunity(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.joinSocialCommunity(id), requestId, correlationId);
    }

    @PostMapping("/communities/{id}/leave")
    public ResponseEntity<Map<String, Object>> leaveCommunity(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.leaveSocialCommunity(id), requestId, correlationId);
    }

    @GetMapping("/pages/{id}")
    public ResponseEntity<Map<String, Object>> getPage(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getSocialPage(id), requestId, correlationId);
    }

    @PostMapping("/pages/{id}/follow")
    public ResponseEntity<Map<String, Object>> followPage(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.followSocialPage(id), requestId, correlationId);
    }

    @GetMapping("/pages")
    public ResponseEntity<Map<String, Object>> listPages(
            @RequestParam(required = false) String kind,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listSocialPages(kind), requestId, correlationId);
    }

    @GetMapping("/moderation/queue")
    public ResponseEntity<Map<String, Object>> moderationQueue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.moderationQueue(), requestId, correlationId);
    }

    @PostMapping("/moderation/{caseId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveModeration(
            @PathVariable String caseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.resolveModeration(caseId, body), requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> proxy(
            java.util.function.Supplier<JsonNode> call,
            String requestId,
            String correlationId) {
        try {
            JsonNode data = call.get();
            Object payload = data;
            if (data != null && data.has("items")) {
                payload = data.get("items");
            } else if (data != null && data.has("data")) {
                payload = data.get("data");
            }
            return ResponseEntity.ok(Map.of(
                    "data", payload != null ? payload : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Social proxy failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of(
                            "request_id", requestId,
                            "correlation_id", correlationId,
                            "degraded", true)));
        }
    }

    private ResponseEntity<Map<String, Object>> proxyCreated(
            java.util.function.Supplier<JsonNode> call,
            String requestId,
            String correlationId) {
        try {
            JsonNode data = call.get();
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Social create failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "SOCIAL_UNAVAILABLE", "message", e.getMessage()),
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
