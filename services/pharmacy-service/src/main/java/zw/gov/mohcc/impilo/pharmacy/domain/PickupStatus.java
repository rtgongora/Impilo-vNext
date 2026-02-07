package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * Lifecycle states for a pickup proof ({@code rx_pickup_proofs.status}).
 *
 * <p>Tracks whether the medication has been collected by the patient
 * or their authorized delegate.</p>
 */
public enum PickupStatus {

    /** Pickup proof created; awaiting patient/delegate claim. */
    PENDING,

    /** Medication successfully collected. Terminal state. */
    CLAIMED,

    /** Pickup proof expired without being claimed. Terminal state. */
    EXPIRED,

    /** Pickup proof cancelled (e.g. order reversed). Terminal state. */
    CANCELLED
}
