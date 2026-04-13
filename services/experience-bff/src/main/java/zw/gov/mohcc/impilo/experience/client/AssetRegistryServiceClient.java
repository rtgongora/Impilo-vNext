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

import java.util.UUID;

/**
 * HTTP client for {@code asset-registry-service}.
 */
@Component
public class AssetRegistryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AssetRegistryServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AssetRegistryServiceClient(RestTemplate serviceRestTemplate,
                                      ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.assetRegistryBaseUrl();
    }

    public JsonNode listAssets(String facilityId, String status, int cursor, int limit) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/assets")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit);
        if (facilityId != null && !facilityId.isBlank()) {
            b.queryParam("facility_id", facilityId);
        }
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return getJson(b.toUriString());
    }

    public JsonNode getAsset(UUID assetId) {
        return getJson(baseUrl + "/internal/v1/assets/" + assetId);
    }

    public JsonNode snapshotAssets(int cursor, int limit, String asOf) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/snapshots/assets")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit);
        if (asOf != null && !asOf.isBlank()) {
            b.queryParam("as_of", asOf);
        }
        return getJson(b.toUriString());
    }

    public JsonNode upsertAsset(UUID assetId, JsonNode body) {
        return exchangeJson(HttpMethod.PUT, baseUrl + "/internal/v1/assets/" + assetId, body);
    }

    public JsonNode updateAssetStatus(UUID assetId, JsonNode body) {
        return exchangeJson(HttpMethod.PUT, baseUrl + "/internal/v1/assets/" + assetId + "/status", body);
    }

    public JsonNode deleteAsset(UUID assetId) {
        log.debug("Asset DELETE {}", assetId);
        ResponseEntity<JsonNode> r = restTemplate.exchange(
                baseUrl + "/internal/v1/assets/" + assetId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, null),
                JsonNode.class);
        return r.getBody();
    }

    public JsonNode getFixedAssetDetails(UUID assetId) {
        return getJson(baseUrl + "/internal/v1/fixed-assets/assets/" + assetId + "/details");
    }

    public JsonNode getFixedAssetDepreciationSchedule(UUID assetId) {
        return getJson(baseUrl + "/internal/v1/fixed-assets/assets/" + assetId + "/depreciation/schedule");
    }

    public JsonNode upsertFixedAssetDetails(UUID assetId, JsonNode body) {
        return exchangeJson(HttpMethod.PUT, baseUrl + "/internal/v1/fixed-assets/assets/" + assetId + "/details", body);
    }

    private JsonNode getJson(String url) {
        log.debug("Asset registry GET {}", url);
        ResponseEntity<JsonNode> r = restTemplate.getForEntity(url, JsonNode.class);
        return r.getBody();
    }

    private JsonNode exchangeJson(HttpMethod method, String url, JsonNode body) {
        log.debug("Asset registry {} {}", method, url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JsonNode> entity = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> r = restTemplate.exchange(url, method, entity, JsonNode.class);
        return r.getBody();
    }
}
