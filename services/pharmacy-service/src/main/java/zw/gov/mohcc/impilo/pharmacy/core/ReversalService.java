package zw.gov.mohcc.impilo.pharmacy.core;

import zw.gov.mohcc.impilo.pharmacy.api.dto.ReturnItemData;
import zw.gov.mohcc.impilo.pharmacy.api.dto.WastageItemData;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;

import java.util.List;
import java.util.UUID;

/**
 * Service processing returns, wastage, and full reversals of dispense orders.
 *
 * <p>Handles the reverse logistics of the pharmacy workflow including
 * patient returns, wastage recording, and full order reversal with
 * corresponding stock movement adjustments.</p>
 */
public interface ReversalService {

    /**
     * Process a patient return of dispensed items.
     */
    DispenseOrderEntity processReturn(UUID orderId, List<ReturnItemData> items);

    /**
     * Process wastage of dispensed items.
     */
    DispenseOrderEntity processWastage(UUID orderId, List<WastageItemData> items);

    /**
     * Process a full reversal of a dispense order.
     */
    DispenseOrderEntity processReversal(UUID orderId, String reason);
}
