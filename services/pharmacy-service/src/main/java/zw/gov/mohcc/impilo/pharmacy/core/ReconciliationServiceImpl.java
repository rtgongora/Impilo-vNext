package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.domain.ReconcileStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ReconcileQueueEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.ReconcileQueueRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.StockMovementRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Manages stock reconciliation for hybrid eLMIS integration mode.
 *
 * <p>When the pharmacy operates in HYBRID mode, stock is tracked both
 * internally and by the external eLMIS. Discrepancies appear as entries
 * in the reconciliation queue with a computed confidence score indicating
 * how closely the internal and external quantities match.</p>
 *
 * <p>Operators review pending entries and either resolve them (accepting
 * the discrepancy) or reject them for further investigation.</p>
 */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    private final ReconcileQueueRepository reconcileRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReconciliationServiceImpl(ReconcileQueueRepository reconcileRepository,
                                     StockMovementRepository stockMovementRepository,
                                     EventOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.reconcileRepository = reconcileRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<ReconcileQueueEntity> getPendingReconciliations(Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        return reconcileRepository.findByTenantIdAndStatus(
                ctx.tenantId(), ReconcileStatus.PENDING, pageable);
    }

    @Override
    @Transactional
    public ReconcileQueueEntity resolveReconciliation(UUID recId, String notes) {
        TrustContext ctx = TrustContextHolder.require();

        ReconcileQueueEntity entry = reconcileRepository.findById(recId)
                .orElseThrow(() -> new IllegalArgumentException("Reconcile entry not found: " + recId));

        if (entry.getStatus() != ReconcileStatus.PENDING) {
            throw new IllegalStateException("Cannot resolve entry in status " + entry.getStatus());
        }

        entry.setStatus(ReconcileStatus.RESOLVED);
        entry.setOpsNotes(notes);
        entry.setResolvedBy(ctx.actorId());
        entry.setResolvedAt(OffsetDateTime.now());
        entry = reconcileRepository.save(entry);

        publishEvent("RECONCILE", recId.toString(),
                "RECONCILE_RESOLVED", entry, ctx.tenantId());

        log.info("Reconcile resolved: recId={}, actor={}", recId, ctx.actorId());
        return entry;
    }

    /**
     * Create a new reconciliation queue entry comparing internal vs external quantities.
     *
     * <p>Calculates a confidence score based on how closely the quantities
     * match: 1.0 for exact match, decreasing as the difference grows.
     * Formula: 1 - (|internal - external| / max(internal, external)).</p>
     *
     * @param facilityId  the facility
     * @param itemCode    the item code
     * @param internalQty the internal stock quantity
     * @param externalQty the external (eLMIS) stock quantity
     * @return the created reconciliation entry
     */
    @Transactional
    public ReconcileQueueEntity createReconcileEntry(UUID facilityId, String itemCode,
                                                      int internalQty, int externalQty) {
        TrustContext ctx = TrustContextHolder.require();

        // Calculate confidence score
        BigDecimal confidence;
        if (internalQty == externalQty) {
            confidence = BigDecimal.ONE;
        } else {
            int maxQty = Math.max(Math.abs(internalQty), Math.abs(externalQty));
            if (maxQty == 0) {
                confidence = BigDecimal.ONE;
            } else {
                int diff = Math.abs(internalQty - externalQty);
                confidence = BigDecimal.ONE.subtract(
                        BigDecimal.valueOf(diff).divide(BigDecimal.valueOf(maxQty), 4, RoundingMode.HALF_UP));
                if (confidence.compareTo(BigDecimal.ZERO) < 0) {
                    confidence = BigDecimal.ZERO;
                }
            }
        }

        ReconcileQueueEntity entry = new ReconcileQueueEntity();
        entry.setTenantId(ctx.tenantId());
        entry.setFacilityId(facilityId);
        entry.setRecType("STOCK");
        entry.setStatus(ReconcileStatus.PENDING);
        entry.setItemCode(itemCode);
        entry.setInternalQty(internalQty);
        entry.setExternalQty(externalQty);
        entry.setConfidence(confidence);

        entry = reconcileRepository.save(entry);

        log.info("Reconcile entry created: recId={}, itemCode={}, internal={}, external={}, confidence={}",
                entry.getRecId(), itemCode, internalQty, externalQty, confidence);
        return entry;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

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
