package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the Indawo facility / address registry sovereign service.
 */
@Component
public class IndawoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(IndawoServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public IndawoServiceClient(RestTemplate serviceRestTemplate,
                               ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.indawoBaseUrl();
    }

    public JsonNode listAddresses() {
        String url = baseUrl + "/internal/v1/addresses";
        log.info("INDAWO: listAddresses operation");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getAddress(UUID id) {
        String url = baseUrl + "/internal/v1/addresses/" + id;
        log.info("INDAWO: getAddress operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createAddress(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/addresses";
        log.info("INDAWO: createAddress operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode upsertSite(UUID siteId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/sites/" + siteId;
        log.info("INDAWO: upsertSite operation [siteId={}]", siteId);
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getSiteInternal(UUID siteId) {
        String url = baseUrl + "/internal/v1/sites/" + siteId;
        log.info("INDAWO: getSiteInternal operation [siteId={}]", siteId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listSites(int cursor, int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/sites")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit)
                .build()
                .encode()
                .toUriString();
        log.info("INDAWO: listSites operation [cursor={}, limit={}]", cursor, limit);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getSiteExternal(UUID siteId) {
        String url = baseUrl + "/external/v1/sites/" + siteId;
        log.info("INDAWO: getSiteExternal operation [siteId={}]", siteId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode snapshotSites(int cursor, int limit, String asOf) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/snapshots/sites")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit);
        if (asOf != null && !asOf.isBlank()) {
            b.queryParam("as_of", asOf);
        }
        String url = b.build().encode().toUriString();
        log.info("INDAWO: snapshotSites operation [cursor={}, limit={}, asOf={}]", cursor, limit, asOf);
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
