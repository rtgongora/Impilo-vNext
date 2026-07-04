package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

@Component
public class RtcGatewayServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RtcGatewayServiceClient.class);
    private static final String API = "/internal/v1/rtc";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RtcGatewayServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.rtcGatewayBaseUrl();
    }

    public JsonNode provisionSession(Map<String, Object> body) {
        log.info("RTC: provision session");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + API + "/sessions",
                new HttpEntity<>(body, idempotency("rtc-provision:" + body.getOrDefault("sessionId", ""))),
                JsonNode.class);
        return extractData(response);
    }

    public JsonNode getSession(String sessionId) {
        log.debug("RTC: get session id={}", sessionId);
        return extractData(restTemplate.getForEntity(baseUrl + API + "/sessions/" + sessionId, JsonNode.class));
    }

    public JsonNode issueParticipantToken(String sessionId, Map<String, Object> body) {
        log.info("RTC: issue token sessionId={}", sessionId);
        Object participant = body.get("participant");
        String identity = participant instanceof Map<?, ?> p && p.get("identity") != null
                ? p.get("identity").toString() : "";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + API + "/sessions/" + sessionId + "/participants/token",
                new HttpEntity<>(body, idempotency(
                        "rtc-token:" + sessionId + ":" + identity + ":" + System.currentTimeMillis())),
                JsonNode.class);
        return extractData(response);
    }

    /** Refresh a previously issued participant token (provider or admitted participant). */
    public JsonNode refreshParticipantToken(String sessionId, Map<String, Object> body) {
        log.info("RTC: refresh token sessionId={}", sessionId);
        Object participant = body.get("participant");
        String identity = participant instanceof Map<?, ?> p && p.get("identity") != null
                ? p.get("identity").toString() : "";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + API + "/sessions/" + sessionId + "/participants/token/refresh",
                new HttpEntity<>(body, idempotency(
                        "rtc-token-refresh:" + sessionId + ":" + identity + ":" + System.currentTimeMillis())),
                JsonNode.class);
        return extractData(response);
    }

    /** All participants for a session — [{identity, displayName, role, state, requestedAt, admittedAt}]. */
    public JsonNode listParticipants(String sessionId) {
        log.debug("RTC: list participants sessionId={}", sessionId);
        return extractData(restTemplate.getForEntity(
                baseUrl + API + "/sessions/" + sessionId + "/participants", JsonNode.class));
    }

    /** Admit a WAITING participant into the room. */
    public JsonNode admitParticipant(String sessionId, String identity, Map<String, Object> body) {
        log.info("RTC: admit participant sessionId={} identity={}", sessionId, identity);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + API + "/sessions/" + sessionId + "/participants/" + identity + "/admit",
                new HttpEntity<>(body == null ? Map.of() : body,
                        idempotency("rtc-admit:" + sessionId + ":" + identity)),
                JsonNode.class);
        return extractData(response);
    }

    /** Deny a WAITING participant ({reason?}). */
    public JsonNode denyParticipant(String sessionId, String identity, Map<String, Object> body) {
        log.info("RTC: deny participant sessionId={} identity={}", sessionId, identity);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + API + "/sessions/" + sessionId + "/participants/" + identity + "/deny",
                new HttpEntity<>(body == null ? Map.of() : body,
                        idempotency("rtc-deny:" + sessionId + ":" + identity)),
                JsonNode.class);
        return extractData(response);
    }

    /** Recordings captured for a session. */
    public JsonNode listRecordings(String sessionId) {
        log.debug("RTC: list recordings sessionId={}", sessionId);
        return extractData(restTemplate.getForEntity(
                baseUrl + API + "/sessions/" + sessionId + "/recordings", JsonNode.class));
    }

    public JsonNode endSession(String sessionId) {
        log.info("RTC: end session id={}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + API + "/sessions/" + sessionId + "/end",
                new HttpEntity<>(Map.of(), idempotency("rtc-end:" + sessionId)),
                JsonNode.class);
        return extractData(response);
    }

    /** Distinct idempotency key per downstream mutation (see ServiceClientConfig). */
    private static org.springframework.http.HttpHeaders idempotency(String key) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(zw.gov.mohcc.impilo.companion.context.CompanionHeaders.IDEMPOTENCY_KEY, key);
        return headers;
    }

    public JsonNode getOpsHealth() {
        log.debug("RTC: ops health");
        return extractData(restTemplate.getForEntity(baseUrl + API + "/ops/health", JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
