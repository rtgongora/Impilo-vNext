package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;

import java.util.Map;
import java.util.UUID;

/**
 * BFF surface for the social timeline. Thin pass-through to community-service
 * (`/internal/v1/community/social/**`) under the cleaner public path
 * {@code /internal/v1/social/**} consumed by web/mobile shells.
 *
 * Mobile-specific feed-scope wrappers live in
 * {@link zw.gov.mohcc.impilo.experience.controller.mobile.citizen.CitizenSocialController}
 * and {@link zw.gov.mohcc.impilo.experience.controller.mobile.provider.ProviderSocialController}.
 */
@RestController
@RequestMapping("/internal/v1/social")
public class SocialController {

    private static final Logger log = LoggerFactory.getLogger(SocialController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CommunityServiceClient client;

    public SocialController(CommunityServiceClient client) {
        this.client = client;
    }

    // ── Feed ───────────────────────────────────────────────────────────────
    @GetMapping("/feed")
    public ResponseEntity<JsonNode> feed(
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestParam(defaultValue = "home") String scope,
            @RequestParam(required = false) String scopeId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            return ResponseEntity.ok(client.getFeed(scope, scopeId, limit, offset));
        } catch (Exception e) {
            log.warn("social feed unavailable scope={} err={}", scope, e.getMessage());
            return ResponseEntity.ok(emptyFeed(scope, requestId));
        }
    }

    // ── Posts ──────────────────────────────────────────────────────────────
    @PostMapping("/posts")
    public ResponseEntity<JsonNode> createPost(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(client.createSocialPost(body));
        } catch (Exception e) {
            log.error("social createPost failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
        }
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<JsonNode> getPost(@PathVariable UUID id) {
        return ResponseEntity.ok(client.getSocialPost(id.toString()));
    }

    @PostMapping("/posts/{id}/reactions")
    public ResponseEntity<JsonNode> react(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(client.reactToPost(id.toString(), body));
    }

    @DeleteMapping("/posts/{id}/reactions")
    public ResponseEntity<JsonNode> unreact(@PathVariable UUID id) {
        return ResponseEntity.ok(client.unreactPost(id.toString()));
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<JsonNode> listComments(@PathVariable UUID id) {
        return ResponseEntity.ok(client.listPostComments(id.toString()));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<JsonNode> addComment(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.addComment(id.toString(), body));
    }

    @PostMapping("/posts/{id}/bookmark")
    public ResponseEntity<JsonNode> bookmark(@PathVariable UUID id) {
        return ResponseEntity.ok(client.bookmarkPost(id.toString()));
    }

    @DeleteMapping("/posts/{id}/bookmark")
    public ResponseEntity<JsonNode> unbookmark(@PathVariable UUID id) {
        return ResponseEntity.ok(client.unbookmarkPost(id.toString()));
    }

    @PostMapping("/posts/{id}/report")
    public ResponseEntity<JsonNode> report(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.reportPost(id.toString(), body));
    }

    // ── Communities ───────────────────────────────────────────────────────
    @GetMapping("/communities")
    public ResponseEntity<JsonNode> listCommunities(@RequestParam(required = false) String category,
                                                    @RequestParam(required = false) String q) {
        try {
            return ResponseEntity.ok(client.listSocialCommunities(category, q));
        } catch (Exception e) {
            log.warn("social communities unavailable: {}", e.getMessage());
            return ResponseEntity.ok(emptyListBody());
        }
    }

    @GetMapping("/communities/{id}")
    public ResponseEntity<JsonNode> getCommunity(@PathVariable UUID id) {
        return ResponseEntity.ok(client.getSocialCommunity(id.toString()));
    }

    @PostMapping("/communities")
    public ResponseEntity<JsonNode> createCommunity(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createSocialCommunity(body));
    }

    @PostMapping("/communities/{id}/join")
    public ResponseEntity<JsonNode> joinCommunity(@PathVariable UUID id) {
        return ResponseEntity.ok(client.joinSocialCommunity(id.toString()));
    }

    @PostMapping("/communities/{id}/leave")
    public ResponseEntity<JsonNode> leaveCommunity(@PathVariable UUID id) {
        return ResponseEntity.ok(client.leaveSocialCommunity(id.toString()));
    }

    // ── Groups ────────────────────────────────────────────────────────────
    @GetMapping("/groups")
    public ResponseEntity<JsonNode> listGroups(@RequestParam(required = false) String communityId) {
        try {
            return ResponseEntity.ok(client.listSocialGroups(communityId));
        } catch (Exception e) {
            log.warn("social groups unavailable: {}", e.getMessage());
            return ResponseEntity.ok(emptyListBody());
        }
    }

    @GetMapping("/groups/{id}")
    public ResponseEntity<JsonNode> getGroup(@PathVariable UUID id) {
        return ResponseEntity.ok(client.getSocialGroup(id.toString()));
    }

    // ── Pages ─────────────────────────────────────────────────────────────
    @GetMapping("/pages")
    public ResponseEntity<JsonNode> listPages(@RequestParam(required = false) String kind) {
        try {
            return ResponseEntity.ok(client.listSocialPages(kind));
        } catch (Exception e) {
            log.warn("social pages unavailable: {}", e.getMessage());
            return ResponseEntity.ok(emptyListBody());
        }
    }

    @GetMapping("/pages/{id}")
    public ResponseEntity<JsonNode> getPage(@PathVariable UUID id) {
        return ResponseEntity.ok(client.getSocialPage(id.toString()));
    }

    @PostMapping("/pages")
    public ResponseEntity<JsonNode> createPage(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createSocialPage(body));
    }

    @PostMapping("/pages/{id}/follow")
    public ResponseEntity<JsonNode> followPage(@PathVariable UUID id) {
        return ResponseEntity.ok(client.followSocialPage(id.toString()));
    }

    // ── Suggestions & Moderation ─────────────────────────────────────────
    @GetMapping("/suggestions")
    public ResponseEntity<JsonNode> suggestions() {
        try {
            return ResponseEntity.ok(client.socialSuggestions());
        } catch (Exception e) {
            log.warn("social suggestions unavailable: {}", e.getMessage());
            return ResponseEntity.ok(emptyBundle());
        }
    }

    @GetMapping("/moderation/queue")
    public ResponseEntity<JsonNode> moderationQueue() {
        try {
            return ResponseEntity.ok(client.moderationQueue());
        } catch (Exception e) {
            log.warn("social moderation queue unavailable: {}", e.getMessage());
            return ResponseEntity.ok(emptyListBody());
        }
    }

    @PostMapping("/moderation/{caseId}/resolve")
    public ResponseEntity<JsonNode> resolveModeration(@PathVariable UUID caseId,
                                                      @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(client.resolveModeration(caseId.toString(), body));
    }

    private JsonNode emptyFeed(String scope, String requestId) {
        return OBJECT_MAPPER.valueToTree(Map.of(
                "scope", scope, "items", java.util.List.of(),
                "nextCursor", null,
                "meta", Map.of("request_id", requestId == null ? "" : requestId, "degraded", true)
        ));
    }

    private JsonNode emptyListBody() {
        return OBJECT_MAPPER.valueToTree(java.util.List.of());
    }

    private JsonNode emptyBundle() {
        return OBJECT_MAPPER.valueToTree(Map.of(
                "communities", java.util.List.of(),
                "groups", java.util.List.of(),
                "pages", java.util.List.of()
        ));
    }
}
