package zw.gov.mohcc.impilo.nhume.core;

import zw.gov.mohcc.impilo.nhume.api.dto.ProofRequest;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.HandoverVerificationGrade;

import java.text.Normalizer;
import java.util.Locale;

/**
 * OF-B18 — pure decision logic for the §12.4 proof-of-handover grade ladder.
 *
 * <p>Fail-closed by construction:</p>
 * <ul>
 *   <li>an OTP grade can only be achieved against the server-held
 *       {@code handover_otp} — a missing stored OTP can never match;</li>
 *   <li>a delivery carrying a named recipient fails verification when the
 *       attested receiver name does not match, regardless of other factors;</li>
 *   <li>controlled cargo demands the second factor (OTP-or-verified-biometric
 *       <b>plus</b> ID check) — anything less refuses the handover;</li>
 *   <li>GPS/geofence is never consulted (CC-8 — corroboration, not proof).</li>
 * </ul>
 *
 * <p>Legacy deliveries that declare no grade contract (no required grade, not
 * controlled, no named-recipient flag) are UNGRADED: proofs record whatever
 * grade the artifacts achieve but are never refused, preserving the existing
 * courier flows.</p>
 */
public final class HandoverGradePolicy {

    /** Recorded grade for legacy proofs that achieve no ladder grade. */
    public static final String UNGRADED = "UNGRADED";

    public static final String FAIL_NAMED_RECIPIENT_MISMATCH = "NAMED_RECIPIENT_MISMATCH";
    public static final String FAIL_CONTROLLED_SECOND_FACTOR_MISSING = "CONTROLLED_SECOND_FACTOR_MISSING";
    public static final String FAIL_GRADE_BELOW_REQUIRED = "GRADE_BELOW_REQUIRED";

    private HandoverGradePolicy() {}

    /** The verdict for one handover attempt. */
    public record Verdict(boolean pass,
                          HandoverVerificationGrade achievedGrade,
                          Boolean namedRecipientMatch,
                          String failureReason) {

        public String achievedGradeName() {
            return achievedGrade != null ? achievedGrade.name() : UNGRADED;
        }
    }

    /** Whether this delivery declares any graded-handover contract at all. */
    public static boolean requiresGradedHandover(DeliveryRequestEntity d) {
        return d.getRequiredPodGrade() != null
                || d.isControlledItem()
                || d.isNamedRecipientRequired();
    }

    /**
     * The grade this delivery's order class demands: an explicit
     * {@code required_pod_grade} wins; controlled cargo hard-floors at
     * {@link HandoverVerificationGrade#OTP_PLUS_ID} (§13.4 second factor);
     * a named recipient floors at NAMED_RECIPIENT_MATCH; otherwise
     * SIGNATURE_ONLY.
     */
    public static HandoverVerificationGrade requiredGrade(DeliveryRequestEntity d) {
        HandoverVerificationGrade explicit = HandoverVerificationGrade.parse(d.getRequiredPodGrade());
        HandoverVerificationGrade floor;
        if (d.isControlledItem()) {
            floor = HandoverVerificationGrade.OTP_PLUS_ID;
        } else if (d.isNamedRecipientRequired()) {
            floor = HandoverVerificationGrade.NAMED_RECIPIENT_MATCH;
        } else {
            floor = HandoverVerificationGrade.SIGNATURE_ONLY;
        }
        if (explicit == null) {
            return floor;
        }
        return explicit.rank() >= floor.rank() ? explicit : floor;
    }

    /**
     * Evaluate one handover attempt.
     *
     * @param biometricVerified TRUE when a live biometric recipient match was
     *                          already verified upstream (substitutes for the
     *                          OTP factor, never for the ID attestation)
     */
    public static Verdict evaluate(DeliveryRequestEntity d, ProofRequest req, boolean biometricVerified) {
        boolean otpMatched = d.getHandoverOtp() != null && !d.getHandoverOtp().isBlank()
                && req.otpCode() != null && d.getHandoverOtp().equals(req.otpCode().trim());
        boolean idChecked = notBlank(req.idDocumentType()) && notBlank(req.idDocumentRef());
        boolean signature = notBlank(req.signatureUri());
        Boolean namedMatch = null;
        if (notBlank(d.getRecipientName()) && notBlank(req.receiverName())) {
            namedMatch = normalize(d.getRecipientName()).equals(normalize(req.receiverName()));
        }

        HandoverVerificationGrade achieved = achievedGrade(
                otpMatched || biometricVerified, idChecked, Boolean.TRUE.equals(namedMatch), signature);

        if (!requiresGradedHandover(d)) {
            // Legacy ungraded class — record, never refuse.
            return new Verdict(true, achieved, namedMatch, null);
        }

        // Named recipient is a hard, non-substitutable check (fail-closed).
        if (d.isNamedRecipientRequired() && !Boolean.TRUE.equals(namedMatch)) {
            return new Verdict(false, achieved, namedMatch, FAIL_NAMED_RECIPIENT_MISMATCH);
        }
        // Controlled second factor: (OTP match or verified biometric) AND ID check.
        if (d.isControlledItem() && !((otpMatched || biometricVerified) && idChecked)) {
            return new Verdict(false, achieved, namedMatch, FAIL_CONTROLLED_SECOND_FACTOR_MISSING);
        }
        HandoverVerificationGrade required = requiredGrade(d);
        if (achieved == null || !achieved.meets(required)) {
            return new Verdict(false, achieved, namedMatch, FAIL_GRADE_BELOW_REQUIRED);
        }
        return new Verdict(true, achieved, namedMatch, null);
    }

    private static HandoverVerificationGrade achievedGrade(boolean otpFactor, boolean idChecked,
                                                           boolean namedMatch, boolean signature) {
        if (otpFactor && idChecked) return HandoverVerificationGrade.OTP_PLUS_ID;
        if (otpFactor) return HandoverVerificationGrade.OTP_MATCH;
        if (idChecked) return HandoverVerificationGrade.ID_CHECK;
        if (namedMatch) return HandoverVerificationGrade.NAMED_RECIPIENT_MATCH;
        if (signature) return HandoverVerificationGrade.SIGNATURE_ONLY;
        return null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Case/diacritic/whitespace-insensitive name normalization. */
    static String normalize(String name) {
        String n = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return n;
    }
}
