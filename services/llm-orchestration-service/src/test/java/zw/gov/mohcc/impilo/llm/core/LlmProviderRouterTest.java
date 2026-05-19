package zw.gov.mohcc.impilo.llm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zw.gov.mohcc.impilo.llm.config.LlmProperties;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmMessage;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmSafetyMetadata;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmUsageMetadata;

class LlmProviderRouterTest {

    @Test
    void fallsBackToOpenAiWhenGeminiFails() {
        LlmProperties properties = new LlmProperties();
        LlmProvider gemini = provider("gemini", true, true);
        LlmProvider openai = provider("openai", true, false);
        LlmProvider deterministic = provider("deterministic", true, false);
        LlmProvider mock = provider("mock", true, false);
        LlmProvider anthropic = provider("anthropic", true, false);
        LlmProvider deepseek = provider("deepseek", true, false);
        LlmProviderRegistry registry = new LlmProviderRegistry(List.of(gemini, openai, deterministic, mock, anthropic, deepseek));
        LlmAuditRepository auditRepository = Mockito.mock(LlmAuditRepository.class);
        Mockito.when(auditRepository.save(Mockito.any(LlmAuditEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LlmProviderRouter router = new LlmProviderRouter(
                properties,
                registry,
                Mockito.mock(LlmToolExecutionGateway.class),
                auditRepository);

        LlmResponse response = router.route(new LlmRequest(
                "TEST", Map.of(), List.of(new LlmMessage("user", "ping")), List.of(), null,
                List.of(LlmCapability.CHAT), "LOW", true, false, 50, 0.0, null));
        assertEquals("openai", response.provider());
    }

    @Test
    void usesDeterministicWhenLlmDisabled() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(false);
        LlmProvider deterministic = provider("deterministic", true, false);
        LlmProviderRegistry registry = new LlmProviderRegistry(List.of(deterministic));
        LlmAuditRepository auditRepository = Mockito.mock(LlmAuditRepository.class);
        Mockito.when(auditRepository.save(Mockito.any(LlmAuditEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LlmProviderRouter router = new LlmProviderRouter(
                properties,
                registry,
                Mockito.mock(LlmToolExecutionGateway.class),
                auditRepository);

        LlmResponse response = router.route(new LlmRequest(
                "TEST", Map.of(), List.of(new LlmMessage("user", "ping")), List.of(), null,
                List.of(LlmCapability.CHAT), "LOW", true, false, 50, 0.0, null));
        assertEquals("deterministic", response.provider());
        assertTrue(response.fallbackUsed());
    }

    private LlmProvider provider(String name, boolean enabled, boolean fail) {
        return new LlmProvider() {
            @Override
            public String name() { return name; }

            @Override
            public boolean enabled() { return enabled; }

            @Override
            public String model() { return "m"; }

            @Override
            public int priority() {
                return switch (name) {
                    case "gemini" -> 1;
                    case "openai" -> 2;
                    case "anthropic" -> 3;
                    case "deepseek" -> 4;
                    case "deterministic" -> 5;
                    default -> 6;
                };
            }

            @Override
            public Set<LlmCapability> capabilities() {
                return Set.of(LlmCapability.CHAT);
            }

            @Override
            public LlmResponse invoke(LlmRequest request) {
                if (fail) throw new RuntimeException("fail");
                return new LlmResponse(name, "m", "ok", Map.of(), List.of(), new LlmUsageMetadata(0, 0, 0),
                        new LlmSafetyMetadata(false, List.of(), null), "STOP", 1, false, false, null);
            }

            @Override
            public LlmModels.LlmProviderHealthStatus healthStatus() {
                return new LlmModels.LlmProviderHealthStatus(name, enabled, true, 1, null, null, null);
            }
        };
    }
}
