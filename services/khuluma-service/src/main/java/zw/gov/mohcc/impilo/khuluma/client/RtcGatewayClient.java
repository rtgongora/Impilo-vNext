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
        // Stamp ownership explicitly: AUDIO/VIDEO resolve to the TELEMEDICINE template whose
        // owningService is PCT — without this, impilo.rtc.* events for khuluma calls would be
        // mis-attributed and the KHULUMA media-truth consumer would never see them.
        req.put("owningService", "KHULUMA");
        req.put("owningRef", sessionId);
        req.put("consentReference",
                consentReference != null && !consentReference.isBlank() ? consentReference : "khuluma-call:" + sessionId);
        req.put("participant", participant);

        return exchange("POST", PREFIX + "/sessions", req, "provision");
    }

    /**
     * Mint a fresh participant token for an already-provisioned session. For lobby-gated
     * templates (MEETING KNOCK) the result may carry {@code status=WAITING|DENIED} with no
     * token — the caller must treat that as a lobby outcome, not an error.
     */
    public RtcSessionResult issueToken(String sessionId, String identity, String displayName, String role) {
        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("identity", identity);
        participant.put("displayName", displayName != null ? displayName : identity);
        participant.put("role", role != null ? role : "HOST");
        Map<String, Object> req = Map.of("participant", participant);
        return exchange("POST", PREFIX + "/sessions/" + sessionId + "/participants/token", req, "token");
    }

    /** One lobby-tracked participant of an rtc session (rtc.session_participants row). */
    public record RtcLobbyParticipant(String identity, String displayName, String role, String state,
                                      String requestedAt, String admittedAt, String admittedBy,
                                      String deniedReason) {}

    /** Admit a WAITING lobby participant (idempotent on the gateway side). */
    public boolean admit(String sessionId, String identity, String decidedBy) {
        return lobbyDecision(sessionId, identity, "admit",
                decidedBy == null ? Map.of() : Map.of("actor", decidedBy));
    }

    /** Deny a WAITING lobby participant (idempotent on the gateway side). */
    public boolean deny(String sessionId, String identity, String reason) {
        return lobbyDecision(sessionId, identity, "deny",
                reason == null ? Map.of() : Map.of("reason", reason));
    }

    private boolean lobbyDecision(String sessionId, String identity, String op, Map<String, Object> body) {
        try {
            URI uri = URI.create(baseUrl + PREFIX + "/sessions/" + sessionId + "/participants/"
                    + java.net.URLEncoder.encode(identity, java.nio.charset.StandardCharsets.UTF_8) + "/" + op);
            RequestEntity<Object> entity = RequestEntity.post(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            restTemplate.exchange(entity, JsonNode.class);
            return true;
        } catch (RestClientException ex) {
            log.warn("rtc-gateway lobby {} failed for session {} identity {}: {}",
                    op, sessionId, identity, ex.getMessage());
            return false;
        }
    }

    /** The gateway's live lobby roster for a session; empty on gateway failure (degrade honestly). */
    public java.util.List<RtcLobbyParticipant> listParticipants(String sessionId) {
        try {
            URI uri = URI.create(baseUrl + PREFIX + "/sessions/" + sessionId + "/participants");
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(uri, JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().get("data") : null;
            if (data == null || !data.isArray()) {
                return java.util.List.of();
            }
            java.util.List<RtcLobbyParticipant> out = new java.util.ArrayList<>();
            for (JsonNode row : data) {
                out.add(new RtcLobbyParticipant(
                        text(row, "identity"), text(row, "displayName"), text(row, "role"),
                        text(row, "state"), text(row, "requestedAt"), text(row, "admittedAt"),
                        text(row, "admittedBy"), text(row, "deniedReason")));
            }
            return out;
        } catch (RestClientException ex) {
            log.warn("rtc-gateway list participants failed for session {}: {}", sessionId, ex.getMessage());
            return java.util.List.of();
        }
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
