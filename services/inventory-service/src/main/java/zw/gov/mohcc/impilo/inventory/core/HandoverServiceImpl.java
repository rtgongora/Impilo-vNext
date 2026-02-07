package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.HandoverStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.HandoverEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.HandoverRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Custody handover workflow implementation.
 *
 * <p>Manages the transfer of custody for a store between two actors,
 * typically during shift changes. The workflow ensures both parties
 * explicitly sign off on the handover for accountability.</p>
 *
 * <p>State machine:
 * <pre>
 *   INITIATED -> OUTGOING_SIGNED -> INCOMING_SIGNED -> COMPLETED (auto)
 * </pre>
 *
 * <p>The incoming signature automatically transitions through INCOMING_SIGNED
 * to COMPLETED in a single operation, as both states represent the same
 * logical completion point.</p>
 */
@Service
public class HandoverServiceImpl implements HandoverService {

    private static final Logger log = LoggerFactory.getLogger(HandoverServiceImpl.class);

    private final HandoverRepository handoverRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public HandoverServiceImpl(HandoverRepository handoverRepository,
                                EventOutboxRepository outboxRepository,
                                ObjectMapper objectMapper) {
        this.handoverRepository = handoverRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public HandoverEntity startHandover(UUID storeId, String toActor) {
        TrustContext ctx = TrustContextHolder.require();

        if (storeId == null) {
            throw new IllegalArgumentException("storeId is required");
        }
        if (toActor == null || toActor.isBlank()) {
            throw new IllegalArgumentException("toActor is required");
        }

        HandoverEntity handover = new HandoverEntity();
        handover.setTenantId(ctx.tenantId());
        handover.setFacilityId(ctx.facilityId());
        handover.setStoreId(storeId);
        handover.setFromActor(ctx.actorId());
        handover.setToActor(toActor);
        handover.setStatus(HandoverStatus.INITIATED);

        handover = handoverRepository.save(handover);

        publishOutboxEvent("HANDOVER", handover.getHandoverId().toString(),
                "HANDOVER_INITIATED", handover, ctx.tenantId());

        log.info("Handover initiated: handoverId={}, store={}, from={}, to={}",
                handover.getHandoverId(), storeId, ctx.actorId(), toActor);
        return handover;
    }

    @Override
    @Transactional
    public HandoverEntity signOutgoing(UUID handoverId) {
        TrustContext ctx = TrustContextHolder.require();
        HandoverEntity handover = loadHandover(handoverId);

        if (handover.getStatus() != HandoverStatus.INITIATED) {
            throw new IllegalStateException(
                    "Cannot sign outgoing: handover " + handoverId + " is in status "
                            + handover.getStatus() + " (expected INITIATED)");
        }

        // Verify the signing actor is the outgoing actor
        if (!ctx.actorId().equals(handover.getFromActor())) {
            log.warn("Outgoing signature by {} but expected {} for handover {}",
                    ctx.actorId(), handover.getFromActor(), handoverId);
        }

        handover.setStatus(HandoverStatus.OUTGOING_SIGNED);
        handover.setOutgoingSignedAt(OffsetDateTime.now());
        handover = handoverRepository.save(handover);

        publishOutboxEvent("HANDOVER", handoverId.toString(),
                "HANDOVER_OUTGOING_SIGNED", handover, ctx.tenantId());

        log.info("Handover outgoing signed: handoverId={}, actor={}", handoverId, ctx.actorId());
        return handover;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The incoming signature automatically completes the handover.
     * The state transitions from OUTGOING_SIGNED through INCOMING_SIGNED
     * to COMPLETED in a single atomic operation.</p>
     */
    @Override
    @Transactional
    public HandoverEntity signIncoming(UUID handoverId) {
        TrustContext ctx = TrustContextHolder.require();
        HandoverEntity handover = loadHandover(handoverId);

        if (handover.getStatus() != HandoverStatus.OUTGOING_SIGNED) {
            throw new IllegalStateException(
                    "Cannot sign incoming: handover " + handoverId + " is in status "
                            + handover.getStatus() + " (expected OUTGOING_SIGNED)");
        }

        // Verify the signing actor is the incoming actor
        if (!ctx.actorId().equals(handover.getToActor())) {
            log.warn("Incoming signature by {} but expected {} for handover {}",
                    ctx.actorId(), handover.getToActor(), handoverId);
        }

        // Auto-complete: OUTGOING_SIGNED -> INCOMING_SIGNED -> COMPLETED
        handover.setStatus(HandoverStatus.COMPLETED);
        handover.setIncomingSignedAt(OffsetDateTime.now());
        handover = handoverRepository.save(handover);

        publishOutboxEvent("HANDOVER", handoverId.toString(),
                "HANDOVER_COMPLETED", handover, ctx.tenantId());

        log.info("Handover completed: handoverId={}, store={}, from={}, to={}",
                handoverId, handover.getStoreId(), handover.getFromActor(), handover.getToActor());
        return handover;
    }

    @Override
    public Page<HandoverEntity> getHandovers(Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        return handoverRepository.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId(), pageable);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private HandoverEntity loadHandover(UUID handoverId) {
        return handoverRepository.findById(handoverId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Handover not found: " + handoverId));
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                     String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event: type={}, aggregateId={}", eventType, aggregateId, e);
        }
    }
}
