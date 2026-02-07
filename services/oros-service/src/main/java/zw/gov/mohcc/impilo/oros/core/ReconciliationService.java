package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.ReconcileStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ReconcileQueueEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ReconcileQueueRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Manages reconciliation of hybrid-routed orders.
 *
 * <p>When orders are routed in HYBRID mode, results from both internal
 * and external systems must be matched. This service provides operations
 * for matching, resolving, and querying pending reconciliation entries.</p>
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconcileQueueRepository reconcileRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReconciliationService(ReconcileQueueRepository reconcileRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.reconcileRepository = reconcileRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Get pending reconciliation entries for a tenant.
     */
    public Page<ReconcileQueueEntity> getPendingReconciliations(Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        return reconcileRepository.findByTenantIdAndStatus(
                ctx.tenantId(), ReconcileStatus.PENDING, pageable);
    }

    /**
     * Match a reconciliation entry to an existing order.
     * Sets confidence to 1.0 (manual match) and status to MATCHED.
     */
    @Transactional
    public ReconcileQueueEntity matchReconciliation(UUID recId, String orderId) {
        TrustContext ctx = TrustContextHolder.require();

        ReconcileQueueEntity entry = reconcileRepository.findById(recId)
                .orElseThrow(() -> new IllegalArgumentException("Reconcile entry not found: " + recId));

        if (entry.getStatus() != ReconcileStatus.PENDING) {
            throw new IllegalStateException("Cannot match entry in status " + entry.getStatus());
        }

        entry.setOrderId(orderId);
        entry.setConfidence(BigDecimal.ONE);
        entry.setStatus(ReconcileStatus.MATCHED);
        entry.setMatchedAt(OffsetDateTime.now());
        entry.setResolvedBy(ctx.actorId());
        entry = reconcileRepository.save(entry);

        publishEvent("RECONCILE", recId.toString(), "RECONCILE_MATCHED",
                entry, ctx.tenantId());

        log.info("Reconcile matched: recId={}, orderId={}, actor={}",
                recId, orderId, ctx.actorId());
        return entry;
    }

    /**
     * Resolve a reconciliation entry with operational notes.
     */
    @Transactional
    public ReconcileQueueEntity resolveReconciliation(UUID recId, String notes) {
        TrustContext ctx = TrustContextHolder.require();

        ReconcileQueueEntity entry = reconcileRepository.findById(recId)
                .orElseThrow(() -> new IllegalArgumentException("Reconcile entry not found: " + recId));

        entry.setStatus(ReconcileStatus.RESOLVED);
        entry.setOpsNotes(notes);
        entry.setResolvedBy(ctx.actorId());
        entry = reconcileRepository.save(entry);

        publishEvent("RECONCILE", recId.toString(), "RECONCILE_RESOLVED",
                entry, ctx.tenantId());

        log.info("Reconcile resolved: recId={}, actor={}", recId, ctx.actorId());
        return entry;
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
