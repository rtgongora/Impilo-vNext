package zw.gov.mohcc.impilo.llm.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LlmModels {
    private LlmModels() {}

    public record LlmMessage(String role, String content) {}

    public record LlmToolDefinition(String name, String description, Map<String, Object> inputSchema) {}

    public record LlmToolCall(String id, String name, Map<String, Object> arguments) {}

    public record LlmToolResult(String callId, String name, Map<String, Object> output, boolean success, String error) {}

    public record LlmStructuredOutputSchema(String name, Map<String, Object> schema) {}

    public record LlmUsageMetadata(int inputTokens, int outputTokens, int totalTokens) {}

    public record LlmSafetyMetadata(boolean blocked, List<String> categories, String providerRawSafetyMetadataRef) {}

    public record LlmProviderHealthStatus(
            String provider,
            boolean enabled,
            boolean healthy,
            long avgLatencyMs,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            String lastError
    ) {}

    public record LlmProviderConfig(
            String provider,
            boolean enabled,
            String model,
            String baseUrl,
            int priority,
            List<LlmCapability> capabilities
    ) {}

    public record LlmRequest(
            String useCase,
            Map<String, Object> actorContext,
            List<LlmMessage> messages,
            List<LlmToolDefinition> tools,
            LlmStructuredOutputSchema structuredOutputSchema,
            List<LlmCapability> requiredCapabilities,
            String riskLevel,
            boolean requiresAudit,
            boolean requiresHumanApprovalForActions,
            Integer maxOutputTokens,
            Double temperature,
            String preferredProvider
    ) {}

    public record LlmResponse(
            String provider,
            String model,
            String content,
            Map<String, Object> structuredOutput,
            List<LlmToolCall> toolCalls,
            LlmUsageMetadata usage,
            LlmSafetyMetadata safety,
            String finishReason,
            long latencyMs,
            boolean fallbackUsed,
            boolean requiresHumanApproval,
            String auditRef
    ) {}
}
