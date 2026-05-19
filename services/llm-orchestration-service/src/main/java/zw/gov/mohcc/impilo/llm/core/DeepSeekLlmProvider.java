package zw.gov.mohcc.impilo.llm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.llm.config.LlmProperties;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;

@Component
public class DeepSeekLlmProvider extends HttpLlmProviderSupport {
    private final LlmProperties properties;

    public DeepSeekLlmProvider(RestTemplate llmRestTemplate, ObjectMapper objectMapper, LlmProperties properties) {
        super(llmRestTemplate, objectMapper);
        this.properties = properties;
    }

    @Override
    public String name() { return "deepseek"; }

    @Override
    public boolean enabled() { return properties.getDeepseek().enabled(); }

    @Override
    public String model() { return properties.getDeepseek().model(); }

    @Override
    public int priority() { return properties.getDeepseek().priority(); }

    @Override
    public Set<LlmCapability> capabilities() {
        return Set.of(
                LlmCapability.CHAT, LlmCapability.STRUCTURED_OUTPUT, LlmCapability.TOOL_CALLING,
                LlmCapability.JSON_MODE, LlmCapability.TOKEN_USAGE, LlmCapability.SYSTEM_PROMPTS, LlmCapability.LONG_CONTEXT
        );
    }

    @Override
    public LlmResponse invoke(LlmRequest request) {
        return callOpenAiCompatible(name(), model(), properties.getDeepseek().baseUrl(), properties.getDeepseek().apiKey(), request);
    }
}
