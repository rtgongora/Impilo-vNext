package zw.gov.mohcc.impilo.learning.core.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects the active {@link LearningAiProvider} by configuration. Default is the safe
 * local stub ({@code STUB_SAFE_LOCAL}); set {@code learning.ai.provider=LLM_ORCHESTRATION}
 * to use the real provider. The selected concrete bean is exposed {@code @Primary} so
 * existing injection points are unchanged (no router, no bean recursion).
 */
@Configuration
public class LearningAiConfig {

    private static final Logger log = LoggerFactory.getLogger(LearningAiConfig.class);

    @Bean
    @Primary
    public LearningAiProvider activeLearningAiProvider(
            LearningAiStubProvider stub,
            LlmOrchestrationLearningAiProvider llmOrchestration,
            @Value("${learning.ai.provider:STUB_SAFE_LOCAL}") String selected) {
        LearningAiProvider chosen = LlmOrchestrationLearningAiProvider.KEY.equals(selected)
                ? llmOrchestration
                : stub;
        log.info("Fundo AI provider selected: {}", chosen.providerName());
        return chosen;
    }
}
