package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.persistence.entity.RequisitionEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing inter-store requisition workflows.
 *
 * <p>Implements the requisition lifecycle:
 * DRAFT -> SUBMITTED -> APPROVED / REJECTED.
 * APPROVED requisitions can be fulfilled (fully or partially),
 * triggering ledger events for stock movement between stores.</p>
 *
 * <p>If the source and destination stores are within the same facility,
 * TRANSFER_OUT/TRANSFER_IN events are used. For cross-facility movements,
 * ISSUE/RECEIPT events are posted instead.</p>
 */
public interface RequisitionService {

    /**
     * Create a new requisition in DRAFT status.
     *
     * @param fromStoreId the requesting (destination) store
     * @param toStoreId   the supplying (source) store
     * @param lines       the requested line items
     * @return the created requisition entity
     */
    RequisitionEntity createRequisition(UUID fromStoreId, UUID toStoreId, List<RequisitionLineData> lines);

    /**
     * Submit a draft requisition for approval.
     *
     * @param reqId the requisition identifier
     * @return the submitted requisition
     * @throws IllegalStateException if requisition is not in DRAFT status
     */
    RequisitionEntity submitRequisition(UUID reqId);

    /**
     * Approve a submitted requisition, setting approved quantities per line.
     *
     * @param reqId     the requisition identifier
     * @param approvals the approved quantities per line
     * @return the approved requisition
     * @throws IllegalStateException if requisition is not in SUBMITTED status
     */
    RequisitionEntity approveRequisition(UUID reqId, List<ApprovalLineData> approvals);

    /**
     * Reject a submitted requisition.
     *
     * @param reqId  the requisition identifier
     * @param reason the rejection reason
     * @return the rejected requisition
     * @throws IllegalStateException if requisition is not in SUBMITTED status
     */
    RequisitionEntity rejectRequisition(UUID reqId, String reason);

    /**
     * Fulfill an approved requisition (fully or partially).
     *
     * <p>Posts ledger events for stock movement: TRANSFER_OUT/TRANSFER_IN
     * for same-facility moves, ISSUE/RECEIPT for cross-facility moves.
     * Updates requisition status to PARTIALLY_FULFILLED or FULFILLED based
     * on whether all lines are fully satisfied.</p>
     *
     * @param reqId        the requisition identifier
     * @param fulfillments the fulfillment data per line
     * @return the updated requisition
     * @throws IllegalStateException if requisition is not in APPROVED or PARTIALLY_FULFILLED status
     */
    RequisitionEntity fulfillRequisition(UUID reqId, List<FulfillLineData> fulfillments);

    /**
     * Data carrier for a requisition line item request.
     */
    record RequisitionLineData(
            String itemCode,
            int qtyRequested,
            String notes
    ) {}

    /**
     * Data carrier for an approval decision on a line item.
     */
    record ApprovalLineData(
            UUID lineId,
            int qtyApproved
    ) {}

    /**
     * Data carrier for fulfillment of a line item.
     */
    record FulfillLineData(
            UUID lineId,
            int qtyFulfilled,
            String batch,
            LocalDate expiry
    ) {}
}
