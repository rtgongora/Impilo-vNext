package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.social.core.SocialGroupService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialGroupEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialGroupMembershipEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Wellness-social GROUPS (sovereign layer, /internal/v1/wellness/social/groups/**). Handles
 * create/list/detail/members/join/request-approve/role/leave, pin, and official announcements.
 * The actor cpid is taken from {@code X-Actor-ID}; role/permission enforcement lives in the service.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social/groups")
public class SocialGroupController {

    private final SocialGroupService groups;

    public SocialGroupController(SocialGroupService groups) {
        this.groups = groups;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        String creator = actorId != null ? actorId : required(body, "person_cpid");
        SocialGroupService.CreateGroup in = new SocialGroupService.CreateGroup(
                required(body, "name"), str(body, "description"), str(body, "group_kind"),
                str(body, "topic"), bool(body, "sensitive_flag"), uuid(body, "community_id"),
                uuid(body, "programme_id"), str(body, "visibility"), str(body, "join_policy"),
                intVal(body, "max_members"));
        SocialGroupEntity g = groups.create(tenantId, creator, in, correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", g));
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(value = "mine", required = false, defaultValue = "false") boolean mine) {
        if (mine && actorId != null) {
            return Map.of("data", groups.myGroups(tenantId, actorId));
        }
        return Map.of("data", groups.list(tenantId));
    }

    @GetMapping("/{groupId}")
    public Map<String, Object> detail(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("groupId") UUID groupId) {
        return Map.of("data", groups.get(tenantId, groupId),
                "members", groups.members(groupId));
    }

    @GetMapping("/{groupId}/members")
    public Map<String, Object> members(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("groupId") UUID groupId) {
        return Map.of("data", groups.members(groupId));
    }

    @PostMapping("/{groupId}/join")
    public ResponseEntity<Map<String, Object>> join(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("groupId") UUID groupId,
            @RequestBody(required = false) Map<String, Object> body) {
        String cpid = actorId != null ? actorId : required(body, "person_cpid");
        SocialGroupMembershipEntity m = groups.join(tenantId, groupId, cpid, correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", m));
    }

    @PostMapping("/{groupId}/members/{memberCpid}/approve")
    public Map<String, Object> approve(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("groupId") UUID groupId,
            @PathVariable("memberCpid") String memberCpid) {
        return Map.of("data", groups.approve(tenantId, groupId, actorId, memberCpid,
                correlationId, requestId));
    }

    @PatchMapping("/{groupId}/members/{memberCpid}/role")
    public Map<String, Object> setRole(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("groupId") UUID groupId,
            @PathVariable("memberCpid") String memberCpid,
            @RequestBody Map<String, Object> body) {
        return Map.of("data", groups.setRole(tenantId, groupId, actorId, memberCpid,
                required(body, "role"), correlationId, requestId));
    }

    @PostMapping("/{groupId}/leave")
    public Map<String, Object> leave(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("groupId") UUID groupId,
            @RequestBody(required = false) Map<String, Object> body) {
        String cpid = actorId != null ? actorId : required(body, "person_cpid");
        groups.leave(tenantId, groupId, cpid, correlationId, requestId);
        return Map.of("data", Map.of("group_id", groupId.toString(), "status", "LEFT"));
    }

    @PostMapping("/{groupId}/posts/{postId}/pin")
    public Map<String, Object> pin(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("groupId") UUID groupId,
            @PathVariable("postId") UUID postId) {
        SocialPostEntity p = groups.pin(tenantId, groupId, actorId, postId, correlationId, requestId);
        return Map.of("data", p);
    }

    @PostMapping("/{groupId}/announcements")
    public ResponseEntity<Map<String, Object>> announce(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("groupId") UUID groupId,
            @RequestBody Map<String, Object> body) {
        SocialPostEntity p = groups.announce(tenantId, groupId, actorId, actorType,
                required(body, "body"), correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", p));
    }

    // ── helpers ────────────────────────────────────────────────────────────────
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

    private static boolean bool(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v != null && Boolean.parseBoolean(v.toString());
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        String s = str(body, key);
        return (s == null || s.isBlank()) ? null : UUID.fromString(s);
    }

    private static Integer intVal(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : Integer.valueOf(v.toString());
    }
}
