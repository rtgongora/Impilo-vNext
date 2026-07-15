package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client for guidance-service (Health OS §13: Conversational & Guidance).
 *
 * <p>Bridges the Experience BFF to the guidance-service backend which provides
 * knowledge retrieval, conversational guidance, reminders, and education.</p>
 */
@Component
public class GuidanceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(GuidanceServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public GuidanceServiceClient(RestTemplate serviceRestTemplate,
                                  ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.guidanceBaseUrl();
    }

    public JsonNode ask(String question, boolean personalized) {
        return ask(question, personalized, Map.of());
    }

    public JsonNode ask(String question, boolean personalized, Map<String, Object> context) {
        String url = baseUrl + "/internal/v1/guidance/ask";
        log.debug("Guidance: ask personalized={} routePath={}", personalized, context.get("routePath"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        payload.put("personalized", personalized);
        if (context != null && !context.isEmpty()) {
            payload.put("context", context);
        }
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, payload, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getReminders() {
        String url = baseUrl + "/internal/v1/guidance/reminders";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Nompilo context-aware guidance (V004) ─────────────────────────────────────────

    /** Resolve the contextual guidance set for the caller's route/user-type/trust + signals. */
    public JsonNode resolveContext(Map<String, Object> contextRequest) {
        String url = baseUrl + "/internal/v1/guidance/nompilo/context";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, contextRequest, JsonNode.class);
        return extractData(response);
    }

    /** Map a denied/locked reason code to a safe plain-language explanation (no sensitive leak). */
    public JsonNode explainLocked(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/guidance/nompilo/explain-locked";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /** Record a person's dismissal of a guidance item. */
    public JsonNode dismiss(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/guidance/nompilo/dismiss";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /**
     * Request a Nompilo handoff / follow-up via the real guidance-service lifecycle (V003). This is
     * the canonical pathway Nompilo uses to REQUEST a human/Khuluma follow-up — it emits
     * {@code core.nompilo.handoff.requested}, which the comms (Khuluma) and Rito subscribers consume.
     * Nompilo never sends a notification itself.
     */
    public JsonNode requestHandoff(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/guidance/nompilo/handoffs";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getEducation(String domain, int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/guidance/education")
                .queryParam("domain", domain)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode search(String query, String domain, int page, int size) {
        java.net.URI url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/guidance/search")
                .queryParam("q", query)
                .queryParam("domain", domain)
                .queryParam("page", page)
                .queryParam("size", size)
                .encode().build().toUri();
        log.debug("Guidance: search q={} domain={}", query, domain);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Public gateway lane (R0, anonymous — public-lane ADR) ─────────────────────────
    // These call ONLY the service-side PublicGuidanceController (/v1/public/guidance/**):
    // allow-listed DTOs, no PII, no personalization. Raw body is forwarded (incl. the
    // data envelope) so the BFF adds no interpretation of public content.

    /** One trust-escalation explainer (why / what level / what next / how to get help). */
    public JsonNode getPublicExplainStep(String stepKey) {
        String url = baseUrl + "/v1/public/guidance/explain-steps/"
                + java.net.URLEncoder.encode(stepKey, java.nio.charset.StandardCharsets.UTF_8);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(java.net.URI.create(url), JsonNode.class);
        return response.getBody();
    }

    /** All active trust-escalation explainers. */
    public JsonNode listPublicExplainSteps() {
        String url = baseUrl + "/v1/public/guidance/explain-steps";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    /**
     * Published public education topics (safe fields only). Optional category filter and optional
     * free-text query — a non-blank {@code q} runs the PUBLISHED-only title/summary search downstream.
     */
    public JsonNode getPublicEducation(String domain, String category, String q, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/public/guidance/education")
                .queryParam("domain", domain)
                .queryParam("page", page)
                .queryParam("size", size);
        if (category != null && !category.isBlank()) {
            builder.queryParam("category", category);
        }
        if (q != null && !q.isBlank()) {
            builder.queryParam("q", q);
        }
        java.net.URI url = builder.encode().build().toUri();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    /** Published-topic categories with counts (the public health-info topic index). */
    public JsonNode getPublicEducationCategories() {
        String url = baseUrl + "/v1/public/guidance/education/categories";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    /** One published education article incl. plain-language body (allow-listed fields only). */
    public JsonNode getPublicEducationArticle(String id) {
        String url = baseUrl + "/v1/public/guidance/education/"
                + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(java.net.URI.create(url), JsonNode.class);
        return response.getBody();
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
