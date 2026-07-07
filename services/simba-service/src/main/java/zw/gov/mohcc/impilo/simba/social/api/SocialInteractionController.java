package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.social.core.SocialInteractionService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommentEntity;

import java.util.Map;
import java.util.UUID;

/** Comments, reactions, bookmarks on wellness-social content. */
@RestController
@RequestMapping("/internal/v1/wellness/social")
public class SocialInteractionController {

    private final SocialInteractionService interactions;

    public SocialInteractionController(SocialInteractionService interactions) {
        this.interactions = interactions;
    }

    @GetMapping("/posts/{postId}/comments")
    public Map<String, Object> comments(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("postId") UUID postId) {
        return Map.of("data", interactions.comments(postId));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<Map<String, Object>> comment(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable("postId") UUID postId,
            @RequestBody Map<String, Object> body) {
        String actor = actorId != null ? actorId : required(body, "actor_cpid");
        SocialCommentEntity c = interactions.comment(tenantId, postId, actor, actorType, required(body, "body"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", c));
    }

    @PostMapping("/{subjectType}/{subjectId}/react")
    public Map<String, Object> react(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable("subjectType") String subjectType,
            @PathVariable("subjectId") UUID subjectId,
            @RequestBody Map<String, Object> body) {
        String actor = actorId != null ? actorId : required(body, "actor_cpid");
        return Map.of("data", interactions.react(tenantId, subjectType.toUpperCase(), subjectId, actor,
                required(body, "reaction")));
    }

    @PostMapping("/{subjectType}/{subjectId}/bookmark")
    public Map<String, Object> bookmark(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable("subjectType") String subjectType,
            @PathVariable("subjectId") UUID subjectId,
            @RequestBody(required = false) Map<String, Object> body) {
        String actor = actorId != null ? actorId
                : (body != null ? required(body, "actor_cpid") : null);
        if (actor == null) {
            throw new IllegalArgumentException("Missing actor");
        }
        return Map.of("data", interactions.bookmark(tenantId, subjectType.toUpperCase(), subjectId, actor));
    }

    @GetMapping("/saved")
    public Map<String, Object> saved(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(value = "person_cpid", required = false) String personCpid) {
        String actor = actorId != null ? actorId : personCpid;
        if (actor == null) {
            throw new IllegalArgumentException("Missing actor");
        }
        return Map.of("data", interactions.savedFor(tenantId, actor));
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
