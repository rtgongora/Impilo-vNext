package zw.gov.mohcc.impilo.khuluma.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.khuluma.core.DeliveryService;
import zw.gov.mohcc.impilo.khuluma.domain.ChannelAdapterEntity;
import zw.gov.mohcc.impilo.khuluma.domain.DeliveryAttemptEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Khuluma channel-delivery API (Phase 5 / W6, G-KH-03): dispatch across channels, configure external
 * adapters, and read the per-channel delivery audit.
 */
@RestController
@RequestMapping("/internal/v1/khuluma/delivery")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> dispatch(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> channels = body.get("channels") instanceof List ? (List<String>) body.get("channels") : List.of();
        @SuppressWarnings("unchecked")
        Map<String, String> variables = body.get("variables") instanceof Map ? (Map<String, String>) body.get("variables") : null;
        // Content for external delivery delegated to notification-service (template-driven).
        DeliveryService.DispatchContent content = new DeliveryService.DispatchContent(
                str(body, "templateKey"), variables, str(body, "patientRef"), str(body, "messageKind"));
        List<DeliveryAttemptEntity> attempts = service.dispatch(
                tenantId, str(body, "messageRef"), str(body, "recipient"), channels, content);
        return ResponseEntity.ok(Map.of("data", attempts.stream().map(DeliveryController::attemptView).toList()));
    }

    @GetMapping("/adapters")
    public ResponseEntity<Map<String, Object>> adapters(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return ResponseEntity.ok(Map.of("data", service.listAdapters(tenantId).stream().map(DeliveryController::adapterView).toList()));
    }

    @PutMapping("/adapters/{channel}")
    public ResponseEntity<Map<String, Object>> configure(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable String channel,
            @RequestBody Map<String, Object> body) {
        ChannelAdapterEntity a = service.configureAdapter(tenantId, channel, str(body, "status"), str(body, "provider"));
        return ResponseEntity.ok(Map.of("data", adapterView(a)));
    }

    @GetMapping("/attempts")
    public ResponseEntity<Map<String, Object>> attempts(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestParam("message_ref") String messageRef) {
        return ResponseEntity.ok(Map.of("data", service.attemptsFor(tenantId, messageRef).stream()
                .map(DeliveryController::attemptView).toList()));
    }

    private static Map<String, Object> attemptView(DeliveryAttemptEntity a) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("channel", a.getChannel());
        v.put("status", a.getStatus());
        v.put("detail", a.getDetail());
        v.put("attemptedAt", a.getAttemptedAt() != null ? a.getAttemptedAt().toString() : null);
        return v;
    }

    private static Map<String, Object> adapterView(ChannelAdapterEntity a) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("channel", a.getChannel());
        v.put("status", a.getStatus());
        v.put("provider", a.getProvider());
        return v;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
