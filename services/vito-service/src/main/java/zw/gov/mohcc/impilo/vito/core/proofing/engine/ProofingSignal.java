package zw.gov.mohcc.impilo.vito.core.proofing.engine;

/**
 * The outcome of a single proofing capability run (document-verify, facial-compare,
 * liveness). Kept deliberately distinct from a biometric {@code VerificationDecision}
 * so the doctrine §6 capability separation is visible in the type system: a document
 * check, a face comparison, and a liveness probe are different signals that an
 * orchestrator composes — none of them alone binds an identity.
 *
 * @param capability the capability that produced this signal (e.g. DOCUMENT_INTEGRITY)
 * @param result     one of {@link #VERIFIED}, {@link #REJECTED}, {@link #INCONCLUSIVE},
 *                   {@link #UNAVAILABLE}, {@link #INVALID_INPUT}
 * @param confidence 0.0–1.0; always 0.0 for non-VERIFIED outcomes
 * @param summary    audit-friendly, PII-free explanation
 */
public record ProofingSignal(String capability, String result, double confidence, String summary) {

    /** The capability positively confirmed the evidence. */
    public static final String VERIFIED = "VERIFIED";
    /** The capability actively disproved the evidence (e.g. a failed check digit). */
    public static final String REJECTED = "REJECTED";
    /** The capability could not decide — route to a higher-assurance / assisted path. */
    public static final String INCONCLUSIVE = "INCONCLUSIVE";
    /** No engine is configured for this capability (fail-closed default). */
    public static final String UNAVAILABLE = "UNAVAILABLE";
    /** The supplied evidence was missing or malformed. */
    public static final String INVALID_INPUT = "INVALID_INPUT";

    public static ProofingSignal verified(String capability, double confidence, String summary) {
        return new ProofingSignal(capability, VERIFIED, confidence, summary);
    }

    public static ProofingSignal rejected(String capability, String summary) {
        return new ProofingSignal(capability, REJECTED, 0.0, summary);
    }

    public static ProofingSignal inconclusive(String capability, String summary) {
        return new ProofingSignal(capability, INCONCLUSIVE, 0.0, summary);
    }

    public static ProofingSignal unavailable(String capability, String summary) {
        return new ProofingSignal(capability, UNAVAILABLE, 0.0, summary);
    }

    public static ProofingSignal invalidInput(String capability, String summary) {
        return new ProofingSignal(capability, INVALID_INPUT, 0.0, summary);
    }

    public boolean isVerified() {
        return VERIFIED.equals(result);
    }

    public boolean isRejected() {
        return REJECTED.equals(result);
    }
}
