package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import zw.gov.mohcc.impilo.sharedkernel.integration.ports.CanonicalReferences.*;

import java.util.List;
import java.util.Map;

/**
 * Canonical AI provider port. Adapters: GeminiAdapter, OpenAIAdapter,
 * AnthropicAdapter, DeepSeekAdapter, LocalModelAdapter.
 *
 * <p>Nompilo and AI Skills MUST call this port — never a vendor SDK directly.
 * This guarantees model-substitutability, sovereignty-aware routing, and
 * uniform audit/observability.
 */
public interface AIProviderPort extends Adapter {

    OperationResult<CompletionResponse> complete(CompletionRequest request, OperationContext ctx);
    OperationResult<EmbeddingResponse>  embed(EmbeddingRequest request, OperationContext ctx);

    record CompletionRequest(
            String skillCode,
            String modelHint,
            List<Message> messages,
            Map<String, Object> parameters,
            String idempotencyKey) {}
    record Message(String role, String content) {}
    record CompletionResponse(String content, String modelRef, int promptTokens, int completionTokens, String finishReason) {}

    record EmbeddingRequest(String modelHint, List<String> inputs) {}
    record EmbeddingResponse(List<float[]> vectors, String modelRef) {}
}
