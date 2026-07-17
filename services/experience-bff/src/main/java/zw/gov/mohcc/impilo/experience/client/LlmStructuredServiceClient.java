package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Thin client for the llm-orchestration-service structured-output endpoint
 * ({@code POST /internal/v1/llm/structured}, port 8265).
 *
 * <p>llm-orchestration-service is the system of record for governed model routing (provider
 * registry, capability routing, audit). This client is the single seam through which the find-care
 * NL upgrade path ({@link zw.gov.mohcc.impilo.experience.service.FindCareServiceTaxonomy}) may map a
 * citizen's free-text care need to a structured service token when a provider is configured — it is
 * feature-flagged off by default so the deterministic taxonomy always ships regardless. It never
 * fabricates: a failure or an empty/blocked response returns {@code null} and the caller falls back
 * to the seeded synonym map.</p>
 */
@Component
public class LlmStructuredServiceClient {

    private static final Logger log = LoggerFactory.getLogger(LlmStructuredServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public LlmStructuredServiceClient(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.llm-orchestration-base-url:http://localhost:8265}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Route one structured-output request through the governed LLM orchestrator.
     *
     * @param useCase          audit/routing use-case tag (e.g. {@code FIND_CARE_SERVICE_INTERPRET})
     * @param systemPrompt     system instruction constraining the model to the allowed vocabulary
     * @param userText         the citizen's free-text need
     * @param structuredSchema the JSON schema the response must conform to (structured output)
     * @return the raw orchestrator response node, or {@code null} on any failure (never throws)
     */
    public JsonNode structured(String useCase, String systemPrompt, String userText,
                               Map<String, Object> structuredSchema) {
        try {
            Map<String, Object> envelope = Map.of(
                    "useCase", useCase,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userText)),
                    "structuredOutputSchema", structuredSchema,
                    "requiredCapabilities", List.of("CHAT", "STRUCTURED_OUTPUT"),
                    "riskLevel", "LOW",
                    "requiresAudit", true,
                    "requiresHumanApprovalForActions", false,
                    "temperature", 0.0);
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    baseUrl + "/internal/v1/llm/structured", envelope, JsonNode.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("LLM structured interpret unavailable, deterministic fallback in effect: {}", e.getMessage());
            return null;
        }
    }
}
