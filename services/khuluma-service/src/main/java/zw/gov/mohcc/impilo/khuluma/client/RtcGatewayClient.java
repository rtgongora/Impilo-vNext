package zw.gov.mohcc.impilo.khuluma.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for rtc-gateway-service (the calls/meetings media SoR over LiveKit). Khuluma reuses
 * {@code POST /internal/v1/rtc/sessions} to provision a room and {@code .../participants/token}
 * to mint a LiveKit access token — it does NOT build its own WebRTC signalling. All calls are
 * best-effort: when the gateway is unavailable or media is consent-gated, the result is
 * {@link RtcSessionResult#unavailable} and the call lifecycle still proceeds.
 */
@Component
public class RtcGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(RtcGatewayClient.class);
    private static final String PREFIX = "/internal/v1/rtc";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RtcGatewayClient(RestTemplate serviceRestTemplate,
                            @Value("${impilo.khuluma.rtc-gateway-base-url:http://localhost:8195}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Result of an rtc-gateway media operation; {@code available=false} when the gateway could not serve it. */
    public record RtcSessionResult(
            boolean available,
            String sessionId,
            String provider,
            String roomName,
            String roomUrl,
            String accessToken,
            String status,
            String error) {

        public static RtcSessionResult unavailable(String error) {
            return new RtcSessionResult(false, null, null, null, null, null, null, error);
        }
    }

    /**
     * Provision a media room for a Khuluma call. {@code consentReference} satisfies rtc-gateway's
     * media consent gate ({@code require-consent-reference-for-media}); for a native peer call the
     * call record itself is the mutual-consent artifact (caller initiates, callee accepts), so a
     * blank reference falls back to {@code khuluma-call:<sessionId>}.
     */
    public RtcSessionResult provision(String tenantId, String sessionId, String patientRef, String providerId,
                                      String purposeOfUse, String callType, String consentReference,
                                      String identity, String displayName, String role) {
        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("identity", identity);
        participant.put("displayName", displayName != null ? displayName : identity);
        participant.put("role", role != null ? role : "HOST");

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("tenantId", tenantId);
        req.put("sessionId", sessionId);
        req.put("patientId", patientRef);
        req.put("providerId", providerId);
        req.put("purposeOfUse", purposeOfUse != null ? purposeOfUse : "CARE_COORDINATION");
        req.put("sessionType", "VIDEO".equalsIgnoreCase(callType) ? "VIDEO" : "AUDIO");
        req.put("consentReference",
                consentReference != null && !consentReference.isBlank() ? consentReference : "khuluma-call:" + sessionId);
        req.put("participant", participant);

        return exchange("POST", PREFIX + "/sessions", req, "provision");
    }

    /** Mint a fresh participant token for an already-provisioned session. */
    public RtcSessionResult issueToken(String sessionId, String identity, String displayName, String role) {
        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("identity", identity);
        participant.put("displayName", displayName != null ? displayName : identity);
        participant.put("role", role != null ? role : "HOST");
        Map<String, Object> req = Map.of("participant", participant);
        return exchange("POST", PREFIX + "/sessions/" + sessionId + "/participants/token", req, "token");
    }

    private RtcSessionResult exchange(String method, String path, Object body, String op) {
        try {
            URI uri = URI.create(baseUrl + path);
            RequestEntity<Object> entity = RequestEntity
                    .method(org.springframework.http.HttpMethod.valueOf(method), uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            ResponseEntity<JsonNode> response = restTemplate.exchange(entity, JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().get("data") : null;
            if (data == null || data.isNull()) {
                return RtcSessionResult.unavailable("rtc-gateway returned no session data");
            }
            return new RtcSessionResult(
                    true,
                    text(data, "id"),
                    text(data, "provider"),
                    text(data, "roomName"),
                    text(data, "roomUrl"),
                    text(data, "accessToken"),
                    text(data, "status"),
                    null);
        } catch (RestClientException ex) {
            log.warn("rtc-gateway {} failed: {}", op, ex.getMessage());
            return RtcSessionResult.unavailable(ex.getMessage());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
