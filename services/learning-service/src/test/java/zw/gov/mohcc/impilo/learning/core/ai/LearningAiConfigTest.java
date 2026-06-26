package zw.gov.mohcc.impilo.learning.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LearningAiConfigTest {

    private final LearningAiConfig config = new LearningAiConfig();
    private final LearningAiStubProvider stub = new LearningAiStubProvider();
    private final LlmOrchestrationLearningAiProvider real = new LlmOrchestrationLearningAiProvider("http://localhost:8265", "");

    @Test
    void defaultsToStub() {
        LearningAiProvider chosen = config.activeLearningAiProvider(stub, real, "STUB_SAFE_LOCAL");
        assertThat(chosen.providerName()).isEqualTo("STUB_SAFE_LOCAL");
    }

    @Test
    void selectsLlmOrchestrationWhenConfigured() {
        LearningAiProvider chosen = config.activeLearningAiProvider(stub, real, "LLM_ORCHESTRATION");
        assertThat(chosen.providerName()).isEqualTo("LLM_ORCHESTRATION");
    }

    @Test
    void unknownProviderFallsBackToStub() {
        LearningAiProvider chosen = config.activeLearningAiProvider(stub, real, "SOMETHING_ELSE");
        assertThat(chosen.providerName()).isEqualTo("STUB_SAFE_LOCAL");
    }
}
