package zw.gov.mohcc.impilo.llm.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.llm.core.LlmCapability;
import zw.gov.mohcc.impilo.llm.core.LlmProvider;
import zw.gov.mohcc.impilo.llm.core.LlmProviderRegistry;
import zw.gov.mohcc.impilo.llm.core.LlmProviderRouter;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmMessage;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmStructuredOutputSchema;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmToolDefinition;

@RestController
@RequestMapping("/internal/v1/llm")
public class LlmController {
    private final LlmProviderRegistry registry;
    private final LlmProviderRouter router;

    public LlmController(LlmProviderRegistry registry, LlmProviderRouter router) {
        this.registry = registry;
        this.router = router;
    }

    @GetMapping("/providers")
    public ResponseEntity<?> providers() {
        return ResponseEntity.ok(Map.of(
                "items",
                registry.allByPriority().stream().map(this::providerSummary).toList()
        ));
    }

    @GetMapping("/providers/health")
    public ResponseEntity<?> providerHealth() {
        return ResponseEntity.ok(Map.of(
                "items",
                registry.allByPriority().stream().map(LlmProvider::healthStatus).toList()
        ));
    }

    @PostMapping("/providers/{provider}/test")
    public ResponseEntity<?> testProvider(@PathVariable String provider) {
        LlmResponse response = router.route(new LlmRequest(
                "LLM_PROVIDER_TEST",
                Map.of(),
                List.of(new LlmMessage("system", "Health test"), new LlmMessage("user", "Respond with OK")),
                List.of(),
                null,
                List.of(LlmCapability.CHAT),
                "LOW",
                true,
                false,
                64,
                0.0,
                provider
        ));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody LlmRequestEnvelope envelope) {
        return ResponseEntity.ok(router.route(toRequest(envelope, null)));
    }

    @PostMapping("/structured")
    public ResponseEntity<?> structured(@RequestBody LlmRequestEnvelope envelope) {
        return ResponseEntity.ok(router.route(toRequest(envelope, envelope.structuredOutputSchema())));
    }

    private Map<String, Object> providerSummary(LlmProvider p) {
        return Map.of(
                "provider", p.name(),
                "enabled", p.enabled(),
                "model", p.model(),
                "priority", p.priority(),
                "capabilities", p.capabilities()
        );
    }

    private LlmRequest toRequest(LlmRequestEnvelope env, Map<String, Object> schema) {
        LlmStructuredOutputSchema s = schema == null ? null : new LlmStructuredOutputSchema("structured_output", schema);
        List<LlmToolDefinition> tools = env.tools() == null ? List.of() : env.tools().stream()
                .map(t -> new LlmToolDefinition(t.name(), t.description(), t.inputSchema()))
                .toList();
        List<LlmCapability> required = env.requiredCapabilities() == null
                ? List.of(LlmCapability.CHAT)
                : env.requiredCapabilities().stream().map(v -> LlmCapability.valueOf(v.toUpperCase())).toList();
        return new LlmRequest(
                env.useCase(),
                env.actorContext() == null ? Map.of() : env.actorContext(),
                env.messages() == null ? List.of() : env.messages().stream().map(m -> new LlmMessage(m.role(), m.content())).toList(),
                tools,
                s,
                required,
                env.riskLevel() == null ? "MODERATE" : env.riskLevel(),
                env.requiresAudit(),
                env.requiresHumanApprovalForActions(),
                env.maxOutputTokens(),
                env.temperature(),
                env.preferredProvider()
        );
    }

    public record LlmRequestEnvelope(
            String useCase,
            Map<String, Object> actorContext,
            List<MessageEnvelope> messages,
            List<ToolEnvelope> tools,
            Map<String, Object> structuredOutputSchema,
            List<String> requiredCapabilities,
            String riskLevel,
            boolean requiresAudit,
            boolean requiresHumanApprovalForActions,
            Integer maxOutputTokens,
            Double temperature,
            String preferredProvider
    ) {}

    public record MessageEnvelope(String role, String content) {}

    public record ToolEnvelope(String name, String description, Map<String, Object> inputSchema) {}
}
