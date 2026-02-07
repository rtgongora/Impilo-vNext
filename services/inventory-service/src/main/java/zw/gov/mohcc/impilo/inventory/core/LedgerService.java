package zw.gov.mohcc.impilo.inventory.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Core append-only ledger engine for all inventory movements.
 *
 * <p>Every stock change (receipt, issue, transfer, adjustment, count, wastage, return)
 * is recorded as an immutable ledger event with a signed {@code qtyDelta}. The current
 * on-hand position is maintained as a denormalized projection that is updated atomically
 * with each event insertion.</p>
 *
 * <p>Idempotency is enforced via a unique key per event to prevent duplicate postings
 * from retried integrations or concurrent requests.</p>
 */
public interface LedgerService {

    /**
     * Post a new ledger event, update the on-hand projection, and publish an outbox event.
     *
     * <p>If an event with the same idempotency key already exists, the existing event
     * is returned without creating a duplicate.</p>
     *
     * @param data the event data to record
     * @return the persisted ledger event (new or existing if idempotent duplicate)
     */
    LedgerEventEntity postEvent(LedgerEventData data);

    /**
     * Query ledger events with optional filtering by facility, store, and item code.
     *
     * @param facilityId the facility identifier
     * @param storeId    the store within the facility
     * @param itemCode   the item code to filter on
     * @param pageable   pagination and sorting parameters
     * @return a page of matching ledger events
     */
    Page<LedgerEventEntity> getLedgerEvents(UUID facilityId, UUID storeId, String itemCode, Pageable pageable);

    /**
     * Get the current computed stock balance for an item at a specific facility and store.
     *
     * @param facilityId the facility identifier
     * @param storeId    the store within the facility
     * @param itemCode   the item code
     * @return the current balance (sum of all deltas)
     */
    int getBalance(UUID facilityId, UUID storeId, String itemCode);

    /**
     * Data carrier for creating a new ledger event.
     */
    record LedgerEventData(
            UUID facilityId,
            UUID storeId,
            UUID binId,
            String itemCode,
            String batch,
            LocalDate expiry,
            int qtyDelta,
            String uom,
            LedgerEventType eventType,
            String reason,
            String refType,
            String refId,
            String idempotencyKey
    ) {}
}
