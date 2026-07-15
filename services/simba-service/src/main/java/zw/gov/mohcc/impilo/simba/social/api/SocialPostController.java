package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.social.core.SocialFeedService;
import zw.gov.mohcc.impilo.simba.social.core.SocialGroupService;
import zw.gov.mohcc.impilo.simba.social.core.SocialPostService;
import zw.gov.mohcc.impilo.simba.social.core.SocialVisibilityService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness-social posts + feed. DISTINCT from the generic community-service social stack
 * (/internal/v1/social/**) — this is the wellness-sovereign, consent-governed layer.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social")
public class SocialPostController {

    private final SocialPostService postService;
    private final SocialFeedService feedService;
    private final SocialVisibilityService visibility;
    private final SocialGroupService groups;

    public SocialPostController(SocialPostService postService,
                                SocialFeedService feedService,
                                SocialVisibilityService visibility,
                                SocialGroupService groups) {
        this.postService = postService;
        this.feedService = feedService;
        this.visibility = visibility;
        this.groups = groups;
    }

    @PostMapping("/posts")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        String actor = actorId != null ? actorId : required(body, "actor_cpid");
        UUID groupId = uuid(body.get("group_id"));
        // Group posts require an active member with posting permission (VIEWER/blocked members denied).
        if (groupId != null) {
            groups.enforceCanPost(groupId, actor);
        }
        SocialPostService.CreatePost in = new SocialPostService.CreatePost(
                actor, actorType, providerId,
                groupId, uuid(body.get("community_id")), uuid(body.get("programme_id")),
                required(body, "body"),
                body.get("media_object_ids") instanceof List<?> l ? (List<String>) l : null,
                str(body, "sensitive_category"), str(body, "milestone_ref"), str(body, "visibility"));
        SocialPostEntity p = postService.create(tenantId, in, correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", p));
    }

    @GetMapping("/posts/{postId}")
    public Map<String, Object> get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable("postId") UUID postId) {
        SocialPostEntity p = postService.get(tenantId, postId);
        // Owner-or-public read check (group/community membership resolution is a feed-scope concern).
        SocialVisibilityService.ViewerContext v = viewer(tenantId, actorId, p);
        if (!visibility.canView(p, v)) {
            throw new zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard.WellnessAccessDeniedException(
                    "Not visible to you");
        }
        return Map.of("data", p);
    }

    @GetMapping("/feed")
    public Map<String, Object> feed(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "group_id", required = false) UUID groupId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        SocialFeedService.FeedPage page = feedService.feed(tenantId, actorId, filter, groupId, cursor, limit);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("next_cursor", page.nextCursor());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", page.posts());
        out.put("meta", meta);
        return out;
    }

    private SocialVisibilityService.ViewerContext viewer(UUID tenantId, String actorId, SocialPostEntity p) {
        boolean isOwner = actorId != null && actorId.equals(p.getActorCpid());
        // Basic single-post viewer: owner + public evaluation only. Group/community/follower scoping
        // is resolved in SocialFeedService for list reads.
        return new SocialVisibilityService.ViewerContext(tenantId, actorId, isOwner,
                false, false, false, false, false, false);
    }

    private static UUID uuid(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : UUID.fromString(String.valueOf(v));
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
