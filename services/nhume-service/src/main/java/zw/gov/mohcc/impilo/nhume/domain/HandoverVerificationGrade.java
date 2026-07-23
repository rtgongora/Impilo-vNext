package zw.gov.mohcc.impilo.nhume.domain;

/**
 * OF-B18 — the proof-of-handover grade ladder (§12.4), ranked weakest to
 * strongest. A handover passes only when the achieved grade {@link #meets}
 * the delivery's required grade; GPS proximity is NEVER a grade (CC-8 —
 * location corroborates, it never proves).
 *
 * <p>Controlled cargo demands the second-factor grade {@link #OTP_PLUS_ID}
 * (OTP against the server-held handover OTP <b>plus</b> a recorded ID check);
 * a verified live-biometric recipient match substitutes for the OTP factor
 * (it is a strictly stronger identity fact), never for the ID attestation.</p>
 */
public enum HandoverVerificationGrade {

    /** Captured signature only — low-risk classes, delegate receipt. */
    SIGNATURE_ONLY(1),

    /** Receiver name matches the delivery's named recipient (normalized). */
    NAMED_RECIPIENT_MATCH(2),

    /** Courier-recorded identity-document check (type + document ref). */
    ID_CHECK(3),

    /** One-time code matched against the server-held handover OTP. */
    OTP_MATCH(4),

    /** Controlled second factor: OTP (or verified biometric) AND ID check. */
    OTP_PLUS_ID(5);

    private final int rank;

    HandoverVerificationGrade(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean meets(HandoverVerificationGrade required) {
        return required == null || this.rank >= required.rank;
    }

    /** Lenient parse — unknown/blank values are NULL (caller decides posture). */
    public static HandoverVerificationGrade parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
