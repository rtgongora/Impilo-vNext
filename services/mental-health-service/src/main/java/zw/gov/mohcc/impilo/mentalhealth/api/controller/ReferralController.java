package zw.gov.mohcc.impilo.mentalhealth.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.mentalhealth.core.ReferralService;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The accepting side of a psychiatric emergency handover. See ReferralService for the doctrine. */
@RestController
@RequestMapping("/internal/v1/mental-health/referrals")
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> intake(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID facilityId = uuidVal(body, "facilityId", "facility_id");
        if (facilityId == null) facilityId = ctx.facilityId();
        String requestedBy = str(body, "requestedBy", "requested_by");
        var referral = referralService.intake(
                ctx.tenantId(),
                facilityId,
                uuidVal(body, "episodeId", "episode_id"),
                uuidVal(body, "handoverId", "handover_id"),
                str(body, "subjectCpid", "subject_cpid"),
                str(body, "requestReason", "request_reason"),
                requestedBy != null ? requestedBy : ctx.actorId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(row(referral), ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        var referral = referralService.get(id, ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(row(referral), ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(@RequestParam(defaultValue = "PENDING") String status) {
        TrustContext ctx = TrustContextHolder.require();
        var referrals = referralService.listByStatus(ctx.tenantId(), status);
        return ResponseEntity.ok(ApiResponse.ok(referrals.stream().map(this::row).toList(), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> accept(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String acceptedBy = str(body, "acceptedBy", "accepted_by");
        var referral = referralService.accept(id, ctx.tenantId(), acceptedBy != null ? acceptedBy : ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(row(referral), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> decline(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String declinedBy = str(body, "declinedBy", "declined_by");
        var referral = referralService.decline(id, ctx.tenantId(),
                declinedBy != null ? declinedBy : ctx.actorId(),
                str(body, "reason", "declineReason", "decline_reason"));
        return ResponseEntity.ok(ApiResponse.ok(row(referral), ctx.correlationId().toString()));
    }

    private Map<String, Object> row(ReferralEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("tenant_id", r.getTenantId().toString());
        m.put("facility_id", r.getFacilityId().toString());
        m.put("episode_id", r.getEpisodeId().toString());
        m.put("handover_id", r.getHandoverId().toString());
        m.put("subject_cpid", r.getSubjectCpid());
        m.put("status", r.getStatus());
        m.put("request_reason", r.getRequestReason());
        m.put("requested_by", r.getRequestedBy());
        m.put("requested_at", r.getRequestedAt() != null ? r.getRequestedAt().toString() : null);
        m.put("reviewed_by", r.getReviewedBy());
        m.put("reviewed_at", r.getReviewedAt() != null ? r.getReviewedAt().toString() : null);
        m.put("decline_reason", r.getDeclineReason());
        return m;
    }

    static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    static UUID uuidVal(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try { return UUID.fromString(s.trim()); } catch (IllegalArgumentException e) { return null; }
    }

    static OffsetDateTime dateVal(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try { return OffsetDateTime.parse(s.trim()); } catch (Exception e) { return null; }
    }
}
