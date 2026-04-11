package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for the search-service (Health OS §12: Governed Knowledge).
 * Provides federated full-text search across health, wellness, diet, sleep,
 * service, and marketplace knowledge domains.
 */
@Component
public class SearchServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SearchServiceClient(RestTemplate serviceRestTemplate,
                               ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.searchBaseUrl();
    }

    public JsonNode search(String query, String domain, int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/search")
                .queryParam("q", query)
                .queryParam("domain", domain)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        log.debug("Search: query={} domain={}", query, domain);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getDocument(String documentId) {
        String url = baseUrl + "/internal/v1/documents/" + documentId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }
}
