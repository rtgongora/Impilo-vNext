package zw.gov.mohcc.impilo.clinical.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thin client over the governed llm-orchestration-service ({@code /internal/v1/llm/structured}).
 *
 * <p>Honesty gate: llm-orchestration's router never throws for "no provider" — it silently falls
 * back to its own {@code deterministic}/{@code mock} provider. So a returned response is only
 * treated as a REAL model answer when {@code provider} is one of the real upstreams AND a non-empty
 * {@code structuredOutput} is present. Anything else (stub provider, empty output, timeout, error)
 * returns {@link Optional#empty()} so the caller falls back to the deterministic clinical path.
 */
@Component
public class LlmClinicalClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClinicalClient.class);
    private static final Set<String> REAL_PROVIDERS = Set.of("anthropic", "openai", "gemini", "deepseek");

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String preferredProvider;
    private final int maxOutputTokens;

    public LlmClinicalClient(
            RestTemplate clinicalLlmRestTemplate,
            @Value("${impilo.clinical.llm.base-url:http://localhost:8265}") String baseUrl,
            @Value("${impilo.clinical.llm.preferred-provider:anthropic}") String preferredProvider,
            @Value("${impilo.clinical.llm.max-output-tokens:1200}") int maxOutputTokens) {
        this.restTemplate = clinicalLlmRestTemplate;
        this.baseUrl = baseUrl;
        this.preferredProvider = preferredProvider;
        this.maxOutputTokens = maxOutputTokens;
    }

    public record LlmStructuredResult(
            String provider, String model, Map<String, Object> structuredOutput, boolean fallbackUsed, String auditRef) {
    }

    @SuppressWarnings("unchecked")
    public Optional<LlmStructuredResult> requestStructured(
            String useCase,
            List<Map<String, String>> messages,
            Map<String, Object> schema,
            Map<String, Object> actorContext,
            String riskLevel,
            double temperature) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("useCase", useCase);
            payload.put("actorContext", actorContext == null ? Map.of() : actorContext);
            payload.put("messages", messages);
            payload.put("structuredOutputSchema", schema);
            payload.put("requiredCapabilities", List.of("STRUCTURED_OUTPUT"));
            payload.put("riskLevel", riskLevel);
            payload.put("requiresAudit", true);
            payload.put("requiresHumanApprovalForActions", false);
            payload.put("maxOutputTokens", maxOutputTokens);
            payload.put("temperature", temperature);
            payload.put("preferredProvider", preferredProvider);

            ResponseEntity<Map> resp =
                    restTemplate.postForEntity(baseUrl + "/internal/v1/llm/structured", payload, Map.class);
            Map<String, Object> raw = resp.getBody();
            if (raw == null) {
                return Optional.empty();
            }
            String provider = str(raw.get("provider"));
            Object structured = raw.get("structuredOutput");
            boolean fallbackUsed = Boolean.TRUE.equals(raw.get("fallbackUsed"));

            if (provider == null || !REAL_PROVIDERS.contains(provider.toLowerCase(Locale.ROOT))) {
                // orchestration answered with its own deterministic/mock provider — not a real model.
                log.debug("Clinical LLM: non-real provider '{}' — treating as no-LLM", provider);
                return Optional.empty();
            }
            if (!(structured instanceof Map) || ((Map<?, ?>) structured).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new LlmStructuredResult(
                    provider, str(raw.get("model")), (Map<String, Object>) structured, fallbackUsed, str(raw.get("auditRef"))));
        } catch (Exception ex) {
            log.warn("Clinical LLM structured call failed ({}): {}", useCase, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
