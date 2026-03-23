package zw.gov.mohcc.impilo.offlineedge.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.offlineedge.core.BreakGlassService;
import zw.gov.mohcc.impilo.offlineedge.domain.BreakGlassEventEntity;

import java.util.*;

/**
 * REST endpoints for break-glass activation and review.
 *
 * <p>Break-glass is an emergency override that allows clinicians to perform
 * actions beyond their entitlement scope. All activations are audited and
 * require mandatory review within 24 hours.</p>
 */
@RestController
public class BreakGlassController {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassController.class);
    private final BreakGlassService breakGlassService;

    public BreakGlassController(BreakGlassService breakGlassService) {
        this.breakGlassService = breakGlassService;
    }

    /**
     * Activate break-glass mode.
     */
    @PostMapping("/internal/v1/offline/break-glass")
    public ResponseEntity<?> activate(@RequestBody Map<String, String> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErrorEnvelope.of("VALIDATION_FAILED", "reason is required",
                            ctx.requestId(), ctx.correlationId()));
        }

        String actorId = body.getOrDefault("actor_id", ctx.principal() != null ? ctx.principal().getName() : "unknown");
        String facilityId = body.getOrDefault("facility_id", "unknown");
        String patientRef = body.get("patient_ref");
        String overrideType = body.getOrDefault("override_type", "SCOPE_EXTENSION");
        String originalScope = body.get("original_scope");
        String extendedScope = body.get("extended_scope");
        UUID entitlementId = body.containsKey("entitlement_id")
                ? UUID.fromString(body.get("entitlement_id")) : null;

        BreakGlassEventEntity event = breakGlassService.activate(
                tenantId, actorId, facilityId, patientRef, reason, overrideType,
                originalScope, extendedScope, entitlementId,
                ctx.podId(), ctx.correlationId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(event));
    }

    /**
     * List break-glass events with optional status filter.
     */
    @GetMapping("/internal/v1/offline/break-glass")
    public ResponseEntity<?> list(@RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "0") int cursor,
                                   @RequestParam(defaultValue = "50") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        Page<BreakGlassEventEntity> page = breakGlassService.list(tenantId, status, cursor, limit);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("items", page.getContent().stream().map(this::toResponse).toList());
        responseBody.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        responseBody.put("limit", limit);
        responseBody.put("total_elements", page.getTotalElements());
        responseBody.put("pending_count", breakGlassService.countPendingReviews(tenantId));
        return ResponseEntity.ok(responseBody);
    }

    /**
     * Get a single break-glass event.
     */
    @GetMapping("/internal/v1/offline/break-glass/{event_id}")
    public ResponseEntity<?> get(@PathVariable("event_id") UUID eventId) {
        RequestContext ctx = RequestContextHolder.require();
        return breakGlassService.get(eventId)
                .map(e -> ResponseEntity.ok((Object) toResponse(e)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Break-glass event not found",
                                ctx.requestId(), ctx.correlationId())));
    }

    /**
     * Review a break-glass event.
     *
     * @param resolution one of: APPROVED, ESCALATED, FLAGGED
     */
    @PostMapping("/internal/v1/offline/break-glass/{event_id}/review")
    public ResponseEntity<?> review(@PathVariable("event_id") UUID eventId,
                                     @RequestBody Map<String, String> body) {
        RequestContext ctx = RequestContextHolder.require();

        String resolution = body.get("resolution");
        if (resolution == null || resolution.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErrorEnvelope.of("VALIDATION_FAILED", "resolution is required",
                            ctx.requestId(), ctx.correlationId()));
        }

        String reviewedBy = body.getOrDefault("reviewed_by", ctx.principal() != null ? ctx.principal().getName() : "unknown");
        String notes = body.get("notes");

        try {
            BreakGlassEventEntity event = breakGlassService.review(
                    eventId, resolution, reviewedBy, notes,
                    ctx.podId(), ctx.correlationId());
            return ResponseEntity.ok(toResponse(event));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(),
                            ctx.requestId(), ctx.correlationId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409)
                    .body(ErrorEnvelope.of("ALREADY_REVIEWED", e.getMessage(),
                            ctx.requestId(), ctx.correlationId()));
        }
    }

    private Map<String, Object> toResponse(BreakGlassEventEntity e) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("event_id", e.getEventId().toString());
        r.put("actor_id", e.getActorId());
        r.put("facility_id", e.getFacilityId());
        if (e.getPatientRef() != null) r.put("patient_ref", e.getPatientRef());
        r.put("reason", e.getReason());
        r.put("override_type", e.getOverrideType());
        if (e.getOriginalScope() != null) r.put("original_scope", e.getOriginalScope());
        if (e.getExtendedScope() != null) r.put("extended_scope", e.getExtendedScope());
        if (e.getEntitlementId() != null) r.put("entitlement_id", e.getEntitlementId().toString());
        r.put("activated_at", e.getActivatedAt().toString());
        r.put("review_required_by", e.getReviewRequiredBy().toString());
        r.put("status", e.getStatus());
        if (e.getReviewedBy() != null) r.put("reviewed_by", e.getReviewedBy());
        if (e.getReviewedAt() != null) r.put("reviewed_at", e.getReviewedAt().toString());
        if (e.getReviewNotes() != null) r.put("review_notes", e.getReviewNotes());
        return r;
    }
}
