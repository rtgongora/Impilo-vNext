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
public class DeterministicFallbackLlmProvider extends AbstractLlmProvider {
    private final LlmProperties properties;

    public DeterministicFallbackLlmProvider(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "deterministic";
    }

    @Override
    public boolean enabled() {
        return properties.getDeterministic().enabled();
    }

    @Override
    public String model() {
        return properties.getDeterministic().model();
    }

    @Override
    public int priority() {
        return properties.getDeterministic().priority();
    }

    @Override
    public Set<LlmCapability> capabilities() {
        return Set.of(LlmCapability.CHAT, LlmCapability.STRUCTURED_OUTPUT, LlmCapability.SAFETY_METADATA, LlmCapability.TOKEN_USAGE);
    }

    @Override
    public LlmResponse invoke(LlmRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("summary", "Deterministic fallback response for " + request.useCase());
        structured.put("riskLevel", "MODERATE");
        structured.put("recommendedActions", List.of("Review open alerts", "Confirm scope before action"));
        structured.put("requiresHumanApproval", properties.isRequireHumanApprovalForHighRiskActions());
        long latency = System.currentTimeMillis() - start;
        recordSuccess(latency);
        return new LlmResponse(
                name(),
                model(),
                "Deterministic fallback used.",
                structured,
                List.of(),
                new LlmUsageMetadata(0, 0, 0),
                new LlmSafetyMetadata(false, List.of(), null),
                "STOP",
                latency,
                true,
                properties.isRequireHumanApprovalForHighRiskActions(),
                null
        );
    }
}
