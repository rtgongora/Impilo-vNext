package zw.gov.mohcc.impilo.clinical.reasoning;

/**
 * Strategy for turning a fully-assembled {@link ReasoningRequest} into a {@link ReasoningResult}.
 * Two implementations: a fail-closed {@link DeterministicClinicalReasoner} (always available) and an
 * {@code LlmClinicalReasoner} (active only when the governed LLM is enabled and reachable). The LLM
 * impl always falls back to the deterministic one on any failure, so the deterministic path is the
 * permanent honest floor.
 */
public interface ClinicalReasoner {

    ReasoningResult reason(ReasoningRequest request);

    /** True only for a real LLM-backed reasoner (observability/labelling). */
    boolean isLlmBacked();
}
