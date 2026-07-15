package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

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

    private static final String PUBLIC_DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    // ── Emergency requests (SOS) ─────────────────────────────────────
    public JsonNode createRequest(Map<String, Object> body) {
        return post(baseUrl + API + "/requests", body, "createRequest");
    }

    /**
     * Anonymous public-lane SOS intake (gateway ADR anonymous-write exception). The inbound public
     * request carries no user trust context, so the BFF supplies service-originated headers here
     * (mirrors {@code TusoServiceClient.anonymousSafeEntity}) plus, when available, the BFF's own
     * service-account bearer so daidzai authenticates the BFF as a SYSTEM caller (same "pre-auth
     * hop" pattern as {@code AuthContactOtpController.serviceAccountHeaders}). Daidzai owns the
     * emergency truth and applies the PD-3 callback gate; the BFF never fabricates a dispatch.
     *
     * @param serviceAccountBearer raw access token, or {@code null} when Keycloak is unreachable
     */
    public JsonNode createPublicSosRequest(Map<String, Object> body, String serviceAccountBearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CompanionHeaders.TENANT_ID, PUBLIC_DEFAULT_TENANT);
        headers.set(CompanionHeaders.POD_ID, "national-spine");
        headers.set(CompanionHeaders.ACTOR_ID, "public-gateway");
        headers.set(CompanionHeaders.ACTOR_TYPE, "SYSTEM");
        headers.set(CompanionHeaders.PURPOSE_OF_USE, "EMERGENCY_INTAKE");
        headers.set(CompanionHeaders.CORRELATION_ID, UUID.randomUUID().toString());
        headers.set(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
        headers.set(CompanionHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        if (serviceAccountBearer != null && !serviceAccountBearer.isBlank()) {
            headers.set(CompanionHeaders.AUTHORIZATION,
                    CompanionHeaders.BEARER_PREFIX + serviceAccountBearer);
        }
        log.debug("DAIDZAI createPublicSosRequest: POST {}{}/requests (bearer={})",
                baseUrl, API, serviceAccountBearer != null);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + API + "/requests", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
        return response.getBody();
    }

    public JsonNode getRequest(String id) {
        return get(baseUrl + API + "/requests/" + id, "getRequest");
    }

    /** Call-taker queue: pending (or status-filtered) SOS requests. WU5/WU6 passthrough. */
    public JsonNode listRequests(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/requests");
        if (status != null && !status.isBlank()) b.queryParam("status", status);
        return get(b.toUriString(), "listRequests");
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
    // ── Assistance cases (crowdfunding — Daidzai owns the case, mushe the money) ──

    public JsonNode createAssistance(Map<String, Object> body) {
        return post(baseUrl + API + "/assistance", body, "createAssistance");
    }

    public JsonNode listPublicAssistance() {
        return get(baseUrl + API + "/assistance", "listPublicAssistance");
    }

    public JsonNode listMyAssistance() {
        return get(baseUrl + API + "/assistance/mine", "listMyAssistance");
    }

    public JsonNode getAssistance(String id) {
        return get(baseUrl + API + "/assistance/" + id, "getAssistance");
    }

    public JsonNode assistanceSubResource(String id, String sub) {
        return get(baseUrl + API + "/assistance/" + id + "/" + sub, "assistance:" + sub);
    }

    public JsonNode assistanceAction(String id, String action, Map<String, Object> body) {
        return post(baseUrl + API + "/assistance/" + id + "/" + action, body, "assistance:" + action);
    }

    // ── EMS clinical dispatch + prehospital ePCR (responder mobile app) ──────
    public JsonNode emsDispatch(String incidentId, Map<String, Object> body) {
        return post(baseUrl + API + "/ems/incidents/" + incidentId + "/dispatch", body, "emsDispatch");
    }

    public JsonNode emsAdvance(String missionId, Map<String, Object> body) {
        return post(baseUrl + API + "/ems/missions/" + missionId + "/advance", body, "emsAdvance");
    }

    public JsonNode emsMission(String missionId) {
        return get(baseUrl + API + "/ems/missions/" + missionId, "emsMission");
    }

    public JsonNode emsMissionByIncident(String incidentId) {
        return get(baseUrl + API + "/ems/incidents/" + incidentId + "/mission", "emsMissionByIncident");
    }

    public JsonNode epcrUpsert(String missionId, Map<String, Object> body) {
        return post(baseUrl + API + "/ems/missions/" + missionId + "/epcr", body, "epcrUpsert");
    }

    public JsonNode epcrEvent(String missionId, Map<String, Object> body) {
        return post(baseUrl + API + "/ems/missions/" + missionId + "/epcr/events", body, "epcrEvent");
    }

    public JsonNode epcr(String missionId) {
        return get(baseUrl + API + "/ems/missions/" + missionId + "/epcr", "epcr");
    }

    /** Trauma-episode timeline read-model (episode + ordered phases). WU5 passthrough. */
    public JsonNode traumaEpisode(String episodeId) {
        return get(baseUrl + API + "/trauma-episodes/" + episodeId, "traumaEpisode");
    }

    private JsonNode get(String url, String op) {
        log.debug("DAIDZAI {}: GET {}", op, url);
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    private JsonNode post(String url, Map<String, Object> body, String op) {
        log.debug("DAIDZAI {}: POST {}", op, url);
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }
}
