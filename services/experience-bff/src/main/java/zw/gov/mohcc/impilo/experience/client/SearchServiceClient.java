package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for the search-service (Health OS §12: Governed Knowledge).
 * Proxies search-service {@code GET /internal/v1/search} (query {@code q},
 * optional {@code entityType}, paging, optional {@code rankMode}).
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

    public JsonNode search(String query, String entityType, int page, int size) {
        return search(query, entityType, page, size, null);
    }

    public JsonNode search(String query, String entityType, int page, int size, @Nullable String rankMode) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/search")
                .queryParam("q", query)
                .queryParam("page", page)
                .queryParam("size", size);
        if (entityType != null && !entityType.isBlank()) {
            b.queryParam("entityType", entityType);
        }
        if (rankMode != null && !rankMode.isBlank()) {
            b.queryParam("rankMode", rankMode);
        }
        String url = b.toUriString();
        log.debug("Search: query={} entityType={} rankMode={}", query, entityType, rankMode);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getDocument(String documentId) {
        String url = baseUrl + "/internal/v1/documents/" + documentId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }
}
