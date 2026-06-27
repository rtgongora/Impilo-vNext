package zw.gov.mohcc.impilo.clinical.reasoning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.clinical.llm.LlmClinicalClient;

/**
 * Selects the single {@link ClinicalReasoner} bean. Default-on, fail-closed:
 * when {@code impilo.clinical.llm.enabled=true} (default) AND the LLM client bean is present, the
 * LLM-backed reasoner is used (it still falls back to deterministic per-call on any failure);
 * otherwise the deterministic reasoner is the bean. There is never more than one reasoner bean, so
 * injection into {@code ClinicalAssistantService} is unambiguous.
 */
@Configuration
public class ReasoningConfig {

    private static final Logger log = LoggerFactory.getLogger(ReasoningConfig.class);

    @Bean
    public ClinicalReasoner clinicalReasoner(
            @Value("${impilo.clinical.llm.enabled:true}") boolean llmEnabled,
            @Value("${impilo.clinical.llm.min-grounding-ratio:0.5}") double minGroundingRatio,
            ObjectProvider<LlmClinicalClient> clientProvider,
            ClinicalPromptBuilder promptBuilder,
            GroundingValidator groundingValidator) {
        DeterministicClinicalReasoner deterministic = new DeterministicClinicalReasoner();
        LlmClinicalClient client = clientProvider.getIfAvailable();
        if (llmEnabled && client != null) {
            log.info("Clinical reasoner: LLM-backed (grounded, fail-closed to deterministic).");
            return new LlmClinicalReasoner(client, promptBuilder, groundingValidator, deterministic, minGroundingRatio);
        }
        log.warn("Clinical reasoner: DETERMINISTIC only (LLM disabled or client unavailable).");
        return deterministic;
    }
}
