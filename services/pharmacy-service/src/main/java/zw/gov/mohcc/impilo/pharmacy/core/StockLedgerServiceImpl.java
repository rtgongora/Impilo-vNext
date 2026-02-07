package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.domain.MovementType;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.StockMovementEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.StockMovementRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages stock positions through an immutable movement ledger.
 *
 * <p>All stock changes are recorded as movement entries with a signed
 * {@code qtyDelta} (positive = stock in, negative = stock out). The
 * current balance for any item is the sum of all deltas for that
 * facility/store/item combination.</p>
 *
 * <p>Provides FEFO (First-Expiry-First-Out) pick suggestions by
 * analyzing batch-level balances and sorting by ascending expiry date.</p>
 */
@Service
public class StockLedgerServiceImpl implements StockLedgerService {

    private static final Logger log = LoggerFactory.getLogger(StockLedgerServiceImpl.class);

    private final StockMovementRepository movementRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public StockLedgerServiceImpl(StockMovementRepository movementRepository,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.movementRepository = movementRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StockMovementEntity recordMovement(UUID facilityId, String storeId, String itemCode,
                                               String batchNumber, LocalDate expiryDate,
                                               int qtyDelta, MovementType type, String reason,
                                               String refType, String refId) {
        TrustContext ctx = TrustContextHolder.require();

        StockMovementEntity movement = new StockMovementEntity();
        movement.setTenantId(ctx.tenantId());
        movement.setFacilityId(facilityId);
        movement.setStoreId(storeId);
        movement.setItemCode(itemCode);
        movement.setBatchNumber(batchNumber);
        movement.setExpiryDate(expiryDate);
        movement.setQtyDelta(qtyDelta);
        movement.setMovementType(type);
        movement.setReason(reason);
        movement.setRefType(refType);
        movement.setRefId(refId);
        movement.setRecordedBy(ctx.actorId());

        movement = movementRepository.save(movement);

        publishEvent("STOCK", movement.getMovementId().toString(),
                "STOCK_MOVEMENT_RECORDED", movement, ctx.tenantId());

        log.info("Stock movement recorded: facilityId={}, storeId={}, itemCode={}, qty={}, type={}, ref={}/{}",
                facilityId, storeId, itemCode, qtyDelta, type, refType, refId);
        return movement;
    }

    @Override
    public int getStockBalance(UUID facilityId, String storeId, String itemCode) {
        return movementRepository.getStockBalance(facilityId, storeId, itemCode);
    }

    /**
     * Get stock balance for a specific batch.
     *
     * @param facilityId the facility
     * @param storeId    the store within the facility
     * @param itemCode   the item code
     * @param batch      the batch number
     * @return the current batch-level balance
     */
    public int getStockBalanceByBatch(UUID facilityId, String storeId, String itemCode, String batch) {
        return movementRepository.getStockBalanceByBatch(facilityId, storeId, itemCode, batch);
    }

    @Override
    public List<FefoSuggestion> suggestFefo(UUID facilityId, String storeId,
                                             String itemCode, int qtyNeeded) {
        // Fetch all movements for this item at this location
        List<StockMovementEntity> movements =
                movementRepository.findByFacilityIdAndStoreIdAndItemCodeOrderByCreatedAtDesc(
                        facilityId, storeId, itemCode);

        // Group by batch and compute balance + expiry per batch
        Map<String, BatchInfo> batchMap = new LinkedHashMap<>();
        for (StockMovementEntity m : movements) {
            String batch = m.getBatchNumber();
            if (batch == null) {
                continue;
            }
            batchMap.computeIfAbsent(batch, k -> new BatchInfo(m.getExpiryDate()))
                    .addDelta(m.getQtyDelta());
        }

        // Filter: positive balance, not expired
        LocalDate today = LocalDate.now();
        List<Map.Entry<String, BatchInfo>> validBatches = batchMap.entrySet().stream()
                .filter(e -> e.getValue().balance > 0)
                .filter(e -> e.getValue().expiryDate == null || !e.getValue().expiryDate.isBefore(today))
                .sorted(Comparator.comparing(
                        e -> e.getValue().expiryDate != null ? e.getValue().expiryDate : LocalDate.MAX))
                .collect(Collectors.toList());

        // Allocate from earliest expiry first (FEFO)
        List<FefoSuggestion> suggestions = new ArrayList<>();
        int remaining = qtyNeeded;

        for (Map.Entry<String, BatchInfo> entry : validBatches) {
            if (remaining <= 0) {
                break;
            }

            String batch = entry.getKey();
            BatchInfo info = entry.getValue();
            int suggested = Math.min(remaining, info.balance);

            suggestions.add(new FefoSuggestion(batch, info.expiryDate, info.balance, suggested));
            remaining -= suggested;
        }

        log.debug("FEFO suggestion: itemCode={}, qtyNeeded={}, batches={}, remaining={}",
                itemCode, qtyNeeded, suggestions.size(), remaining);
        return suggestions;
    }

    /**
     * Get the movement history for an item at a facility/store.
     *
     * @param facilityId the facility
     * @param storeId    the store
     * @param itemCode   the item code
     * @return list of movements ordered by creation time descending
     */
    public List<StockMovementEntity> getMovements(UUID facilityId, String storeId, String itemCode) {
        return movementRepository.findByFacilityIdAndStoreIdAndItemCodeOrderByCreatedAtDesc(
                facilityId, storeId, itemCode);
    }

    @Override
    @Transactional
    public List<StockMovementEntity> reverseMovements(String refType, String refId, String reason) {
        TrustContext ctx = TrustContextHolder.require();

        List<StockMovementEntity> originals = movementRepository.findByRefTypeAndRefId(refType, refId);
        if (originals.isEmpty()) {
            log.warn("No movements found to reverse for ref={}/{}", refType, refId);
            return List.of();
        }

        List<StockMovementEntity> reversals = new ArrayList<>();
        for (StockMovementEntity original : originals) {
            StockMovementEntity reversal = new StockMovementEntity();
            reversal.setTenantId(original.getTenantId());
            reversal.setFacilityId(original.getFacilityId());
            reversal.setStoreId(original.getStoreId());
            reversal.setBinId(original.getBinId());
            reversal.setItemCode(original.getItemCode());
            reversal.setItemDisplay(original.getItemDisplay());
            reversal.setBatchNumber(original.getBatchNumber());
            reversal.setExpiryDate(original.getExpiryDate());
            reversal.setQtyDelta(-original.getQtyDelta());
            reversal.setMovementType(MovementType.ADJUSTMENT);
            reversal.setReason("REVERSAL: " + reason);
            reversal.setRefType("REVERSAL");
            reversal.setRefId(original.getMovementId().toString());
            reversal.setRecordedBy(ctx.actorId());

            reversal = movementRepository.save(reversal);
            reversals.add(reversal);
        }

        publishEvent("STOCK", refId, "STOCK_REVERSAL_RECORDED",
                Map.of("refType", refType, "refId", refId, "reversalCount", reversals.size()),
                ctx.tenantId());

        log.info("Stock movements reversed: ref={}/{}, count={}", refType, refId, reversals.size());
        return reversals;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Mutable holder for batch-level aggregation during FEFO calculation.
     */
    private static class BatchInfo {
        final LocalDate expiryDate;
        int balance;

        BatchInfo(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
            this.balance = 0;
        }

        void addDelta(int delta) {
            this.balance += delta;
        }
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
