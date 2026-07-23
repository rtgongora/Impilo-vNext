package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.persistence.entity.StockReservationEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dura stock reservation service.
 *
 * <p>Reservations are soft holds that reduce <em>available</em> stock
 * (available = on-hand − active reservations) without mutating the on-hand
 * ledger projection. Fulfilment (CONSUMED) is recorded separately from the
 * actual ledger ISSUE posted by the consuming workflow.</p>
 *
 * <p>All operations are tenant-scoped via the trust context.</p>
 */
public interface ReservationService {

    /**
     * Reserve stock for a workflow. Fails if available quantity is insufficient.
     *
     * @param facilityId facility holding the stock
     * @param storeId    store within the facility
     * @param itemCode   commodity code
     * @param batchLotId optional specific batch ({@code null} = any)
     * @param qty        quantity to hold (must be positive)
     * @param uom        unit of measure
     * @param refType    reference type (e.g. DISPENSE, PROCEDURE, TRANSFER, ORDER)
     * @param refId      reference identifier
     * @param reason     optional reason / note
     * @param expiresAt  optional auto-expiry timestamp
     */
    StockReservationEntity reserve(UUID facilityId, UUID storeId, String itemCode, UUID batchLotId,
                                   int qty, String uom, String refType, String refId,
                                   String reason, OffsetDateTime expiresAt);

    /**
     * Release an active reservation back to available stock (no consumption).
     */
    StockReservationEntity release(UUID reservationId);

    /**
     * Mark an active reservation as fulfilled (CONSUMED). The caller is
     * responsible for posting the corresponding ledger ISSUE event.
     */
    StockReservationEntity consume(UUID reservationId);

    /**
     * List active reservations for an item at a store.
     */
    List<StockReservationEntity> listActive(UUID facilityId, UUID storeId, String itemCode);

    /**
     * Total quantity held by active reservations for an item at a store.
     */
    int reservedQuantity(UUID facilityId, UUID storeId, String itemCode);

    /**
     * Available quantity = on-hand − active reservations.
     */
    int availableQuantity(UUID facilityId, UUID storeId, String itemCode);

    /**
     * System expiry sweep (OF-B11 / §8.9.3 fail-close): flip every ACTIVE
     * reservation whose {@code expiresAt} has lapsed to EXPIRED and emit the
     * reservation-expired events. Runs without a trust context (scheduled).
     *
     * @param now the sweep instant
     * @return number of reservations expired
     */
    int expireLapsed(OffsetDateTime now);
}
