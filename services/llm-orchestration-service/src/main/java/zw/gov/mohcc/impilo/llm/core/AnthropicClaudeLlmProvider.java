package zw.gov.mohcc.impilo.llm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.llm.config.LlmProperties;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;

@Component
public class AnthropicClaudeLlmProvider extends HttpLlmProviderSupport {
    private final LlmProperties properties;

    public AnthropicClaudeLlmProvider(RestTemplate llmRestTemplate, ObjectMapper objectMapper, LlmProperties properties) {
        super(llmRestTemplate, objectMapper);
        this.properties = properties;
    }

    @Override
    public String name() { return "anthropic"; }

    @Override
    public boolean enabled() { return properties.getAnthropic().enabled(); }

    @Override
    public String model() { return properties.getAnthropic().model(); }

    @Override
    public int priority() { return properties.getAnthropic().priority(); }

    @Override
    public Set<LlmCapability> capabilities() {
        return Set.of(
                LlmCapability.CHAT, LlmCapability.STRUCTURED_OUTPUT, LlmCapability.TOOL_CALLING,
                LlmCapability.SAFETY_METADATA, LlmCapability.TOKEN_USAGE, LlmCapability.SYSTEM_PROMPTS, LlmCapability.LONG_CONTEXT
        );
    }

    @Override
    public LlmResponse invoke(LlmRequest request) {
        return callOpenAiCompatible(name(), model(), properties.getAnthropic().baseUrl(), properties.getAnthropic().apiKey(), request);
    }
}
