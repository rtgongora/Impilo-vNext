package zw.gov.mohcc.impilo.offlineedge.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.offlineedge.domain.BreakGlassEventEntity;
import zw.gov.mohcc.impilo.offlineedge.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.offlineedge.repository.BreakGlassEventRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for break-glass activation and review.
 *
 * <p>Break-glass allows a clinician to perform emergency actions beyond their
 * normal entitlement scope. Every activation is:
 * <ul>
 *   <li>Logged with full context (actor, facility, patient, reason)</li>
 *   <li>Published as a HIGH-priority outbox event</li>
 *   <li>Added to a review queue with a 24-hour deadline</li>
 *   <li>Auto-escalated if not reviewed within the deadline</li>
 * </ul>
 */
@Service
public class BreakGlassService {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassService.class);

    private final BreakGlassEventRepository breakGlassRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public BreakGlassService(BreakGlassEventRepository breakGlassRepository,
                              OutboxEventRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.breakGlassRepository = breakGlassRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Activate break-glass mode for an emergency action.
     *
     * @param tenantId      tenant scope
     * @param actorId       the clinician invoking break-glass
     * @param facilityId    facility context
     * @param patientRef    patient being accessed (CPID)
     * @param reason        justification text (mandatory)
     * @param overrideType  SCOPE_EXTENSION or EXPIRED_ENTITLEMENT
     * @param originalScope original scope boundaries
     * @param extendedScope new scope requested
     * @param entitlementId linked entitlement (if applicable)
     * @param podId         pod identifier for event routing
     * @param correlationId request correlation
     * @return the created break-glass event
     */
    @Transactional
    public BreakGlassEventEntity activate(UUID tenantId, String actorId, String facilityId,
                                            String patientRef, String reason, String overrideType,
                                            String originalScope, String extendedScope,
                                            UUID entitlementId, String podId, String correlationId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Break-glass reason is mandatory");
        }

        BreakGlassEventEntity event = new BreakGlassEventEntity();
        event.setTenantId(tenantId);
        event.setActorId(actorId);
        event.setFacilityId(facilityId);
        event.setPatientRef(patientRef);
        event.setReason(reason);
        event.setOverrideType(overrideType != null ? overrideType : "SCOPE_EXTENSION");
        event.setOriginalScope(originalScope);
        event.setExtendedScope(extendedScope);
        event.setEntitlementId(entitlementId);
        event.setActivatedAt(OffsetDateTime.now());
        event.setReviewRequiredBy(OffsetDateTime.now().plusHours(24));
        event.setStatus("PENDING_REVIEW");

        breakGlassRepository.save(event);

        // Emit HIGH-priority outbox event
        emitBreakGlassEvent(event, podId, correlationId);

        log.warn("BREAK-GLASS activated: actor={}, facility={}, patient={}, reason={}",
                actorId, facilityId, patientRef, reason);

        return event;
    }

    /**
     * Review a break-glass event. Resolution must be one of:
     * APPROVED, ESCALATED, FLAGGED.
     */
    @Transactional
    public BreakGlassEventEntity review(UUID eventId, String resolution, String reviewedBy,
                                          String notes, String podId, String correlationId) {
        BreakGlassEventEntity event = breakGlassRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Break-glass event not found: " + eventId));

        if (!"PENDING_REVIEW".equals(event.getStatus())) {
            throw new IllegalStateException("Break-glass event already reviewed: " + event.getStatus());
        }

        Set<String> validResolutions = Set.of("APPROVED", "ESCALATED", "FLAGGED");
        if (!validResolutions.contains(resolution)) {
            throw new IllegalArgumentException("Invalid resolution: " + resolution
                    + ". Must be one of: APPROVED, ESCALATED, FLAGGED");
        }

        event.setStatus(resolution);
        event.setReviewedBy(reviewedBy);
        event.setReviewNotes(notes);
        event.setReviewedAt(OffsetDateTime.now());
        breakGlassRepository.save(event);

        // Emit review event
        emitReviewEvent(event, podId, correlationId);

        log.info("Break-glass reviewed: eventId={}, resolution={}, by={}",
                eventId, resolution, reviewedBy);

        return event;
    }

    /**
     * List break-glass events with optional status filter.
     */
    @Transactional(readOnly = true)
    public Page<BreakGlassEventEntity> list(UUID tenantId, String status, int page, int size) {
        return breakGlassRepository.findFiltered(tenantId, status, PageRequest.of(page, size));
    }

    /**
     * Get a single break-glass event.
     */
    @Transactional(readOnly = true)
    public Optional<BreakGlassEventEntity> get(UUID eventId) {
        return breakGlassRepository.findById(eventId);
    }

    /**
     * Count pending reviews for a tenant.
     */
    @Transactional(readOnly = true)
    public long countPendingReviews(UUID tenantId) {
        return breakGlassRepository.countByTenantIdAndStatus(tenantId, "PENDING_REVIEW");
    }

    /**
     * Escalate overdue break-glass events (past 24h review deadline).
     * Runs every 15 minutes.
     */
    @Scheduled(fixedDelay = 900_000) // 15 minutes
    @Transactional
    public void escalateOverdueReviews() {
        List<BreakGlassEventEntity> overdue = breakGlassRepository
                .findByStatusAndReviewRequiredByBefore("PENDING_REVIEW", OffsetDateTime.now());

        for (BreakGlassEventEntity event : overdue) {
            event.setStatus("ESCALATED");
            event.setReviewNotes("Auto-escalated: review deadline exceeded");
            breakGlassRepository.save(event);

            log.warn("Break-glass auto-escalated: eventId={}, actor={}, activatedAt={}",
                    event.getEventId(), event.getActorId(), event.getActivatedAt());
        }

        if (!overdue.isEmpty()) {
            log.info("Escalated {} overdue break-glass reviews", overdue.size());
        }
    }

    private void emitBreakGlassEvent(BreakGlassEventEntity event, String podId, String correlationId) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("BreakGlass");
        outbox.setAggregateId(event.getEventId().toString());
        outbox.setEventType("impilo.offline.break_glass.activated.v1");
        outbox.setCorrelationId(parseUuid(correlationId));
        outbox.setCausationId(parseUuid(correlationId));
        outbox.setTenantId(event.getTenantId());
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(event.getPatientRef());
        outbox.setSubjectType("BreakGlass");
        outbox.setOccurredAt(event.getActivatedAt());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", event.getEventId().toString());
        payload.put("actor_id", event.getActorId());
        payload.put("facility_id", event.getFacilityId());
        payload.put("patient_ref", event.getPatientRef());
        payload.put("reason", event.getReason());
        payload.put("override_type", event.getOverrideType());
        payload.put("original_scope", event.getOriginalScope());
        payload.put("extended_scope", event.getExtendedScope());
        payload.put("activated_at", event.getActivatedAt().toString());
        payload.put("review_required_by", event.getReviewRequiredBy().toString());
        payload.put("priority", "HIGH");
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(event.getActorId());
        outboxRepository.save(outbox);
    }

    private void emitReviewEvent(BreakGlassEventEntity event, String podId, String correlationId) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("BreakGlass");
        outbox.setAggregateId(event.getEventId().toString());
        outbox.setEventType("impilo.offline.break_glass.reviewed.v1");
        outbox.setCorrelationId(parseUuid(correlationId));
        outbox.setCausationId(parseUuid(correlationId));
        outbox.setTenantId(event.getTenantId());
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(event.getPatientRef());
        outbox.setSubjectType("BreakGlass");
        outbox.setOccurredAt(OffsetDateTime.now());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", event.getEventId().toString());
        payload.put("resolution", event.getStatus());
        payload.put("reviewed_by", event.getReviewedBy());
        payload.put("review_notes", event.getReviewNotes());
        payload.put("reviewed_at", event.getReviewedAt().toString());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(event.getActorId());
        outboxRepository.save(outbox);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { return null; }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON serialization failed", e); }
    }
}
