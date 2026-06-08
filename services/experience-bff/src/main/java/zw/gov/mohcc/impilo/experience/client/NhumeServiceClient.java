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

/**
 * HTTP client for nhume-service — mirrors {@code ui/one-ui-shell/src/lib/nhume.ts} surface.
 */
@Component
public class NhumeServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NhumeServiceClient.class);
    private static final String INTERNAL_PREFIX = "/internal/v1/nhume";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NhumeServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.nhumeBaseUrl();
    }

    public JsonNode listDeliveries(String status, String facilityId, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + INTERNAL_PREFIX + "/deliveries")
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        if (facilityId != null && !facilityId.isBlank()) {
            builder.queryParam("facility_id", facilityId);
        }
        return get(builder.toUriString());
    }

    public JsonNode getDashboard() {
        return get(baseUrl + INTERNAL_PREFIX + "/dashboard");
    }

    public JsonNode getDispatcherConsole() {
        return get(baseUrl + INTERNAL_PREFIX + "/dispatcher-console");
    }

    public JsonNode getDelivery(String deliveryId) {
        return get(baseUrl + INTERNAL_PREFIX + "/deliveries/" + deliveryId);
    }

    public JsonNode getDeliverySubresource(String deliveryId, String sub) {
        return get(baseUrl + INTERNAL_PREFIX + "/deliveries/" + deliveryId + "/" + sub);
    }

    public JsonNode postDeliveryAction(String deliveryId, String action, Object body) {
        return post(baseUrl + INTERNAL_PREFIX + "/deliveries/" + deliveryId + "/" + action, body);
    }

    public JsonNode createDelivery(Object body) {
        return post(baseUrl + INTERNAL_PREFIX + "/deliveries", body);
    }

    public JsonNode getZones() {
        return get(baseUrl + INTERNAL_PREFIX + "/zones");
    }

    public JsonNode getMapToken() {
        return get(baseUrl + INTERNAL_PREFIX + "/map-token");
    }

    public JsonNode listFleet() {
        return get(baseUrl + INTERNAL_PREFIX + "/fleet");
    }

    public JsonNode getFleetAsset(String id) {
        return get(baseUrl + INTERNAL_PREFIX + "/fleet/" + id);
    }

    public JsonNode createFleetAsset(Object body) {
        return post(baseUrl + INTERNAL_PREFIX + "/fleet", body);
    }

    public JsonNode patchFleetAsset(String id, Object body) {
        return patch(baseUrl + INTERNAL_PREFIX + "/fleet/" + id, body);
    }

    public JsonNode listCouriers() {
        return get(baseUrl + INTERNAL_PREFIX + "/couriers");
    }

    public JsonNode getCourier(String id) {
        return get(baseUrl + INTERNAL_PREFIX + "/couriers/" + id);
    }

    public JsonNode createCourier(Object body) {
        return post(baseUrl + INTERNAL_PREFIX + "/couriers", body);
    }

    public JsonNode patchCourier(String id, Object body) {
        return patch(baseUrl + INTERNAL_PREFIX + "/couriers/" + id, body);
    }

    public JsonNode listPolicies() {
        return get(baseUrl + INTERNAL_PREFIX + "/policies");
    }

    public JsonNode createPolicy(Object body) {
        return post(baseUrl + INTERNAL_PREFIX + "/policies", body);
    }

    public JsonNode patchPolicy(String code, Object body) {
        return patch(baseUrl + INTERNAL_PREFIX + "/policies/" + code, body);
    }

    public JsonNode listIntegrations() {
        return get(baseUrl + INTERNAL_PREFIX + "/integrations");
    }

    public JsonNode createIntegration(Object body) {
        return post(baseUrl + INTERNAL_PREFIX + "/integrations", body);
    }

    public JsonNode listMissions() {
        return get(baseUrl + INTERNAL_PREFIX + "/autonomous-missions");
    }

    public JsonNode getMission(String id) {
        return get(baseUrl + INTERNAL_PREFIX + "/autonomous-missions/" + id);
    }

    public JsonNode createMission(Object body) {
        return post(baseUrl + INTERNAL_PREFIX + "/autonomous-missions", body);
    }

    public JsonNode cancelMission(String id, Object body) {
        return post(baseUrl + INTERNAL_PREFIX + "/autonomous-missions/" + id + "/cancel", body);
    }

    private JsonNode get(String url) {
        log.debug("Nhume GET {}", url);
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    private JsonNode post(String url, Object body) {
        log.debug("Nhume POST {}", url);
        Object payload = body != null ? body : Map.of();
        return restTemplate.postForEntity(url, payload, JsonNode.class).getBody();
    }

    private JsonNode patch(String url, Object body) {
        log.debug("Nhume PATCH {}", url);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PATCH, new HttpEntity<>(body != null ? body : Map.of()), JsonNode.class);
        return response.getBody();
    }
}
