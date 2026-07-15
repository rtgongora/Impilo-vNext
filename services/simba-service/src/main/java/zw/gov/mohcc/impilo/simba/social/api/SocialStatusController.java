package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.social.core.SocialStatusService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialStatusEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Ephemeral wellness-social statuses (create / list-active / delete). Sovereign namespace
 * (/internal/v1/wellness/social/**) — distinct from the generic community-service social stack.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social/statuses")
public class SocialStatusController {

    private final SocialStatusService statuses;

    public SocialStatusController(SocialStatusService statuses) {
        this.statuses = statuses;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        String actor = actorId != null ? actorId : str(body, "actor_cpid");
        if (actor == null) {
            throw new IllegalArgumentException("Missing actor");
        }
        SocialStatusService.CreateStatus in = new SocialStatusService.CreateStatus(
                actor, actorType, str(body, "text"), str(body, "mood"),
                str(body, "visibility"), intOrNull(body.get("ttl_hours")));
        SocialStatusEntity s = statuses.create(tenantId, in, correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", s));
    }

    @GetMapping
    public Map<String, Object> active(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(value = "person_cpid", required = false) String personCpid,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        String viewer = actorId != null ? actorId : personCpid;
        return Map.of("data", statuses.listActive(tenantId, viewer, limit));
    }

    @DeleteMapping("/{statusId}")
    public Map<String, Object> delete(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("statusId") UUID statusId,
            @RequestParam(value = "person_cpid", required = false) String personCpid) {
        String actor = actorId != null ? actorId : personCpid;
        if (actor == null) {
            throw new IllegalArgumentException("Missing actor");
        }
        return Map.of("data", statuses.delete(tenantId, statusId, actor, correlationId, requestId));
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    private static Integer intOrNull(Object v) {
        if (v == null) return null;
        try {
            return Integer.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
