package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.social.core.SocialCommunityService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommunityEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommunityMembershipEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Wellness-social COMMUNITIES (sovereign layer, /internal/v1/wellness/social/communities/**).
 * Handles create/list/detail/members/join/leave/role and official community announcements.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social/communities")
public class SocialCommunityController {

    private final SocialCommunityService communities;

    public SocialCommunityController(SocialCommunityService communities) {
        this.communities = communities;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        String creator = actorId != null ? actorId : required(body, "person_cpid");
        SocialCommunityService.CreateCommunity in = new SocialCommunityService.CreateCommunity(
                required(body, "name"), str(body, "description"), str(body, "community_type"),
                uuid(body, "programme_id"), str(body, "visibility"));
        SocialCommunityEntity c = communities.create(tenantId, creator, in, correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", c));
    }

    @GetMapping
    public Map<String, Object> list(@RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return Map.of("data", communities.list(tenantId));
    }

    @GetMapping("/{communityId}")
    public Map<String, Object> detail(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("communityId") UUID communityId) {
        return Map.of("data", communities.get(tenantId, communityId),
                "members", communities.members(communityId));
    }

    @GetMapping("/{communityId}/members")
    public Map<String, Object> members(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("communityId") UUID communityId) {
        return Map.of("data", communities.members(communityId));
    }

    @PostMapping("/{communityId}/join")
    public ResponseEntity<Map<String, Object>> join(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("communityId") UUID communityId,
            @RequestBody(required = false) Map<String, Object> body) {
        String cpid = actorId != null ? actorId : required(body, "person_cpid");
        SocialCommunityMembershipEntity m = communities.join(tenantId, communityId, cpid,
                correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", m));
    }

    @PostMapping("/{communityId}/leave")
    public Map<String, Object> leave(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("communityId") UUID communityId,
            @RequestBody(required = false) Map<String, Object> body) {
        String cpid = actorId != null ? actorId : required(body, "person_cpid");
        communities.leave(tenantId, communityId, cpid, correlationId, requestId);
        return Map.of("data", Map.of("community_id", communityId.toString(), "status", "LEFT"));
    }

    @PatchMapping("/{communityId}/members/{memberCpid}/role")
    public Map<String, Object> setRole(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("communityId") UUID communityId,
            @PathVariable("memberCpid") String memberCpid,
            @RequestBody Map<String, Object> body) {
        return Map.of("data", communities.setRole(tenantId, communityId, actorId, memberCpid,
                required(body, "role"), correlationId, requestId));
    }

    @PostMapping("/{communityId}/announcements")
    public ResponseEntity<Map<String, Object>> announce(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("communityId") UUID communityId,
            @RequestBody Map<String, Object> body) {
        SocialPostEntity p = communities.announce(tenantId, communityId, actorId, actorType,
                required(body, "body"), correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", p));
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        String s = str(body, key);
        return (s == null || s.isBlank()) ? null : UUID.fromString(s);
    }
}
