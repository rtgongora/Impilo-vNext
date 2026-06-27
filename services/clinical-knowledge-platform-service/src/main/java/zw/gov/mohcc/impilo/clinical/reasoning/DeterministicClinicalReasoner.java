package zw.gov.mohcc.impilo.clinical.reasoning;

/**
 * Fail-closed default reasoner. Produces a {@code DETERMINISTIC} marker result that tells
 * {@code ClinicalAssistantService} to keep its own deterministic (template + rules) composition —
 * the honest, no-fabrication floor that has always backed the assistant. Always available; also the
 * fallback collaborator inside {@code LlmClinicalReasoner}.
 */
public class DeterministicClinicalReasoner implements ClinicalReasoner {

    @Override
    public ReasoningResult reason(ReasoningRequest request) {
        return ReasoningResult.deterministic("deterministic-reasoner");
    }

    @Override
    public boolean isLlmBacked() {
        return false;
    }
}
