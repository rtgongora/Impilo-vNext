package zw.gov.mohcc.impilo.llm.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.llm.config.LlmProperties;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmMessage;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmToolCall;

@Service
public class LlmProviderRouter {
    /** Providers whose {@code invoke} egresses to a third-party model; prompts to these are scrubbed. */
    private static final Set<String> EXTERNAL_PROVIDERS = Set.of("gemini", "openai", "anthropic", "deepseek");

    private final LlmProperties properties;
    private final LlmProviderRegistry registry;
    private final LlmToolExecutionGateway toolGateway;
    private final LlmAuditRepository auditRepository;
    private final PromptPiiScrubber piiScrubber;

    public LlmProviderRouter(
            LlmProperties properties,
            LlmProviderRegistry registry,
            LlmToolExecutionGateway toolGateway,
            LlmAuditRepository auditRepository,
            PromptPiiScrubber piiScrubber) {
        this.properties = properties;
        this.registry = registry;
        this.piiScrubber = piiScrubber;
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

    /**
     * Redact direct identifiers from the prompt before it egresses to an external model. Local
     * providers (deterministic/mock) never leave the estate, so they pass through unchanged.
     */
    private LlmRequest scrubForEgress(LlmProvider provider, LlmRequest request) {
        if (provider == null || !EXTERNAL_PROVIDERS.contains(provider.name().toLowerCase(Locale.ROOT))) {
            return request;
        }
        if (request.messages() == null || request.messages().isEmpty()) {
            return request;
        }
        boolean anyRedacted = false;
        List<LlmMessage> scrubbed = new ArrayList<>(request.messages().size());
        for (LlmMessage m : request.messages()) {
            PromptPiiScrubber.Result r = piiScrubber.scrub(m.content());
            anyRedacted |= r.redacted();
            scrubbed.add(new LlmMessage(m.role(), r.text()));
        }
        if (!anyRedacted) {
            return request;
        }
        org.slf4j.LoggerFactory.getLogger(LlmProviderRouter.class)
                .warn("PII redacted from prompt before egress to external provider {} (useCase={})",
                        provider.name(), request.useCase());
        return new LlmRequest(request.useCase(), request.actorContext(), scrubbed, request.tools(),
                request.structuredOutputSchema(), request.requiredCapabilities(), request.riskLevel(),
                request.requiresAudit(), request.requiresHumanApprovalForActions(), request.maxOutputTokens(),
                request.temperature(), request.preferredProvider());
    }

    private LlmResponse invokeWithAudit(LlmProvider provider, LlmRequest request, boolean fallbackFlag) {
        // PII Wave 0.2: scrub direct identifiers from the prompt before it egresses to a third-party model.
        LlmRequest egressRequest = scrubForEgress(provider, request);
        LlmResponse response = provider.invoke(egressRequest);
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
        entity.setInputHash(hash(String.valueOf(egressRequest.messages())));
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
