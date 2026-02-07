package zw.gov.mohcc.impilo.pharmacy.core;

import zw.gov.mohcc.impilo.pharmacy.domain.MovementType;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.StockMovementEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing stock movements and FEFO (First-Expiry-First-Out) logic.
 *
 * <p>Provides stock balance queries, movement recording, FEFO batch
 * suggestions for picking, and movement reversal for returns/wastage.</p>
 */
public interface StockLedgerService {

    /**
     * Record a stock movement in the ledger.
     */
    StockMovementEntity recordMovement(UUID facilityId, String storeId, String itemCode,
                                        String batchNumber, LocalDate expiryDate,
                                        int qtyDelta, MovementType type, String reason,
                                        String refType, String refId);

    /**
     * Get the current stock balance for an item at a facility/store.
     */
    int getStockBalance(UUID facilityId, String storeId, String itemCode);

    /**
     * Suggest batches to pick using FEFO ordering for a given quantity.
     */
    List<FefoSuggestion> suggestFefo(UUID facilityId, String storeId, String itemCode, int qtyNeeded);

    /**
     * Reverse all movements associated with a reference (e.g. for returns).
     */
    List<StockMovementEntity> reverseMovements(String refType, String refId, String reason);

    /**
     * Data carrier for FEFO batch suggestions.
     */
    record FefoSuggestion(
            String batchNumber,
            LocalDate expiryDate,
            int availableQty,
            int suggestedQty
    ) {}
}
