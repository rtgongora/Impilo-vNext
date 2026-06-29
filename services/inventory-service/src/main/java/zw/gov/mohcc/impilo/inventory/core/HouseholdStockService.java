package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.domain.HomeStockSource;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockStatus;
import zw.gov.mohcc.impilo.inventory.domain.RefillStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ClientHomeStockEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RefillRequestEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dura client/caregiver household stock service.
 *
 * <p>Tracks medicines and supplies held at home and the refill-request
 * lifecycle. Household stock is personal health data; access for a client or an
 * authorised caregiver is governed by Tshepo/Vito at the gateway. This service
 * is tenant-scoped and records the client context.</p>
 */
public interface HouseholdStockService {

    ClientHomeStockEntity addHomeStock(String clientId, String itemCode, String itemName, int qty,
                                       String uom, String batchNumber, LocalDate expiryDate,
                                       HomeStockSource source, String managedByCaregiver, String notes);

    List<ClientHomeStockEntity> listHomeStock(String clientId);

    /**
     * Update the held quantity; quantity of 0 marks the item DEPLETED.
     */
    ClientHomeStockEntity updateQuantity(UUID homeStockId, int newQty);

    ClientHomeStockEntity setHomeStockStatus(UUID homeStockId, HomeStockStatus status);

    RefillRequestEntity requestRefill(String clientId, String itemCode, String itemName,
                                      int qtyRequested, UUID preferredFacilityId, String notes);

    List<RefillRequestEntity> listRefills(String clientId, RefillStatus status);

    /**
     * Update a refill request's status; FULFILLED stamps the fulfilment time.
     */
    RefillRequestEntity updateRefillStatus(UUID refillId, RefillStatus status, String notes);
}
