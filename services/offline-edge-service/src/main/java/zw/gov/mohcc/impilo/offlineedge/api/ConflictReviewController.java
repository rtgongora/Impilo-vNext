package zw.gov.mohcc.impilo.offlineedge.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.offlineedge.domain.ConflictReviewEntity;
import zw.gov.mohcc.impilo.offlineedge.repository.ConflictReviewRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * REST endpoints for managing the offline replay conflict review queue.
 *
 * <p>Conflicts arise when replayed offline vitals collide with data
 * already in BUTANO (e.g. duplicate Observations). Clinicians or
 * administrators use these endpoints to review and resolve conflicts.</p>
 */
@RestController
public class ConflictReviewController {

    private static final Logger log = LoggerFactory.getLogger(ConflictReviewController.class);
    private final ConflictReviewRepository conflictRepository;

    public ConflictReviewController(ConflictReviewRepository conflictRepository) {
        this.conflictRepository = conflictRepository;
    }

    /**
     * List conflicts with optional status filter.
     */
    @GetMapping("/internal/v1/offline/conflicts")
    public ResponseEntity<?> listConflicts(@RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") int cursor,
                                            @RequestParam(defaultValue = "50") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        Page<ConflictReviewEntity> page = conflictRepository.findFiltered(tenantId, status, PageRequest.of(cursor, limit));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("pending_count", conflictRepository.countByTenantIdAndStatus(tenantId, "PENDING"));
        return ResponseEntity.ok(body);
    }

    /**
     * Get a single conflict by ID.
     */
    @GetMapping("/internal/v1/offline/conflicts/{conflict_id}")
    public ResponseEntity<?> getConflict(@PathVariable("conflict_id") UUID conflictId) {
        RequestContext ctx = RequestContextHolder.require();
        return conflictRepository.findById(conflictId)
                .map(c -> ResponseEntity.ok((Object) toResponse(c)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Conflict not found", ctx.requestId(), ctx.correlationId())));
    }

    /**
     * Resolve a conflict.
     *
     * @param resolution one of: KEEP_OFFLINE, KEEP_EXISTING, MERGED
     */
    @PostMapping("/internal/v1/offline/conflicts/{conflict_id}/resolve")
    public ResponseEntity<?> resolveConflict(@PathVariable("conflict_id") UUID conflictId,
                                              @RequestBody Map<String, String> body) {
        RequestContext ctx = RequestContextHolder.require();
        String resolution = body.get("resolution");
        String notes = body.get("notes");
        String resolvedBy = body.getOrDefault("resolved_by", "system");

        if (resolution == null || resolution.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErrorEnvelope.of("VALIDATION_FAILED", "resolution is required",
                            ctx.requestId(), ctx.correlationId()));
        }

        return conflictRepository.findById(conflictId).map(conflict -> {
            if (!"PENDING".equals(conflict.getStatus())) {
                return ResponseEntity.status(409)
                        .body((Object) ErrorEnvelope.of("ALREADY_RESOLVED",
                                "Conflict already resolved with status: " + conflict.getStatus(),
                                ctx.requestId(), ctx.correlationId()));
            }

            conflict.setStatus("RESOLVED_" + resolution.toUpperCase());
            conflict.setResolvedBy(resolvedBy);
            conflict.setResolutionNotes(notes);
            conflict.setResolvedAt(OffsetDateTime.now());
            conflictRepository.save(conflict);

            log.info("Resolved conflict {} as {} by {}", conflictId, resolution, resolvedBy);
            return ResponseEntity.ok((Object) toResponse(conflict));
        }).orElseGet(() -> ResponseEntity.status(404)
                .body(ErrorEnvelope.of("NOT_FOUND", "Conflict not found", ctx.requestId(), ctx.correlationId())));
    }

    private Map<String, Object> toResponse(ConflictReviewEntity c) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("conflict_id", c.getConflictId().toString());
        r.put("action_id", c.getActionId().toString());
        if (c.getBatchId() != null) r.put("batch_id", c.getBatchId().toString());
        r.put("patient_ref", c.getPatientRef());
        r.put("action_type", c.getActionType());
        r.put("conflict_reason", c.getConflictReason());
        r.put("status", c.getStatus());
        r.put("created_at", c.getCreatedAt().toString());
        if (c.getResolvedBy() != null) r.put("resolved_by", c.getResolvedBy());
        if (c.getResolvedAt() != null) r.put("resolved_at", c.getResolvedAt().toString());
        if (c.getResolutionNotes() != null) r.put("resolution_notes", c.getResolutionNotes());
        return r;
    }
}
