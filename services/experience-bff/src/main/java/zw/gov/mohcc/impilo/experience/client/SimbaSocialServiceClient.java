package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * BFF client for the Simba wellness-SOCIAL sovereign endpoints (/internal/v1/wellness/social/**).
 * Kept separate from the generic community-service social client. Used by the reel-upload composition
 * (register media asset + create reel after the MinIO write) and social read/write proxying where a
 * composite is needed.
 */
@Component
public class SimbaSocialServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SimbaSocialServiceClient(RestTemplate serviceRestTemplate,
                                    ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.simbaBaseUrl();
    }

    public JsonNode registerMediaAsset(Map<String, Object> request) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/wellness/social/media-assets", request, JsonNode.class));
    }

    public JsonNode createReel(Map<String, Object> request) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/wellness/social/reels", request, JsonNode.class));
    }

    public JsonNode reelFeed(Long cursor, int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/social/reels")
                .queryParam("limit", limit)
                .queryParamIfPresent("cursor", java.util.Optional.ofNullable(cursor))
                .encode().toUriString();
        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        return resp.getBody();
    }

    public JsonNode getReel(String reelId) {
        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(
                baseUrl + "/internal/v1/wellness/social/reels/" + reelId, JsonNode.class);
        return resp.getBody();
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
