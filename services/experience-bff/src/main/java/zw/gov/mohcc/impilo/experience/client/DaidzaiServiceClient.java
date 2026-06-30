package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for daidzai-service — emergency, disaster &amp; public-health response command.
 *
 * <p>Thin pass-through: the shared {@code serviceRestTemplate} forwards the inbound v1.1 trust
 * headers (tenant/pod/request/correlation/actor + Authorization + Idempotency-Key) so the
 * downstream companion filter sees the same trust context as the BFF caller. The BFF owns no
 * emergency truth — Daidzai does.</p>
 */
@Component
public class DaidzaiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DaidzaiServiceClient.class);
    private static final String API = "/internal/v1/daidzai";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DaidzaiServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.daidzaiBaseUrl();
    }

    // ── Emergency requests (SOS) ─────────────────────────────────────
    public JsonNode createRequest(Map<String, Object> body) {
        return post(baseUrl + API + "/requests", body, "createRequest");
    }

    public JsonNode getRequest(String id) {
        return get(baseUrl + API + "/requests/" + id, "getRequest");
    }

    public JsonNode triageRequest(String id) {
        return post(baseUrl + API + "/requests/" + id + "/triage", Map.of(), "triageRequest");
    }

    // ── Incidents ────────────────────────────────────────────────────
    public JsonNode createIncident(Map<String, Object> body) {
        return post(baseUrl + API + "/incidents", body, "createIncident");
    }

    public JsonNode listIncidents(String type, String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/incidents");
        if (type != null && !type.isBlank()) b.queryParam("type", type);
        if (status != null && !status.isBlank()) b.queryParam("status", status);
        return get(b.toUriString(), "listIncidents");
    }

    public JsonNode getIncident(String id) {
        return get(baseUrl + API + "/incidents/" + id, "getIncident");
    }

    public JsonNode dispatch(String id, Map<String, Object> body) {
        return post(baseUrl + API + "/incidents/" + id + "/dispatch", body, "dispatch");
    }

    public JsonNode missionEvent(String id, Map<String, Object> body) {
        return post(baseUrl + API + "/incidents/" + id + "/mission-events", body, "missionEvent");
    }

    public JsonNode missions(String id) {
        return get(baseUrl + API + "/incidents/" + id + "/missions", "missions");
    }

    public JsonNode handoff(String id, Map<String, Object> body) {
        return post(baseUrl + API + "/incidents/" + id + "/handoff", body, "handoff");
    }

    public JsonNode requestResource(String id, Map<String, Object> body) {
        return post(baseUrl + API + "/incidents/" + id + "/resources", body, "requestResource");
    }

    public JsonNode resources(String id) {
        return get(baseUrl + API + "/incidents/" + id + "/resources", "resources");
    }

    // ── Disasters / MCI command ──────────────────────────────────────
    public JsonNode declareDisaster(Map<String, Object> body) {
        return post(baseUrl + API + "/disasters", body, "declareDisaster");
    }

    public JsonNode listDisasters() {
        return get(baseUrl + API + "/disasters", "listDisasters");
    }

    public JsonNode addSite(String id, Map<String, Object> body) {
        return post(baseUrl + API + "/disasters/" + id + "/sites", body, "addSite");
    }

    public JsonNode sites(String id) {
        return get(baseUrl + API + "/disasters/" + id + "/sites", "sites");
    }

    public JsonNode closeDisaster(String id, Map<String, Object> body) {
        return post(baseUrl + API + "/disasters/" + id + "/close", body, "closeDisaster");
    }

    // ── helpers ──────────────────────────────────────────────────────
    private JsonNode get(String url, String op) {
        log.debug("DAIDZAI {}: GET {}", op, url);
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    private JsonNode post(String url, Map<String, Object> body, String op) {
        log.debug("DAIDZAI {}: POST {}", op, url);
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }
}
