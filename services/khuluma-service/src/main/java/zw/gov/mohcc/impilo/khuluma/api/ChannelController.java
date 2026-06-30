package zw.gov.mohcc.impilo.khuluma.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.khuluma.core.ChannelService;
import zw.gov.mohcc.impilo.khuluma.core.ChannelService.NewChannel;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Khuluma channels, communities & broadcast API (Phase 5 / W5, G-KH-02).
 */
@RestController
@RequestMapping("/internal/v1/khuluma/channels")
public class ChannelController {

    private final ChannelService service;

    public ChannelController(ChannelService service) {
        this.service = service;
    }

    @PostMapping("")
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestBody Map<String, Object> body) {
        NewChannel cmd = new NewChannel(str(body, "conversationType"), str(body, "scopeType"),
                uuid(body, "scopeRef"), str(body, "title"), str(body, "topic"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("data", view(service.create(tenantId, actorId, actorType, cmd))));
    }

    @GetMapping("/discover")
    public ResponseEntity<Map<String, Object>> discover(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam("scope_type") String scopeType,
            @RequestParam("scope_ref") UUID scopeRef) {
        List<ConversationEntity> rows = service.discover(tenantId, scopeType, scopeRef);
        return ResponseEntity.ok(Map.of("data", rows.stream().map(this::view).toList()));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Map<String, Object>> join(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        service.join(tenantId, id, actorId, actorType, str(body, "displayName"));
        return ResponseEntity.ok(Map.of("data", Map.of("conversationId", id.toString(), "joined", true)));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Map<String, Object>> leave(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        service.leave(tenantId, id, actorId);
        return ResponseEntity.ok(Map.of("data", Map.of("conversationId", id.toString(), "left", true)));
    }

    @PostMapping("/{id}/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var msg = service.broadcast(tenantId, id, actorId, actorType, str(body, "body"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("data", Map.of("messageId", msg.getMessageId().toString(), "conversationId", id.toString())));
    }

    private Map<String, Object> view(ConversationEntity c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("conversationId", c.getConversationId().toString());
        v.put("conversationType", c.getConversationType());
        v.put("scopeType", c.getScopeType());
        v.put("scopeRef", c.getScopeRef() != null ? c.getScopeRef().toString() : null);
        v.put("title", c.getTitle());
        v.put("topic", c.getTopic());
        v.put("status", c.getStatus());
        return v;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
    private static UUID uuid(Map<String, Object> body, String key) {
        String s = str(body, key);
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException ex) { return null; }
    }
}
