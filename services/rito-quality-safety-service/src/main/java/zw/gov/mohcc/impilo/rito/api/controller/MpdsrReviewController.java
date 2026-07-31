package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.MpdsrReviewService;
import zw.gov.mohcc.impilo.rito.persistence.entity.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MPDSR review HTTP surface — QI/clinical governance roles only (not citizen, not EHR maternity chart).
 */
@RestController
@RequestMapping("/internal/v1/mpdsr/reviews")
public class MpdsrReviewController {

    private final MpdsrReviewService mpdsrReviewService;

    public MpdsrReviewController(MpdsrReviewService mpdsrReviewService) {
        this.mpdsrReviewService = mpdsrReviewService;
    }

    @PostMapping("/from-death-event")
    public ResponseEntity<MpdsrReviewEntity> fromDeathEvent(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        MpdsrReviewService.DeathEventIntake intake = new MpdsrReviewService.DeathEventIntake(
                uuid(body, "deathCaseId", "death_case_id", "caseId"),
                uuid(body, "facilityId", "facility_id"),
                uuid(body, "districtId", "district_id"),
                str(body, "flags", "triggerFlags", "trigger_flags"),
                str(body, "nearMissRef", "near_miss_ref"),
                str(body, "whoClassification", "who_classification"),
                str(body, "reviewLevel", "review_level"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mpdsrReviewService.openFromDeathEvent(tenantId, intake, actorId));
    }

    @GetMapping("")
    public List<MpdsrReviewEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) String status) {
        return mpdsrReviewService.list(tenantId, status);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        MpdsrReviewEntity review = mpdsrReviewService.get(tenantId, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("review", review);
        out.put("meetings", mpdsrReviewService.meetings(id));
        out.put("findings", mpdsrReviewService.findings(id));
        out.put("actions", mpdsrReviewService.actions(tenantId, id));
        out.put("readinessTasks", mpdsrReviewService.readinessTasks(id));
        return out;
    }

    @PostMapping("/{id}/committee-meetings")
    public ResponseEntity<MpdsrCommitteeMeetingEntity> committeeMeeting(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        OffsetDateTime meetingAt = odt(body, "meetingAt", "meeting_at");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                mpdsrReviewService.recordCommitteeMeeting(
                        tenantId, id, meetingAt,
                        str(body, "reviewLevel", "review_level"),
                        str(body, "chairActorId", "chair_actor_id"),
                        str(body, "membersJson", "members_json"),
                        str(body, "minutesSummary", "minutes_summary")));
    }

    @PostMapping("/{id}/findings")
    public ResponseEntity<MpdsrFindingEntity> finding(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                mpdsrReviewService.addFinding(tenantId, id,
                        required(body, "delayType", "delay_type"),
                        required(body, "modifiableFactor", "modifiable_factor"),
                        str(body, "narrative")));
    }

    @PostMapping("/{id}/actions")
    public ResponseEntity<CorrectiveActionEntity> action(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                mpdsrReviewService.addAction(tenantId, id,
                        required(body, "title"),
                        str(body, "description"),
                        str(body, "ownerActorId", "owner_actor_id"),
                        str(body, "ownerActorType", "owner_actor_type"),
                        odt(body, "dueAt", "due_at"),
                        actorId));
    }

    @PostMapping("/{id}/readiness-task")
    public ResponseEntity<MpdsrReadinessTaskEntity> readinessTask(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        OffsetDateTime dueAt = body != null ? odt(body, "dueAt", "due_at") : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mpdsrReviewService.raiseFacilityReadinessTask(tenantId, id, dueAt));
    }

    @PostMapping("/{id}/close")
    public MpdsrReviewEntity close(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id) {
        return mpdsrReviewService.closeReview(tenantId, id, actorId, actorType);
    }

    private static String required(Map<String, Object> body, String... keys) {
        String v = str(body, keys);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing " + keys[0]);
        }
        return v;
    }

    private static String str(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    private static UUID uuid(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        return UUID.fromString(s);
    }

    private static OffsetDateTime odt(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        return OffsetDateTime.parse(s);
    }
}
