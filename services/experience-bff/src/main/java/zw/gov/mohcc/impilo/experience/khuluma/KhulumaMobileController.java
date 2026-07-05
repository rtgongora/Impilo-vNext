package zw.gov.mohcc.impilo.experience.khuluma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;

import java.util.Map;

/**
 * Mobile surface for the Khuluma Comms Hub ({@code /internal/v1/mobile/khuluma/**}) — a focused
 * subset of the web BFF for the citizen/provider apps (inbox, conversation, send, read, presence,
 * notifications, 1:1 calls). Same client, same policy pre-checks, same status relay.
 */
@RestController
@RequestMapping("/internal/v1/mobile/khuluma")
public class KhulumaMobileController {

    private final KhulumaServiceClient khuluma;
    private final NotificationServiceClient notifications;
    private final KhulumaAccessPolicyService policy;
    private final ObjectMapper objectMapper;

    public KhulumaMobileController(KhulumaServiceClient khuluma,
                                   NotificationServiceClient notifications,
                                   KhulumaAccessPolicyService policy,
                                   ObjectMapper objectMapper) {
        this.khuluma = khuluma;
        this.notifications = notifications;
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/summary")
    public ResponseEntity<JsonNode> summary(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        KhulumaServiceClient.Result unread = khuluma.get("/unread-count", Map.of());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("messages", unread.body());
        try {
            body.set("notifications", notifications.unreadCount(actorId));
        } catch (Exception ignored) {
            body.putObject("notifications");
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/inbox")
    public ResponseEntity<JsonNode> inbox() {
        return relay(khuluma.get("/conversations", Map.of()));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<JsonNode> conversation(@PathVariable String id) {
        return relay(khuluma.get("/conversations/" + id, Map.of()));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<JsonNode> messages(@PathVariable String id) {
        return relay(khuluma.get("/conversations/" + id + "/messages", Map.of()));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<JsonNode> send(@PathVariable String id, @RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.post("/conversations/" + id + "/messages", body));
    }

    @PostMapping("/conversations/{id}/messages/read")
    public ResponseEntity<JsonNode> markRead(@PathVariable String id, @RequestBody JsonNode body) {
        return relay(khuluma.post("/conversations/" + id + "/messages/read", body));
    }

    @GetMapping("/notifications")
    public ResponseEntity<JsonNode> notifications(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        try {
            return ResponseEntity.ok(notifications.listNotifications(actorId));
        } catch (Exception ex) {
            return ResponseEntity.status(502).body(objectMapper.createObjectNode());
        }
    }

    @PutMapping("/presence")
    public ResponseEntity<JsonNode> presence(@RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.put("/presence", body));
    }

    @PostMapping("/meetings")
    public ResponseEntity<JsonNode> createMeeting(@RequestBody JsonNode body) {
        policy.requireCallActor();
        return relay(khuluma.post("/meetings", body));
    }

    @PostMapping("/conversations/{id}/meeting/join")
    public ResponseEntity<JsonNode> joinMeeting(@PathVariable String id) {
        policy.requireCallActor();
        return relay(khuluma.post("/conversations/" + id + "/meeting/join", objectMapper.createObjectNode()));
    }

    @PostMapping("/conversations/{id}/meeting/end")
    public ResponseEntity<JsonNode> endMeeting(@PathVariable String id) {
        return relay(khuluma.post("/conversations/" + id + "/meeting/end", objectMapper.createObjectNode()));
    }

    // ── W5 MEETING: mobile is JOIN_CAPABLE (template mobileParity) — join + lobby-wait +
    // detail + hand/reactions + invite resolve. Notes/agenda editing, cohost management and
    // lobby moderation stay web-only (parity note in the meeting screen).

    @GetMapping("/conversations/{id}/meeting")
    public ResponseEntity<JsonNode> meetingDetail(@PathVariable String id) {
        return relay(khuluma.get("/conversations/" + id + "/meeting", Map.of()));
    }

    @PostMapping("/conversations/{id}/meeting/hand")
    public ResponseEntity<JsonNode> raiseHand(@PathVariable String id, @RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.post("/conversations/" + id + "/meeting/hand", body));
    }

    @PostMapping("/conversations/{id}/meeting/reactions")
    public ResponseEntity<JsonNode> react(@PathVariable String id, @RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.post("/conversations/" + id + "/meeting/reactions", body));
    }

    @PostMapping("/meetings/invites/resolve")
    public ResponseEntity<JsonNode> resolveInvite(@RequestBody JsonNode body) {
        policy.requireCallActor();
        return relay(khuluma.post("/meetings/invites/resolve", body));
    }

    @PostMapping("/meetings/from-event")
    public ResponseEntity<JsonNode> meetingFromEvent(@RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.post("/meetings/from-event", body));
    }

    @GetMapping("/events/{eventId}/conversation")
    public ResponseEntity<JsonNode> eventConversation(@PathVariable String eventId) {
        return relay(khuluma.get("/events/" + eventId + "/conversation", Map.of()));
    }

    @GetMapping("/calls/incoming")
    public ResponseEntity<JsonNode> incomingCalls() {
        return relay(khuluma.get("/calls/incoming", Map.of()));
    }

    @PostMapping("/calls")
    public ResponseEntity<JsonNode> startCall(@RequestBody JsonNode body) {
        policy.requireCallActor();
        return relay(khuluma.post("/calls", body));
    }

    @PostMapping("/calls/{id}/accept")
    public ResponseEntity<JsonNode> accept(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return relay(khuluma.post("/calls/" + id + "/accept", body != null ? body : objectMapper.createObjectNode()));
    }

    @PostMapping("/calls/{id}/decline")
    public ResponseEntity<JsonNode> decline(@PathVariable String id) {
        return relay(khuluma.post("/calls/" + id + "/decline", objectMapper.createObjectNode()));
    }

    @PostMapping("/calls/{id}/end")
    public ResponseEntity<JsonNode> end(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return relay(khuluma.post("/calls/" + id + "/end", body != null ? body : objectMapper.createObjectNode()));
    }

    private ResponseEntity<JsonNode> relay(KhulumaServiceClient.Result result) {
        return ResponseEntity.status(result.status()).body(result.body());
    }
}
