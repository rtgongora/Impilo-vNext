package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * Specific reason codes for stock movements ({@code rx_stock_movements.reason}).
 *
 * <p>Provides a finer-grained explanation than {@link MovementType} alone,
 * enabling accurate audit trails and reconciliation with eLMIS.</p>
 */
public enum MovementReason {

    /** Dispense triggered by an OROS order. */
    ORDER_DISPENSE,

    /** Patient returned previously dispensed medication. */
    PATIENT_RETURN,

    /** Stock discarded due to expiry date reached. */
    EXPIRY_DISCARD,

    /** Stock lost due to physical breakage or damage. */
    BREAKAGE,

    /** Quantity adjusted following a physical stock count. */
    COUNT_ADJUSTMENT,

    /** Stock credited back after a dispense reversal. */
    REVERSAL_CREDIT,

    /** Stock moved between stores within or across facilities. */
    INTER_STORE_TRANSFER,

    /** Stock received from the external eLMIS system. */
    ELMIS_RECEIPT,

    /** Stock received via manual entry (no eLMIS reference). */
    MANUAL_RECEIPT
}
