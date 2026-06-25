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
 * Experience-BFF surface for the Khuluma Comms Hub ({@code /internal/v1/khuluma/**}). Composes the
 * khuluma-service Comms SoR with the notification-service inbox (Khuluma never re-implements
 * notifications — it delegates). Sensitive actions pass the {@link KhulumaAccessPolicyService}
 * pre-check; the client relays khuluma-service's status semantics unchanged.
 */
@RestController
@RequestMapping("/internal/v1/khuluma")
public class KhulumaBffController {

    private final KhulumaServiceClient khuluma;
    private final NotificationServiceClient notifications;
    private final KhulumaAccessPolicyService policy;
    private final ObjectMapper objectMapper;

    public KhulumaBffController(KhulumaServiceClient khuluma,
                               NotificationServiceClient notifications,
                               KhulumaAccessPolicyService policy,
                               ObjectMapper objectMapper) {
        this.khuluma = khuluma;
        this.notifications = notifications;
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    // ── summary / counts ────────────────────────────────────────────────────

    @GetMapping("/summary")
    public ResponseEntity<JsonNode> summary(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        KhulumaServiceClient.Result unread = khuluma.get("/unread-count", Map.of());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("messages", unread.body());
        body.set("notifications", safeNotificationUnread(actorId));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<JsonNode> unreadCount(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        KhulumaServiceClient.Result unread = khuluma.get("/unread-count", Map.of());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("messages", unread.body());
        body.set("notifications", safeNotificationUnread(actorId));
        return ResponseEntity.status(unread.status()).body(body);
    }

    // ── conversations ─────────────────────────────────────────────────────────

    @GetMapping({"/inbox", "/conversations"})
    public ResponseEntity<JsonNode> inbox() {
        return relay(khuluma.get("/conversations", Map.of()));
    }

    @PostMapping("/conversations")
    public ResponseEntity<JsonNode> createConversation(@RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.post("/conversations", body));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<JsonNode> getConversation(@PathVariable String id) {
        return relay(khuluma.get("/conversations/" + id, Map.of()));
    }

    @PostMapping("/conversations/{id}/participants")
    public ResponseEntity<JsonNode> addParticipant(@PathVariable String id, @RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.post("/conversations/" + id + "/participants", body));
    }

    @PostMapping("/conversations/{id}/links")
    public ResponseEntity<JsonNode> linkPatient(@PathVariable String id, @RequestBody JsonNode body) {
        policy.requireClinicalActorForPatientLink();
        return relay(khuluma.post("/conversations/" + id + "/links", body));
    }

    // ── messages ──────────────────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<JsonNode> messages(@PathVariable String id) {
        return relay(khuluma.get("/conversations/" + id + "/messages", Map.of()));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<JsonNode> sendMessage(@PathVariable String id, @RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.post("/conversations/" + id + "/messages", body));
    }

    @PostMapping("/conversations/{id}/messages/read")
    public ResponseEntity<JsonNode> markRead(@PathVariable String id, @RequestBody JsonNode body) {
        return relay(khuluma.post("/conversations/" + id + "/messages/read", body));
    }

    // ── meetings / live events ────────────────────────────────────────────────

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

    /** Compose with Impilo Live: attach a Khuluma conversation to an existing live event. */
    @PostMapping("/meetings/from-event")
    public ResponseEntity<JsonNode> meetingFromEvent(@RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.post("/meetings/from-event", body));
    }

    @GetMapping("/events/{eventId}/conversation")
    public ResponseEntity<JsonNode> eventConversation(@PathVariable String eventId) {
        return relay(khuluma.get("/events/" + eventId + "/conversation", Map.of()));
    }

    @PostMapping("/conversations/{id}/meeting/end")
    public ResponseEntity<JsonNode> endMeeting(@PathVariable String id) {
        return relay(khuluma.post("/conversations/" + id + "/meeting/end", objectMapper.createObjectNode()));
    }

    // ── notifications (delegate to notification-service) ───────────────────────

    @GetMapping("/notifications")
    public ResponseEntity<JsonNode> notifications(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        try {
            return ResponseEntity.ok(notifications.listNotifications(actorId));
        } catch (Exception ex) {
            return ResponseEntity.status(502).body(error("NOTIFICATIONS_UNAVAILABLE", ex.getMessage()));
        }
    }

    // ── presence ──────────────────────────────────────────────────────────────

    @PutMapping("/presence")
    public ResponseEntity<JsonNode> updatePresence(@RequestBody JsonNode body) {
        policy.requireCommsActor();
        return relay(khuluma.put("/presence", body));
    }

    @GetMapping("/presence/{actorId}")
    public ResponseEntity<JsonNode> getPresence(@PathVariable String actorId) {
        return relay(khuluma.get("/presence/" + actorId, Map.of()));
    }

    // ── calls ─────────────────────────────────────────────────────────────────

    @PostMapping("/calls")
    public ResponseEntity<JsonNode> startCall(@RequestBody JsonNode body) {
        policy.requireCallActor();
        return relay(khuluma.post("/calls", body));
    }

    @GetMapping("/calls/incoming")
    public ResponseEntity<JsonNode> incomingCalls() {
        return relay(khuluma.get("/calls/incoming", Map.of()));
    }

    @GetMapping("/calls/{id}")
    public ResponseEntity<JsonNode> getCall(@PathVariable String id) {
        return relay(khuluma.get("/calls/" + id, Map.of()));
    }

    @PostMapping("/calls/{id}/accept")
    public ResponseEntity<JsonNode> acceptCall(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return relay(khuluma.post("/calls/" + id + "/accept", body != null ? body : objectMapper.createObjectNode()));
    }

    @PostMapping("/calls/{id}/decline")
    public ResponseEntity<JsonNode> declineCall(@PathVariable String id) {
        return relay(khuluma.post("/calls/" + id + "/decline", objectMapper.createObjectNode()));
    }

    @PostMapping("/calls/{id}/end")
    public ResponseEntity<JsonNode> endCall(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return relay(khuluma.post("/calls/" + id + "/end", body != null ? body : objectMapper.createObjectNode()));
    }

    @PostMapping("/calls/{id}/signals")
    public ResponseEntity<JsonNode> signal(@PathVariable String id, @RequestBody JsonNode body) {
        return relay(khuluma.post("/calls/" + id + "/signals", body));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<JsonNode> relay(KhulumaServiceClient.Result result) {
        return ResponseEntity.status(result.status()).body(result.body());
    }

    private JsonNode safeNotificationUnread(String actorId) {
        try {
            JsonNode count = notifications.unreadCount(actorId);
            return count != null ? count : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return error("NOTIFICATIONS_UNAVAILABLE", ex.getMessage());
        }
    }

    private JsonNode error(String code, String message) {
        ObjectNode node = objectMapper.createObjectNode();
        ObjectNode err = node.putObject("error");
        err.put("code", code);
        err.put("message", message != null ? message : code);
        return node;
    }
}
