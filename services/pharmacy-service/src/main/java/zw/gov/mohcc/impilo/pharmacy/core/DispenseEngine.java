package zw.gov.mohcc.impilo.pharmacy.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.pharmacy.api.dto.PickItemData;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;

import java.util.List;
import java.util.UUID;

/**
 * Core dispense engine managing the pharmacy order lifecycle.
 *
 * <p>Orchestrates state transitions from order receipt through
 * acceptance, picking, partial/full dispense, backorder, and cancellation.
 * Each transition records stock movements and publishes outbox events.</p>
 */
public interface DispenseEngine {

    /**
     * Create a pharmacy dispense order from an incoming OROS order.
     */
    DispenseOrderEntity createFromOrosOrder(String orosOrderId, String patientCpid,
                                             UUID facilityId, String prescriberNotes,
                                             List<DispenseEngine.OrderItemData> items);

    /**
     * Accept a pending dispense order, assigning it to the current pharmacist.
     */
    DispenseOrderEntity acceptOrder(UUID orderId);

    /**
     * Pick items from shelves for a dispense order.
     */
    DispenseOrderEntity pickItems(UUID orderId, List<PickItemData> items);

    /**
     * Complete a partial dispense (some items dispensed, others outstanding).
     */
    DispenseOrderEntity partialDispense(UUID orderId);

    /**
     * Complete the full dispense, recording all stock movements.
     */
    DispenseOrderEntity completeDispense(UUID orderId);

    /**
     * Place the order on backorder with operational notes.
     */
    DispenseOrderEntity backorder(UUID orderId, String notes);

    /**
     * Cancel a dispense order.
     */
    DispenseOrderEntity cancelOrder(UUID orderId);

    /**
     * Get a single dispense order by its ID.
     */
    DispenseOrderEntity getOrder(UUID orderId);

    /**
     * Get all dispense orders for a patient by CPID.
     */
    List<DispenseOrderEntity> getPatientOrders(String cpid);

    /**
     * Data carrier for order items during creation.
     */
    record OrderItemData(
            String drugCode,
            String drugDisplay,
            int qtyRequested,
            String instructions
    ) {}
}
