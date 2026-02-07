package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Lifecycle states of an inventory requisition ({@code inv_requisitions.status}).
 *
 * <p>A requisition represents an internal request to move stock between
 * stores (inter-store transfer) or from a central warehouse. The lifecycle
 * progresses from draft through approval to fulfilment.</p>
 */
public enum RequisitionStatus {

    /** Requisition created but not yet submitted for approval. */
    DRAFT,

    /** Requisition submitted and awaiting authoriser review. */
    SUBMITTED,

    /** Requisition approved by an authorised reviewer. */
    APPROVED,

    /** Requisition declined by the authoriser. */
    REJECTED,

    /** Some (but not all) line items have been fulfilled. */
    PARTIALLY_FULFILLED,

    /** All line items have been fully fulfilled. */
    FULFILLED,

    /** Requisition withdrawn before fulfilment. */
    CANCELLED
}
