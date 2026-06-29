package zw.gov.mohcc.impilo.khuluma.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.khuluma.core.EscalationService;
import zw.gov.mohcc.impilo.khuluma.core.EscalationService.NewEscalation;
import zw.gov.mohcc.impilo.khuluma.domain.EscalationEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Khuluma escalation & SLA API (Phase 5 / W4, G-KH-01). Internal surface for the ops-desk console
 * and other services that escalate a conversation/feedback item.
 */
@RestController
@RequestMapping("/internal/v1/khuluma/escalations")
public class EscalationController {

    private final EscalationService service;

    public EscalationController(EscalationService service) {
        this.service = service;
    }

    @PostMapping("")
    public ResponseEntity<Map<String, Object>> open(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestBody Map<String, Object> body) {
        NewEscalation cmd = new NewEscalation(uuid(body, "conversationId"), str(body, "reason"), str(body, "priority"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", view(service.open(tenantId, cmd))));
    }

    @GetMapping("")
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(value = "queue", defaultValue = "false") boolean queueOnly) {
        List<EscalationEntity> rows = queueOnly ? service.queue(tenantId) : service.list(tenantId);
        return ResponseEntity.ok(Map.of("data", rows.stream().map(this::view).toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("data", view(service.get(tenantId, id))));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assign(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", view(service.assign(tenantId, id, str(body, "assignee")))));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("data", view(service.accept(tenantId, id, actorId))));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolve(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", view(service.resolve(tenantId, id, str(body, "note")))));
    }

    @PostMapping("/{id}/escalate")
    public ResponseEntity<Map<String, Object>> escalate(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", view(service.escalate(tenantId, id, str(body, "reason")))));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", view(service.cancel(tenantId, id, str(body, "note")))));
    }

    private Map<String, Object> view(EscalationEntity e) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", e.getId().toString());
        v.put("conversationId", e.getConversationId() != null ? e.getConversationId().toString() : null);
        v.put("reason", e.getReason());
        v.put("priority", e.getPriority());
        v.put("tier", e.getTier());
        v.put("status", e.getStatus());
        v.put("assignedTo", e.getAssignedTo());
        v.put("firstResponseDueAt", str(e.getFirstResponseDueAt()));
        v.put("resolutionDueAt", str(e.getResolutionDueAt()));
        v.put("firstResponseBreached", e.isFirstResponseBreached());
        v.put("resolutionBreached", e.isResolutionBreached());
        v.put("createdAt", str(e.getCreatedAt()));
        return v;
    }

    private static String str(java.time.OffsetDateTime t) { return t == null ? null : t.toString(); }
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
