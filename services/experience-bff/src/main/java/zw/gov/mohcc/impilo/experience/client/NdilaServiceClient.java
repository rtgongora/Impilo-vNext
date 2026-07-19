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

    /** HPA facilities awaiting map-location confirmation (geocode review queue). */
    public JsonNode hpaGeocodeReviewQueue(int limit) {
        String url = baseUrl + "/internal/v1/locations/hpa-import/geocode-review-queue?limit=" + limit;
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
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

    /**
     * Anonymous street tile from ndila's public lane ({@code PublicStreetTileController},
     * gateway-public-lane-security ADR §3) — self-hosted Martin MVT street geometry, no
     * PII, no bearer token. Headers are service-originated defaults (same pattern as
     * {@code VarapiServiceClient.publicPractitionerVerify}); when the caller did send
     * platform headers the shared forwarding interceptor overwrites these defaults.
     *
     * @return raw tile bytes (typically gzipped MVT), or {@code null} for an empty tile
     *         (ndila 204 — preserved so the BFF controller can answer 204 too)
     */
    public byte[] publicStreetTile(int z, int x, int y) {
        String url = baseUrl + "/v1/public/ndila/tiles/" + z + "/" + x + "/" + y + ".mvt";
        ResponseEntity<byte[]> response = restTemplate.exchange(
                java.net.URI.create(url), org.springframework.http.HttpMethod.GET,
                anonymousSafeEntity(), byte[].class);
        byte[] body = response.getBody();
        return (body == null || body.length == 0) ? null : body;
    }

    /**
     * Anonymous public-lane request entity: downstream services enforce the mandatory
     * v1.1 trust headers, and anonymous public-gateway traffic arrives with trust
     * headers stripped at the edge — so the BFF supplies service-originated defaults.
     */
    private org.springframework.http.HttpEntity<Void> anonymousSafeEntity() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Tenant-ID", "00000000-0000-0000-0000-000000000001");
        headers.set("X-Pod-ID", "national-spine");
        headers.set("X-Actor-ID", "public-gateway");
        headers.set("X-Actor-Type", "SYSTEM");
        headers.set("X-Purpose-Of-Use", "PUBLIC_ACCESS");
        headers.set("X-Correlation-ID", java.util.UUID.randomUUID().toString());
        headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());
        return new org.springframework.http.HttpEntity<>(headers);
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
     * Road distance + ETA from one origin to a list of facility coordinates, via Ndila's
     * distance-matrix (the geography/routing SoR). Returns the raw
     * {@code DistanceMatrixResponse} node: {@code success}, {@code distancesMeters[0][j]} and
     * {@code durationsSeconds[0][j]} align 1:1 with the {@code destinations} order. Used by the
     * public "find care" orchestrator to rank service-matched facilities by travel distance.
     *
     * @param originLat    origin latitude
     * @param originLng    origin longitude
     * @param destinations facility coordinates in candidate order (each a
     *                     {@code {latitude, longitude}} map)
     */
    public JsonNode distanceMatrixFromOrigin(java.math.BigDecimal originLat,
                                             java.math.BigDecimal originLng,
                                             java.util.List<Map<String, Object>> destinations) {
        String url = baseUrl + "/api/v1/ndila/distance-matrix";
        Map<String, Object> origin = new java.util.LinkedHashMap<>();
        origin.put("latitude", originLat);
        origin.put("longitude", originLng);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("origins", java.util.List.of(origin));
        body.put("destinations", destinations);
        body.put("mode", "DRIVING");
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    // ── Catchment resolution (R6/G4) — ndila is the geography SoR ─────────────

    /** Which catchments contain a point ({@code {point:{latitude,longitude}}}). */
    public JsonNode catchmentCheckPoint(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/catchments/check-point";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    /** Nearest service locations to a point ({@code {point, locationType, limit}}). */
    public JsonNode catchmentNearestService(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/catchments/nearest-service";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    /** Coverage summary for an owner ({@code {ownerService, ownerEntityId}}). */
    public JsonNode catchmentCoverageSummary(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/catchments/coverage-summary";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    public JsonNode catchmentsByOwner(String ownerService, String ownerEntityId) {
        String url = baseUrl + "/api/v1/ndila/catchments/by-owner/" + ownerService + "/" + ownerEntityId;
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    public JsonNode locationsByOwner(String ownerService, String ownerEntityId) {
        String url = baseUrl + "/api/v1/ndila/locations/by-owner/" + ownerService + "/" + ownerEntityId;
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    public JsonNode getLocation(String locationId) {
        String url = baseUrl + "/api/v1/ndila/locations/" + locationId;
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    public JsonNode createCatchment(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/catchments";
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
