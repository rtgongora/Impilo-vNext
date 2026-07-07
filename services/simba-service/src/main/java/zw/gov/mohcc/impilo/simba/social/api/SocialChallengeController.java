package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.social.core.SocialChallengeService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Social layer over challenges (sovereign, /internal/v1/wellness/social/challenges/**). Base challenge
 * CRUD/join/progress stays on the existing {@code /internal/v1/wellness/challenges} controller; this
 * adds share-to-feed, the ranked group leaderboard, and the programme-campaign aggregate.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social/challenges")
public class SocialChallengeController {

    private final SocialChallengeService challenges;

    public SocialChallengeController(SocialChallengeService challenges) {
        this.challenges = challenges;
    }

    @PostMapping("/{challengeId}/share")
    public ResponseEntity<Map<String, Object>> share(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("challengeId") UUID challengeId,
            @RequestBody(required = false) Map<String, Object> body) {
        String cpid = actorId != null ? actorId : required(body, "person_cpid");
        SocialPostEntity post = challenges.shareToFeed(tenantId, challengeId, cpid,
                str(body, "message"), str(body, "visibility"), correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", post));
    }

    @GetMapping("/{challengeId}/leaderboard")
    public Map<String, Object> leaderboard(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("challengeId") UUID challengeId) {
        return Map.of("data", challenges.groupLeaderboard(tenantId, challengeId));
    }

    @GetMapping("/campaign-aggregate")
    public Map<String, Object> campaignAggregate(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("programme_id") UUID programmeId) {
        return Map.of("data", challenges.campaignAggregate(tenantId, programmeId));
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
}
