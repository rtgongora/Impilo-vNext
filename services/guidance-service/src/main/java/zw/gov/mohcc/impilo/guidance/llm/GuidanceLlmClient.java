package zw.gov.mohcc.impilo.guidance.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Grounded LLM synthesis for the conversational guidance answer (G039), over the
 * governed llm-orchestration-service ({@code /internal/v1/llm/structured}).
 *
 * <p>Opt-in via {@code guidance.llm.enabled} (default false → the caller keeps the
 * knowledge-base-retrieval answer). The model is instructed to answer ONLY from the supplied
 * knowledge-base sources (no fabrication, advisory tone, cite the sources used) and to return a
 * calibrated confidence.</p>
 *
 * <p>Honesty gate (same as CKP): llm-orchestration's router never throws for "no provider" — it
 * silently falls back to its own deterministic/mock provider. A response is treated as a REAL model
 * answer only when {@code provider} is a real upstream AND a non-empty {@code structuredOutput} with
 * an {@code answer} is present. Anything else (stub provider, empty, timeout, error) returns
 * {@link Optional#empty()} so the caller falls back to retrieval — never fabricates.</p>
 */
@Component
public class GuidanceLlmClient {

    private static final Logger log = LoggerFactory.getLogger(GuidanceLlmClient.class);
    private static final Set<String> REAL_PROVIDERS = Set.of("anthropic", "openai", "gemini", "deepseek");

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String baseUrl;
    private final String preferredProvider;
    private final int maxOutputTokens;

    public GuidanceLlmClient(
            RestTemplate guidanceLlmRestTemplate,
            @Value("${guidance.llm.enabled:false}") boolean enabled,
            @Value("${guidance.llm.base-url:http://localhost:8265}") String baseUrl,
            @Value("${guidance.llm.preferred-provider:anthropic}") String preferredProvider,
            @Value("${guidance.llm.max-output-tokens:800}") int maxOutputTokens) {
        this.restTemplate = guidanceLlmRestTemplate;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.preferredProvider = preferredProvider;
        this.maxOutputTokens = maxOutputTokens;
    }

    /** A source article the answer may be grounded in. */
    public record Source(String title, String summary, String domain) {}

    /** A grounded answer with a calibrated confidence and the titles the model cited. */
    public record GroundedAnswer(String answer, double confidence, List<String> citedTitles) {}

    /**
     * Synthesise a grounded answer from the question + retrieved sources, or empty to fall back.
     */
    @SuppressWarnings("unchecked")
    public Optional<GroundedAnswer> synthesize(String question, List<Source> sources) {
        if (!enabled || question == null || question.isBlank() || sources == null || sources.isEmpty()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("useCase", "guidance.conversational.answer");
            payload.put("actorContext", Map.of());
            payload.put("messages", buildMessages(question, sources));
            payload.put("structuredOutputSchema", answerSchema());
            payload.put("requiredCapabilities", List.of("STRUCTURED_OUTPUT"));
            payload.put("riskLevel", "LOW");
            payload.put("requiresAudit", true);
            payload.put("requiresHumanApprovalForActions", false);
            payload.put("maxOutputTokens", maxOutputTokens);
            payload.put("temperature", 0.2);
            payload.put("preferredProvider", preferredProvider);

            ResponseEntity<Map> resp =
                    restTemplate.postForEntity(baseUrl + "/internal/v1/llm/structured", payload, Map.class);
            Map<String, Object> raw = resp.getBody();
            if (raw == null) {
                return Optional.empty();
            }
            String provider = str(raw.get("provider"));
            if (provider == null || !REAL_PROVIDERS.contains(provider.toLowerCase(Locale.ROOT))) {
                log.debug("Guidance LLM: non-real provider '{}' — falling back to retrieval", provider);
                return Optional.empty();
            }
            Object structured = raw.get("structuredOutput");
            if (!(structured instanceof Map<?, ?> out) || out.isEmpty()) {
                return Optional.empty();
            }
            String answer = str(out.get("answer"));
            if (answer == null || answer.isBlank()) {
                return Optional.empty();
            }
            double confidence = clamp(out.get("confidence"));
            List<String> cited = toStringList(out.get("citedTitles"));
            return Optional.of(new GroundedAnswer(answer, confidence, cited));
        } catch (Exception ex) {
            log.warn("Guidance LLM synthesis failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static List<Map<String, String>> buildMessages(String question, List<Source> sources) {
        StringBuilder ctx = new StringBuilder();
        int i = 1;
        for (Source s : sources) {
            ctx.append("[").append(i++).append("] ").append(s.title()).append("\n")
                    .append(s.summary() == null ? "" : s.summary()).append("\n\n");
        }
        String system = "You are a health guidance assistant. Answer the user's question using ONLY the "
                + "numbered knowledge-base sources provided. Do not invent facts not supported by them. "
                + "Be concise, advisory, and never override professional medical judgement. If the sources "
                + "do not contain the answer, say so. Return the cited source titles and a confidence in "
                + "[0,1] reflecting how well the sources support your answer.";
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user",
                "content", "Question: " + question + "\n\nSources:\n" + ctx));
        return messages;
    }

    private static Map<String, Object> answerSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "answer", Map.of("type", "string"),
                        "confidence", Map.of("type", "number"),
                        "citedTitles", Map.of("type", "array", "items", Map.of("type", "string"))),
                "required", List.of("answer", "confidence"));
    }

    private static double clamp(Object v) {
        if (!(v instanceof Number n)) {
            return 0.7;
        }
        double d = n.doubleValue();
        return Math.max(0.0, Math.min(1.0, d));
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object v) {
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
