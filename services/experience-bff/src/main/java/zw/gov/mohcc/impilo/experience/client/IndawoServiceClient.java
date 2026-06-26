package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for INDAWO (public health site registry).
 */
@Component
public class IndawoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(IndawoServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public IndawoServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.indawoBaseUrl();
    }

    /** Paged site list (internal). */
    public JsonNode listSites(int cursor, int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/sites")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit)
                .toUriString();
        log.info("INDAWO: listSites cursor={} limit={}", cursor, limit);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    /** Internal site detail. */
    public JsonNode getSite(UUID siteId) {
        String url = baseUrl + "/internal/v1/sites/" + siteId;
        log.info("INDAWO: getSite siteId={}", siteId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    /** Idempotent site upsert (creates or updates shell). */
    public JsonNode upsertSite(UUID siteId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/sites/" + siteId;
        log.info("INDAWO: upsertSite siteId={}", siteId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(body, headers), JsonNode.class);
        return response.getBody();
    }

    // ── Public-health surveillance / place mode ─────────────────────────────

    private JsonNode dataOf(ResponseEntity<JsonNode> response) {
        JsonNode body = response.getBody();
        if (body != null && body.has("data")) {
            return body.get("data");
        }
        return body;
    }

    public JsonNode placeModeSummary() {
        String url = baseUrl + "/internal/v1/surveillance/place-mode/summary";
        return dataOf(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listSurveillance(String resource, String queryParam, String queryValue) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(
                baseUrl + "/internal/v1/surveillance/" + resource);
        if (queryParam != null && queryValue != null) {
            b.queryParam(queryParam, queryValue);
        }
        return dataOf(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
    }

    public JsonNode postSurveillance(String path, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/surveillance/" + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return dataOf(restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class));
    }
}
