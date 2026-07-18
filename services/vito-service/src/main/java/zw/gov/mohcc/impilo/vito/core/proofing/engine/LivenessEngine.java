package zw.gov.mohcc.impilo.vito.core.proofing.engine;

import org.springframework.stereotype.Component;

/**
 * Pluggable presentation-attack-detection / liveness capability (doctrine §6, distinct
 * from facial-comparison): is the capture a live person, not a photo, mask, or replay?
 *
 * <p>Liveness needs a trained model; the default {@link FailClosed} fails closed and
 * returns {@link ProofingSignal#UNAVAILABLE} so a remote unattended capture can never
 * self-certify liveness — the orchestrator must fall back to a witnessed (video/in-person)
 * path until a vendor engine registers as {@code @Primary}.</p>
 */
public interface LivenessEngine {

    String CAPABILITY = "LIVENESS";

    /**
     * @param captureRef document-service reference to the liveness capture (video / active-challenge frames)
     */
    ProofingSignal assess(String captureRef);

    @Component
    class FailClosed implements LivenessEngine {
        @Override
        public ProofingSignal assess(String captureRef) {
            if (captureRef == null) {
                return ProofingSignal.invalidInput(CAPABILITY, "Missing liveness capture reference");
            }
            return ProofingSignal.unavailable(CAPABILITY,
                    "No liveness engine configured — fails closed (unattended capture cannot self-certify liveness). "
                            + "Register a @Primary LivenessEngine to enable; meanwhile require a witnessed path.");
        }
    }
}
