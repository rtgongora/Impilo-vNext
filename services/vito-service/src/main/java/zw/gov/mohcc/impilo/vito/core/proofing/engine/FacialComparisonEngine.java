package zw.gov.mohcc.impilo.vito.core.proofing.engine;

import org.springframework.stereotype.Component;

/**
 * Pluggable 1:1 facial-comparison capability (doctrine §6, distinct from document-verify,
 * liveness, and 1:N ABIS identification): does the live selfie match the face on the
 * presented/enrolled document?
 *
 * <p>Face comparison requires a trained model; there is no honest deterministic native
 * implementation, so the default {@link FailClosed} fails closed — it never fabricates a
 * match and returns {@link ProofingSignal#UNAVAILABLE}, forcing the orchestrator onto an
 * assisted / in-person path until a vendor engine registers as {@code @Primary}.</p>
 */
public interface FacialComparisonEngine {

    String CAPABILITY = "FACIAL_COMPARISON";

    /**
     * @param selfieRef   document-service reference to the captured live selfie
     * @param documentFaceRef reference to the face extracted from the document / enrolled portrait
     */
    ProofingSignal compare(String selfieRef, String documentFaceRef);

    @Component
    class FailClosed implements FacialComparisonEngine {
        @Override
        public ProofingSignal compare(String selfieRef, String documentFaceRef) {
            if (selfieRef == null || documentFaceRef == null) {
                return ProofingSignal.invalidInput(CAPABILITY, "Missing selfie or document-face reference");
            }
            return ProofingSignal.unavailable(CAPABILITY,
                    "No facial-comparison engine configured — fails closed (no fabricated match). "
                            + "Register a @Primary FacialComparisonEngine to enable; meanwhile route to assisted/in-person.");
        }
    }
}
