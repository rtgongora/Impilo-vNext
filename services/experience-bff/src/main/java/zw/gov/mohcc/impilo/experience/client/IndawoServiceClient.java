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
}
