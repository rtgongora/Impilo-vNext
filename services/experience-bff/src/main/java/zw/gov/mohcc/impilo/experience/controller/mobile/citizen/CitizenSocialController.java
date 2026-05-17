package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

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
 * Citizen-shell social timeline surface. Adds composer, communities, pages
 * and bookmarks on top of the legacy feed endpoint already wrapped by
 * {@link CitizenFeedController}.
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/social")
public class CitizenSocialController {

    private static final Logger log = LoggerFactory.getLogger(CitizenSocialController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CommunityServiceClient client;

    public CitizenSocialController(CommunityServiceClient client) {
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
            log.warn("citizen social feed scope={} degraded: {}", scope, e.getMessage());
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

    @DeleteMapping("/posts/{id}/reactions")
    public ResponseEntity<JsonNode> unreact(@PathVariable UUID id) {
        return ResponseEntity.ok(client.unreactPost(id.toString()));
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<JsonNode> comments(@PathVariable UUID id) {
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

    @GetMapping("/communities")
    public ResponseEntity<JsonNode> communities(@RequestParam(required = false) String category) {
        try {
            return ResponseEntity.ok(client.listSocialCommunities(category, null));
        } catch (Exception e) {
            return ResponseEntity.ok(OBJECT_MAPPER.valueToTree(List.of()));
        }
    }

    @PostMapping("/communities/{id}/join")
    public ResponseEntity<JsonNode> joinCommunity(@PathVariable UUID id) {
        return ResponseEntity.ok(client.joinSocialCommunity(id.toString()));
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
}
