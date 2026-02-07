package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.ReconcileStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ReconcileQueueEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ReconcileQueueRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stock reconciliation service implementation.
 *
 * <p>Manages reconciliation entries that compare internal stock quantities
 * with external system (e.g. eLMIS) quantities. Each entry includes a
 * confidence score indicating match quality, and operators can resolve
 * discrepancies through the resolution workflow.</p>
 *
 * <p>Confidence score formula:
 * <ul>
 *   <li>1.0 if internal == external (exact match)</li>
 *   <li>1 - (|internal - external| / max(|internal|, |external|)) otherwise</li>
 *   <li>Clamped to minimum of 0.0</li>
 * </ul>
 */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    private final ReconcileQueueRepository reconcileRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReconciliationServiceImpl(ReconcileQueueRepository reconcileRepository,
                                      EventOutboxRepository outboxRepository,
                                      ObjectMapper objectMapper) {
        this.reconcileRepository = reconcileRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<ReconcileQueueEntity> getPending(Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        return reconcileRepository.findByTenantIdAndStatus(
                ctx.tenantId(), ReconcileStatus.PENDING, pageable);
    }

    @Override
    @Transactional
    public ReconcileQueueEntity resolve(UUID recId, String notes) {
        TrustContext ctx = TrustContextHolder.require();

        ReconcileQueueEntity entry = reconcileRepository.findById(recId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reconciliation entry not found: " + recId));

        if (entry.getStatus() != ReconcileStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot resolve entry " + recId + " in status " + entry.getStatus()
                            + " (expected PENDING)");
        }

        entry.setStatus(ReconcileStatus.RESOLVED);
        entry.setResolvedAt(OffsetDateTime.now());
        entry.setOpsNotes(notes);
        entry.setResolvedBy(ctx.actorId());
        entry = reconcileRepository.save(entry);

        publishOutboxEvent("RECONCILE", recId.toString(),
                "RECONCILE_RESOLVED", entry, ctx.tenantId());

        log.info("Reconciliation resolved: recId={}, item={}, batch={}, internal={}, external={}",
                recId, entry.getItemCode(), entry.getBatch(),
                entry.getInternalQty(), entry.getExternalQty());
        return entry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calculates a confidence score based on the difference between
     * internal and external quantities. The score ranges from 0.0 (complete
     * mismatch) to 1.0 (exact match).</p>
     */
    @Override
    @Transactional
    public ReconcileQueueEntity createEntry(UUID facilityId, UUID storeId, String itemCode,
                                              String batch, int internalQty, int externalQty) {
        TrustContext ctx = TrustContextHolder.require();

        // Calculate confidence score
        BigDecimal confidence = calculateConfidence(internalQty, externalQty);

        ReconcileQueueEntity entry = new ReconcileQueueEntity();
        entry.setTenantId(ctx.tenantId());
        entry.setFacilityId(facilityId);
        entry.setStoreId(storeId);
        entry.setItemCode(itemCode);
        entry.setBatch(batch);
        entry.setInternalQty(internalQty);
        entry.setExternalQty(externalQty);
        entry.setConfidence(confidence);
        entry.setStatus(ReconcileStatus.PENDING);

        entry = reconcileRepository.save(entry);

        // Auto-match if confidence is 1.0 (exact match)
        if (confidence.compareTo(BigDecimal.ONE) == 0) {
            entry.setStatus(ReconcileStatus.MATCHED);
            entry = reconcileRepository.save(entry);
            log.info("Reconciliation auto-matched: recId={}, item={}, batch={}, qty={}",
                    entry.getRecId(), itemCode, batch, internalQty);
        } else {
            publishOutboxEvent("RECONCILE", entry.getRecId().toString(),
                    "RECONCILE_ENTRY_CREATED", entry, ctx.tenantId());
            log.info("Reconciliation entry created: recId={}, item={}, batch={}, " +
                            "internal={}, external={}, confidence={}",
                    entry.getRecId(), itemCode, batch, internalQty, externalQty, confidence);
        }

        return entry;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Calculate the confidence score for a reconciliation entry.
     *
     * <p>Formula: 1.0 if quantities match; otherwise
     * 1 - (|internal - external| / max(|internal|, |external|)).
     * Result is clamped to [0.0, 1.0] and rounded to 4 decimal places.</p>
     *
     * @param internalQty the internal stock quantity
     * @param externalQty the external system stock quantity
     * @return the confidence score between 0.0 and 1.0
     */
    private BigDecimal calculateConfidence(int internalQty, int externalQty) {
        if (internalQty == externalQty) {
            return BigDecimal.ONE;
        }

        int absInternal = Math.abs(internalQty);
        int absExternal = Math.abs(externalQty);
        int maxQty = Math.max(absInternal, absExternal);

        if (maxQty == 0) {
            return BigDecimal.ONE;
        }

        int diff = Math.abs(internalQty - externalQty);
        BigDecimal confidence = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(diff).divide(
                        BigDecimal.valueOf(maxQty), 4, RoundingMode.HALF_UP));

        // Clamp to minimum of 0.0
        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            confidence = BigDecimal.ZERO;
        }

        return confidence;
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
