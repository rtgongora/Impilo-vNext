package zw.gov.mohcc.impilo.llm.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.llm.config.LlmProperties;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmSafetyMetadata;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmUsageMetadata;

@Component
public class MockLlmProvider extends AbstractLlmProvider {
    private final LlmProperties properties;

    public MockLlmProvider(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean enabled() {
        return properties.getMock().enabled();
    }

    @Override
    public String model() {
        return properties.getMock().model();
    }

    @Override
    public int priority() {
        return properties.getMock().priority();
    }

    @Override
    public Set<LlmCapability> capabilities() {
        return Set.of(LlmCapability.CHAT, LlmCapability.STRUCTURED_OUTPUT, LlmCapability.TOOL_CALLING, LlmCapability.JSON_MODE);
    }

    @Override
    public LlmResponse invoke(LlmRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("summary", "Mock response for " + request.useCase());
        structured.put("riskLevel", "LOW");
        structured.put("recommendedActions", List.of("No-op"));
        structured.put("suggestedTasks", List.of());
        structured.put("suggestedMessages", List.of());
        structured.put("sourceSignals", List.of());
        structured.put("confidence", 0.99);
        long latency = System.currentTimeMillis() - start;
        recordSuccess(latency);
        return new LlmResponse(
                name(),
                model(),
                "Mock provider response",
                structured,
                List.of(),
                new LlmUsageMetadata(10, 12, 22),
                new LlmSafetyMetadata(false, List.of(), null),
                "STOP",
                latency,
                false,
                false,
                null
        );
    }
}
