package zw.gov.mohcc.impilo.llm.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.llm.config.LlmProperties;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmToolCall;

@Service
public class LlmProviderRouter {
    private final LlmProperties properties;
    private final LlmProviderRegistry registry;
    private final LlmToolExecutionGateway toolGateway;
    private final LlmAuditRepository auditRepository;

    public LlmProviderRouter(
            LlmProperties properties,
            LlmProviderRegistry registry,
            LlmToolExecutionGateway toolGateway,
            LlmAuditRepository auditRepository) {
        this.properties = properties;
        this.registry = registry;
        this.toolGateway = toolGateway;
        this.auditRepository = auditRepository;
    }

    public LlmResponse route(LlmRequest request) {
        if (!properties.isEnabled()) {
            return invokeWithAudit(registry.byName("deterministic"), request, true);
        }

        LlmProvider explicit = registry.byName(request.preferredProvider());
        if (isUsable(explicit, request)) {
            return invokeWithAudit(explicit, request, false);
        }

        LlmProvider defaultProvider = registry.byName(properties.getDefaultProvider());
        if (isUsable(defaultProvider, request)) {
            try {
                return invokeWithAudit(defaultProvider, request, false);
            } catch (Exception ignored) {
                // fallback chain below
            }
        }

        List<String> chain = new ArrayList<>(List.of("gemini", "openai", "anthropic", "deepseek"));
        if (!chain.contains(properties.getFallbackProvider().toLowerCase(Locale.ROOT))) {
            chain.add(properties.getFallbackProvider());
        }
        chain.add("deterministic");
        chain.add("mock");

        for (String candidate : chain) {
            LlmProvider provider = registry.byName(candidate);
            if (!isUsable(provider, request)) continue;
            try {
                boolean fallback = !"gemini".equalsIgnoreCase(candidate);
                return invokeWithAudit(provider, request, fallback);
            } catch (Exception ignored) {
                // keep trying
            }
        }
        return invokeWithAudit(Objects.requireNonNull(registry.byName("deterministic")), request, true);
    }

    private boolean isUsable(LlmProvider provider, LlmRequest request) {
        if (provider == null || !provider.enabled()) return false;
        if (properties.getAllowedProviders() != null
                && !properties.getAllowedProviders().contains(provider.name().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (request.requiredCapabilities() == null || request.requiredCapabilities().isEmpty()) return true;
        return provider.capabilities().containsAll(request.requiredCapabilities());
    }

    private LlmResponse invokeWithAudit(LlmProvider provider, LlmRequest request, boolean fallbackFlag) {
        LlmResponse response = provider.invoke(request);
        if (response.toolCalls() != null && !response.toolCalls().isEmpty() && properties.isToolCallingEnabled()) {
            for (LlmToolCall call : response.toolCalls()) {
                toolGateway.execute(provider.name(), request, call);
            }
        }
        LlmAuditEntity entity = new LlmAuditEntity();
        entity.setProvider(response.provider());
        entity.setModel(response.model());
        entity.setUseCase(request.useCase());
        entity.setRiskLevel(request.riskLevel());
        entity.setFallbackUsed(fallbackFlag || response.fallbackUsed());
        entity.setRequiresHumanApproval(response.requiresHumanApproval());
        entity.setLatencyMs(response.latencyMs());
        entity.setInputHash(hash(String.valueOf(request.messages())));
        entity.setOutputHash(hash(response.content() + String.valueOf(response.structuredOutput())));
        if (request.actorContext() != null && request.actorContext().get("actorId") != null) {
            entity.setActorId(String.valueOf(request.actorContext().get("actorId")));
        }
        if (request.actorContext() != null && request.actorContext().get("tenantId") != null) {
            try {
                entity.setTenantId(java.util.UUID.fromString(String.valueOf(request.actorContext().get("tenantId"))));
            } catch (IllegalArgumentException ignored) {
                // no-op
            }
        }
        LlmAuditEntity saved = auditRepository.save(entity);
        return new LlmResponse(
                response.provider(),
                response.model(),
                response.content(),
                response.structuredOutput(),
                response.toolCalls(),
                response.usage(),
                response.safety(),
                response.finishReason(),
                response.latencyMs(),
                entity.isFallbackUsed(),
                response.requiresHumanApproval(),
                "llm-audit-" + saved.getId()
        );
    }

    private String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return "";
        }
    }
}
