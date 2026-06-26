package zw.gov.mohcc.impilo.learning.core.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Real, provider-agnostic AI provider that delegates to the platform
 * llm-orchestration service (the same {@code /internal/v1/llm/structured} contract the
 * Experience BFF uses). Consume-not-rebuild: Fundo never calls a public LLM API directly.
 *
 * <p>Disabled by default — selected only when {@code learning.ai.provider=LLM_ORCHESTRATION}.
 * Model choice is delegated to llm-orchestration via {@code requiredCapabilities}, so the
 * platform stays provider-agnostic. Output remains a review-gated draft. If the
 * orchestration service is not configured/reachable it fails loudly rather than fabricating.
 */
@Component
public class LlmOrchestrationLearningAiProvider implements LearningAiProvider {

    public static final String KEY = "LLM_ORCHESTRATION";
    private static final Logger log = LoggerFactory.getLogger(LlmOrchestrationLearningAiProvider.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String model;

    public LlmOrchestrationLearningAiProvider(
            @Value("${learning.ai.llm-base-url:http://localhost:8265}") String baseUrl,
            @Value("${learning.ai.model:}") String model) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.model = model == null ? "" : model.trim();
    }

    @Override
    public String providerName() {
        return KEY;
    }

    @Override
    public Map<String, Object> generateCourseOutline(Map<String, Object> request) {
        return generate("FUNDO_AUTHORING_COURSE_OUTLINE", "course-outline", request);
    }

    @Override
    public Map<String, Object> generateLessonContent(Map<String, Object> request) {
        return generate("FUNDO_AUTHORING_LESSON_CONTENT", "lesson-content", request);
    }

    @Override
    public Map<String, Object> generateQuiz(Map<String, Object> request) {
        return generate("FUNDO_AUTHORING_QUIZ", "quiz", request);
    }

    @Override
    public Map<String, Object> generateSummary(Map<String, Object> request) {
        return generate("FUNDO_AUTHORING_SUMMARY", "summary", request);
    }

    @Override
    public Map<String, Object> generateSurvey(Map<String, Object> request) {
        return generate("FUNDO_AUTHORING_SURVEY", "survey", request);
    }

    @Override
    public Map<String, Object> generateFacilitatorGuide(Map<String, Object> request) {
        return generate("FUNDO_AUTHORING_FACILITATOR_GUIDE", "facilitator-guide", request);
    }

    @Override
    public Map<String, Object> generateVoiceoverScript(Map<String, Object> request) {
        return generate("FUNDO_AUTHORING_VOICEOVER", "voiceover-script", request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> generate(String useCase, String generationType, Map<String, Object> request) {
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("learning.ai.llm-base-url not configured for LLM_ORCHESTRATION provider");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("useCase", useCase);
        payload.put("purposeOfUse", "LEARNING_AUTHORING");
        payload.put("requiredCapabilities", java.util.List.of("STRUCTURED_OUTPUT"));
        payload.put("riskLevel", "MODERATE");
        payload.put("requiresAudit", true);
        payload.put("requiresHumanApprovalForActions", true);
        payload.put("temperature", 0.2);
        if (!model.isBlank()) {
            payload.put("model", model);
        }
        payload.put("input", request == null ? Map.of() : request);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Object body = restTemplate.postForObject(
                    trimSlash(baseUrl) + "/internal/v1/llm/structured", new HttpEntity<>(payload, headers), Map.class);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("generationType", generationType);
            out.put("provider", KEY);
            out.put("draft", true);
            out.put("output", body == null ? Map.of() : body);
            return out;
        } catch (Exception ex) {
            log.warn("llm-orchestration generation failed for {}: {}", useCase, ex.getMessage());
            throw new IllegalStateException("AI generation via llm-orchestration failed: " + ex.getMessage(), ex);
        }
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
