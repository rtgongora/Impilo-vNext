package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;
import zw.gov.mohcc.impilo.simba.social.core.SocialModerationService;
import zw.gov.mohcc.impilo.simba.social.entity.ContentReportEntity;
import zw.gov.mohcc.impilo.simba.social.entity.ModerationActionEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Social moderation + safety. Anyone can file a report; SELF_HARM/ABUSE reports auto-escalate to a
 * care linkage. The inbox + action endpoints require a moderator/provider/operator actor.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social/moderation")
public class SocialModerationController {

    private final SocialModerationService moderation;

    public SocialModerationController(SocialModerationService moderation) {
        this.moderation = moderation;
    }

    @PostMapping("/reports")
    public ResponseEntity<Map<String, Object>> report(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        String reporter = actorId != null ? actorId : required(body, "reporter_cpid");
        ContentReportEntity r = moderation.report(tenantId, reporter,
                required(body, "subject_type").toUpperCase(), UUID.fromString(required(body, "subject_id")),
                str(body, "subject_owner_cpid"), required(body, "reason"), str(body, "detail"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", r,
                "escalated", r.getCareLinkageId() != null));
    }

    @GetMapping("/inbox")
    public Map<String, Object> inbox(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestParam(value = "status", required = false) String status) {
        requireModerator(actorType);
        return Map.of("data", moderation.inbox(tenantId, status),
                "backlog", moderation.backlogCount(tenantId));
    }

    @PostMapping("/{reportId}/action")
    public Map<String, Object> act(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable("reportId") UUID reportId,
            @RequestBody Map<String, Object> body) {
        requireModerator(actorType);
        ModerationActionEntity a = moderation.act(tenantId, reportId, actorId,
                required(body, "action_state").toUpperCase(), str(body, "rationale"));
        return Map.of("data", a);
    }

    private static void requireModerator(String actorType) {
        String t = actorType == null ? "" : actorType.toUpperCase();
        boolean ok = t.equals("MODERATOR") || t.equals("PROVIDER") || t.equals("OPERATOR")
                || t.equals("PROGRAMME_ADMIN") || t.equals("SYSTEM");
        if (!ok) {
            throw new WellnessAccessGuard.WellnessAccessDeniedException("Moderator actor required");
        }
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
