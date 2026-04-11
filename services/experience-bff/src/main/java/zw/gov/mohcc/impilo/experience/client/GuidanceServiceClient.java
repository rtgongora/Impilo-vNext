package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

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
        String url = baseUrl + "/internal/v1/guidance/ask";
        log.debug("Guidance: ask personalized={}", personalized);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url,
                Map.of("question", question, "personalized", personalized), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getReminders() {
        String url = baseUrl + "/internal/v1/guidance/reminders";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
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
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/guidance/search")
                .queryParam("q", query)
                .queryParam("domain", domain)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        log.debug("Guidance: search q={} domain={}", query, domain);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
