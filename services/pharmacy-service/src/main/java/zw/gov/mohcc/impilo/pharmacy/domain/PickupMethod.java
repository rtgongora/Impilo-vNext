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
    QR,

    /**
     * Live biometric identity match confirmed at collection through the shared
     * biometric seam. Recorded on the proof when a probe MATCHes at claim time.
     */
    BIOMETRIC,

    /**
     * OF-B16 named-recipient/no-smartphone path: an opaque short reference is
     * sent by SMS (never the token); the reference is resolved server-side and
     * staff verify the named recipient's identity at handover (§8.11.3).
     */
    SMS_REFERENCE
}
