package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * Classification of stock movements ({@code rx_stock_movements.movement_type}).
 *
 * <p>Each stock movement records a positive or negative quantity delta
 * against a specific item/batch/bin combination. The movement type
 * indicates the business reason for the change.</p>
 */
public enum MovementType {

    /** Stock decreased due to dispensing to a patient. */
    DISPENSE,

    /** Stock increased due to receipt from eLMIS or manual entry. */
    RECEIPT,

    /** Stock increased due to patient return of dispensed items. */
    RETURN,

    /** Stock decreased due to expiry, breakage, or contamination. */
    WASTAGE,

    /** Stock adjusted (up or down) from a stock count or correction. */
    ADJUSTMENT,

    /** Stock increased due to reversal of a prior dispense. */
    REVERSAL,

    /** Stock moved between stores or facilities. */
    TRANSFER
}
