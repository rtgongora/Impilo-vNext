package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * Methods for verifying medication pickup ({@code rx_pickup_proofs.method}).
 *
 * <p>Determines how the patient or delegate proves identity when
 * collecting dispensed medication.</p>
 */
public enum PickupMethod {

    /** One-time password sent to the patient's registered phone number. */
    OTP,

    /** QR code presented on the patient's device or printed receipt. */
    QR
}
