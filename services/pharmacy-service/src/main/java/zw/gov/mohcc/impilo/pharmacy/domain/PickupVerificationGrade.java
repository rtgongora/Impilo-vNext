package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * OF-B16 — the proof-ladder grade a collection was verified at
 * ({@code rx_pickup_proofs.verification_grade}, spec §12.4/§8.11.3).
 *
 * <p>Always assigned server-side at claim time — never accepted from the
 * client. Higher grades subsume lower ones for policy checks.</p>
 */
public enum PickupVerificationGrade {

    /** OTP/QR token matched — the baseline single-factor grade. */
    TOKEN_MATCH,

    /** Token matched AND the collection was a recorded delegate collection. */
    DELEGATE_TOKEN_MATCH,

    /** Live biometric MATCH of the collector through the shared biometric seam. */
    BIOMETRIC_MATCH,

    /**
     * Named-recipient/SMS-reference path: opaque SMS reference resolved
     * server-side plus a staff identity check of the named recipient.
     */
    SMS_REFERENCE_ID_CHECK
}
