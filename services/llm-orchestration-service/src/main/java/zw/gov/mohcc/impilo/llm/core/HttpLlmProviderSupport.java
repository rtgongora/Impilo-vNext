package zw.gov.mohcc.impilo.llm.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmMessage;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmSafetyMetadata;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmUsageMetadata;

abstract class HttpLlmProviderSupport extends AbstractLlmProvider {
    protected final RestTemplate restTemplate;
    protected final ObjectMapper objectMapper;

    protected HttpLlmProviderSupport(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    protected LlmResponse callOpenAiCompatible(String provider, String model, String baseUrl, String apiKey, LlmRequest request) {
        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.setBearerAuth(apiKey);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", request.messages().stream().map(this::toMessageMap).toList());
            payload.put("max_tokens", request.maxOutputTokens() != null ? request.maxOutputTokens() : 1024);
            payload.put("temperature", request.temperature() != null ? request.temperature() : 0.2d);
            JsonNode node = restTemplate.postForEntity(
                    normalized(baseUrl) + "/v1/chat/completions",
                    new HttpEntity<>(payload, headers),
                    JsonNode.class
            ).getBody();
            String content = readCompletionContent(node);
            int input = node != null && node.path("usage").has("prompt_tokens") ? node.path("usage").path("prompt_tokens").asInt() : 0;
            int output = node != null && node.path("usage").has("completion_tokens") ? node.path("usage").path("completion_tokens").asInt() : 0;
            int total = node != null && node.path("usage").has("total_tokens") ? node.path("usage").path("total_tokens").asInt() : input + output;
            long latency = System.currentTimeMillis() - start;
            recordSuccess(latency);
            return new LlmResponse(
                    provider,
                    model,
                    content,
                    Map.of("summary", content, "riskLevel", "MODERATE"),
                    List.of(),
                    new LlmUsageMetadata(input, output, total),
                    new LlmSafetyMetadata(false, List.of(), null),
                    "STOP",
                    latency,
                    false,
                    false,
                    null
            );
        } catch (Exception ex) {
            recordFailure(ex.getMessage());
            throw ex;
        }
    }

    private Map<String, Object> toMessageMap(LlmMessage msg) {
        return Map.of("role", msg.role(), "content", msg.content());
    }

    private String readCompletionContent(JsonNode node) {
        if (node == null) return "";
        JsonNode choices = node.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode content = choices.get(0).path("message").path("content");
            return content.isMissingNode() ? "" : content.asText("");
        }
        return "";
    }

    private String normalized(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
