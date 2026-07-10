package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

@Component
public class NdilaServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public NdilaServiceClient(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints,
            ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.ndilaBaseUrl();
        this.objectMapper = objectMapper;
    }

    public JsonNode tileConfig() {
        String url = baseUrl + "/api/v1/ndila/tiles/config";
        JsonNode body = restTemplate.getForEntity(url, JsonNode.class).getBody();
        return rewriteTileConfigForBrowser(body);
    }

    public byte[] tilePng(int z, int x, int y) {
        String url = baseUrl + "/api/v1/ndila/tiles/" + z + "/" + x + "/" + y + ".png";
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        return response.getBody() == null ? new byte[0] : response.getBody();
    }

    public byte[] tileVector(int z, int x, int y) {
        String url = baseUrl + "/api/v1/ndila/tiles/" + z + "/" + x + "/" + y + ".mvt";
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        return response.getBody() == null ? new byte[0] : response.getBody();
    }

    public JsonNode nearbyAssets(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/tracking/assets/nearby";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    public JsonNode geocode(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/geocode";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    public JsonNode reverseGeocode(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/reverse-geocode";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    public JsonNode syncFacilityMasterSeed(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/facilities/sync-master-seed";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    public JsonNode geocodeReviewQueue() {
        String url = baseUrl + "/api/v1/ndila/facilities/geocode-review-queue";
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    public JsonNode spatialNearby(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/spatial/nearby";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    public JsonNode spatialNearest(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/spatial/nearest";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    /**
     * Browser-facing tile URLs must be same-origin BFF paths so MapLibre can load
     * raster pyramids without CORS or direct ndila-service exposure.
     */
    static JsonNode rewriteTileConfigForBrowser(JsonNode body) {
        if (body == null || !body.isObject()) {
            return body;
        }
        ObjectNode node = (ObjectNode) body;
        String provider = node.path("providerName").asText("");
        String template = node.path("tileUrlTemplate").asText("");
        if ("PREVIEW_SOVEREIGN".equals(provider)
                || "OSM_OSRM".equals(provider)
                || template.startsWith("/api/v1/ndila/tiles/")
                || template.startsWith("mock://")) {
            node.put("tileUrlTemplate", "/internal/v1/ndila/tiles/{z}/{x}/{y}.png");
            if (node.hasNonNull("vectorTileUrlTemplate")) {
                node.put("vectorTileUrlTemplate", "/internal/v1/ndila/tiles/{z}/{x}/{y}.mvt");
            }
            if ("mock://".equals(template) || template.startsWith("mock://")) {
                node.put("providerName", "PREVIEW_SOVEREIGN");
                node.put("attribution", "Impilo Ndila — sovereign preview tiles");
            } else if ("OSM_OSRM".equals(provider)) {
                node.put("attribution", "© OpenStreetMap contributors — self-hosted via Ndila");
            }
        }
        return node;
    }
}
