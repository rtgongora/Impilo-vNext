package zw.gov.mohcc.impilo.llm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.llm.config.LlmProperties;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;

@Component
public class OpenAiLlmProvider extends HttpLlmProviderSupport {
    private final LlmProperties properties;

    public OpenAiLlmProvider(RestTemplate llmRestTemplate, ObjectMapper objectMapper, LlmProperties properties) {
        super(llmRestTemplate, objectMapper);
        this.properties = properties;
    }

    @Override
    public String name() { return "openai"; }

    @Override
    public boolean enabled() { return properties.getOpenai().enabled(); }

    @Override
    public String model() { return properties.getOpenai().model(); }

    @Override
    public int priority() { return properties.getOpenai().priority(); }

    @Override
    public Set<LlmCapability> capabilities() {
        return Set.of(
                LlmCapability.CHAT, LlmCapability.STRUCTURED_OUTPUT, LlmCapability.TOOL_CALLING,
                LlmCapability.JSON_MODE, LlmCapability.SAFETY_METADATA, LlmCapability.TOKEN_USAGE,
                LlmCapability.SYSTEM_PROMPTS, LlmCapability.FUNCTION_CALLING, LlmCapability.LONG_CONTEXT
        );
    }

    @Override
    public LlmResponse invoke(LlmRequest request) {
        return callOpenAiCompatible(name(), model(), properties.getOpenai().baseUrl(), properties.getOpenai().apiKey(), request);
    }
}
