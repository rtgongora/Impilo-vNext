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
 * Client for live-service (the live events / meetings SoR). Khuluma orchestrates multi-party
 * virtual meetings + live events here rather than re-implementing them: it creates an event, and
 * participants join a room whose media token comes from live-service's rtc-gateway provider
 * (self-hosted LiveKit). Khuluma never owns the meeting media or registry — it links a conversation
 * to the live event and drives the lifecycle. Best-effort: a down live-service degrades honestly.
 */
@Component
public class LiveServiceClient {

    private static final Logger log = LoggerFactory.getLogger(LiveServiceClient.class);
    private static final String EVENTS = "/internal/v1/live/events";
    private static final String ROOM = "/internal/v1/live/room";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public LiveServiceClient(RestTemplate serviceRestTemplate,
                             @Value("${impilo.khuluma.live-base-url:http://localhost:8380}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Result of creating a live event; {@code eventId} is the meeting handle. */
    public record CreateEventResult(boolean available, String eventId, String status, String error) {
        public static CreateEventResult unavailable(String error) {
            return new CreateEventResult(false, null, null, error);
        }
    }

    /** A participant's media join for a meeting. */
    public record RoomTokenResult(boolean available, String roomId, String roomUrl, String accessToken,
                                  String provider, String channel, String error) {
        public static RoomTokenResult unavailable(String error) {
            return new RoomTokenResult(false, null, null, null, null, null, error);
        }
    }

    /** Create a virtual meeting / live event owned by Khuluma (the conversation is its owning entity). */
    public CreateEventResult createEvent(String title, String mode, String eventType,
                                         String owningEntityId, String organiserType, String organiserId,
                                         Integer maxParticipants) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("title", title);
        req.put("mode", mode != null ? mode : "VIRTUAL");
        req.put("eventType", eventType != null ? eventType : "MEETING");
        req.put("owningService", "khuluma-service");
        req.put("owningEntityId", owningEntityId);
        req.put("organiserType", organiserType != null ? organiserType : "PROVIDER");
        req.put("organiserId", organiserId);
        req.put("registrationRequired", false);
        if (maxParticipants != null) {
            req.put("maxParticipants", maxParticipants);
        }
        JsonNode body = post(EVENTS, req);
        if (body == null) {
            return CreateEventResult.unavailable("live-service unavailable");
        }
        return new CreateEventResult(true, text(body, "id"), text(body, "status"), null);
    }

    /** Ensure the room exists for an event (idempotent provisioning of the media session). */
    public void joinRoom(String eventId, String participantId, String participantType, String role) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("participantId", participantId);
        req.put("participantType", participantType != null ? participantType : "PROVIDER");
        req.put("role", role != null ? role : "ATTENDEE");
        req.put("consentGranted", true);
        post(ROOM + "/" + eventId + "/join", req);
    }

    /** Mint this participant's media token for the meeting (real LiveKit token via live-service). */
    public RoomTokenResult issueToken(String eventId, String participantId, String role) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("participantId", participantId);
        req.put("role", role != null ? role : "ATTENDEE");
        JsonNode body = post(ROOM + "/" + eventId + "/token", req);
        if (body == null) {
            return RoomTokenResult.unavailable("live-service unavailable");
        }
        return new RoomTokenResult(true, text(body, "roomId"), text(body, "roomUrl"),
                text(body, "accessToken"), text(body, "provider"), text(body, "channel"), null);
    }

    /** End the meeting's media session. */
    public void endRoom(String eventId) {
        post(ROOM + "/" + eventId + "/end", Map.of());
    }

    private JsonNode post(String path, Object body) {
        try {
            RequestEntity<Object> entity = RequestEntity
                    .post(URI.create(baseUrl + path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            ResponseEntity<JsonNode> response = restTemplate.exchange(entity, JsonNode.class);
            JsonNode payload = response.getBody();
            if (payload != null && payload.has("data") && !payload.get("data").isNull()) {
                return payload.get("data"); // tolerate either bare or {data:...}
            }
            return payload;
        } catch (RestClientException ex) {
            log.warn("live-service POST {} failed: {}", path, ex.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
