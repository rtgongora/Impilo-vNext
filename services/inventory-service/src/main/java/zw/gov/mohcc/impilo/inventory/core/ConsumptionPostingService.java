package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for posting consumption events from downstream clinical systems.
 *
 * <p>All consumption methods delegate to {@link LedgerService#postEvent} with
 * appropriate event types and reference metadata. Provides typed entry points
 * for pharmacy, laboratory, and generic clinical consumption.</p>
 */
public interface ConsumptionPostingService {

    /**
     * Post a pharmacy consumption event (dispensing).
     *
     * @param facilityId the facility identifier
     * @param storeId    the pharmacy store identifier
     * @param itemCode   the dispensed item code
     * @param batch      the batch number
     * @param expiry     the expiry date
     * @param qty        the quantity dispensed (positive value)
     * @param orderId    the pharmacy/OROS order identifier
     * @return the created ledger event
     */
    LedgerEventEntity postPharmacyConsumption(UUID facilityId, UUID storeId, String itemCode,
                                                String batch, LocalDate expiry, int qty, String orderId);

    /**
     * Post a laboratory consumption event.
     *
     * @param facilityId the facility identifier
     * @param storeId    the lab store identifier
     * @param itemCode   the consumed item code
     * @param qty        the quantity consumed (positive value)
     * @param orderId    the lab/OROS order identifier
     * @return the created ledger event
     */
    LedgerEventEntity postLabConsumption(UUID facilityId, UUID storeId, String itemCode,
                                          int qty, String orderId);

    /**
     * Post a generic clinical consumption event.
     *
     * @param facilityId the facility identifier
     * @param storeId    the store identifier
     * @param itemCode   the consumed item code
     * @param qty        the quantity consumed (positive value)
     * @param refType    the reference type (e.g. WARD, THEATRE, PROCEDURE)
     * @param refId      the reference identifier
     * @return the created ledger event
     */
    LedgerEventEntity postClinicalConsumption(UUID facilityId, UUID storeId, String itemCode,
                                                int qty, String refType, String refId);
}
