package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-shell social timeline surface. Same backing community-service
 * endpoints, but exposed at {@code /internal/v1/mobile/provider/social/**}
 * so the provider mobile app and any provider-aware web routes can request
 * a professional-flavoured slice (e.g. defaults to provider communities of
 * practice, training cohorts and facility-page updates).
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/social")
public class ProviderSocialController {

    private static final Logger log = LoggerFactory.getLogger(ProviderSocialController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CommunityServiceClient client;

    public ProviderSocialController(CommunityServiceClient client) {
        this.client = client;
    }

    @GetMapping("/feed")
    public ResponseEntity<JsonNode> feed(@RequestParam(defaultValue = "home") String scope,
                                         @RequestParam(required = false) String scopeId,
                                         @RequestParam(defaultValue = "20") int limit,
                                         @RequestParam(defaultValue = "0") int offset) {
        try {
            return ResponseEntity.ok(client.getFeed(scope, scopeId, limit, offset));
        } catch (Exception e) {
            log.warn("provider social feed scope={} degraded: {}", scope, e.getMessage());
            return ResponseEntity.ok(OBJECT_MAPPER
                    .valueToTree(Map.of("scope", scope, "items", List.of(), "nextCursor", null)));
        }
    }

    @PostMapping("/posts")
    public ResponseEntity<JsonNode> createPost(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createSocialPost(body));
    }

    @PostMapping("/posts/{id}/reactions")
    public ResponseEntity<JsonNode> react(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(client.reactToPost(id.toString(), body));
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<JsonNode> comments(@PathVariable UUID id) {
        return ResponseEntity.ok(client.listPostComments(id.toString()));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<JsonNode> addComment(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.addComment(id.toString(), body));
    }

    @GetMapping("/communities")
    public ResponseEntity<JsonNode> communities(@RequestParam(required = false) String category) {
        try {
            return ResponseEntity.ok(client.listSocialCommunities(category == null ? "professional" : category, null));
        } catch (Exception e) {
            return ResponseEntity.ok(OBJECT_MAPPER.valueToTree(List.of()));
        }
    }

    @PostMapping("/communities/{id}/join")
    public ResponseEntity<JsonNode> joinCommunity(@PathVariable UUID id) {
        return ResponseEntity.ok(client.joinSocialCommunity(id.toString()));
    }

    @GetMapping("/groups")
    public ResponseEntity<JsonNode> groups(@RequestParam(required = false) String communityId) {
        try {
            return ResponseEntity.ok(client.listSocialGroups(communityId));
        } catch (Exception e) {
            return ResponseEntity.ok(OBJECT_MAPPER.valueToTree(List.of()));
        }
    }

    @GetMapping("/pages")
    public ResponseEntity<JsonNode> pages(@RequestParam(required = false) String kind) {
        try {
            return ResponseEntity.ok(client.listSocialPages(kind));
        } catch (Exception e) {
            return ResponseEntity.ok(OBJECT_MAPPER.valueToTree(List.of()));
        }
    }

    @PostMapping("/pages/{id}/follow")
    public ResponseEntity<JsonNode> followPage(@PathVariable UUID id) {
        return ResponseEntity.ok(client.followSocialPage(id.toString()));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<JsonNode> suggestions() {
        try {
            return ResponseEntity.ok(client.socialSuggestions());
        } catch (Exception e) {
            return ResponseEntity.ok(OBJECT_MAPPER
                    .valueToTree(Map.of("communities", List.of(), "groups", List.of(), "pages", List.of())));
        }
    }

    @GetMapping("/moderation/queue")
    public ResponseEntity<JsonNode> moderationQueue() {
        try {
            return ResponseEntity.ok(client.moderationQueue());
        } catch (Exception e) {
            return ResponseEntity.ok(OBJECT_MAPPER.valueToTree(List.of()));
        }
    }
}
