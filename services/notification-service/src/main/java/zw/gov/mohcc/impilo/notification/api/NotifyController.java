package zw.gov.mohcc.impilo.notification.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.notification.api.dto.NotificationResponse;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyResponse;
import zw.gov.mohcc.impilo.notification.service.NotifyService;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @PostMapping("/notify")
    public ResponseEntity<NotifyResponse> enqueueNotification(@Valid @RequestBody NotifyRequest request) {
        RequestContext ctx = RequestContextHolder.require();

        NotifyResponse response = notifyService.enqueue(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/notifications/send")
    public ResponseEntity<NotifyResponse> enqueueNotificationAlias(@Valid @RequestBody NotifyRequest request) {
        return enqueueNotification(request);
    }

    @GetMapping("/notifications")
    public ResponseEntity<Page<NotificationResponse>> listNotifications(Pageable pageable) {
        RequestContext ctx = RequestContextHolder.require();

        Page<NotificationResponse> page = notifyService.listNotifications(ctx.tenantId(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/notifications/inbox")
    public ResponseEntity<Page<NotificationResponse>> listInbox(Pageable pageable) {
        return listNotifications(pageable);
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<NotificationResponse> markNotificationRead(@PathVariable("id") String id) {
        RequestContext ctx = RequestContextHolder.require();
        return notifyService.markAsRead(ctx.tenantId(), ctx.podId(), ctx.correlationId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/notifications/inbox/{id}/read")
    public ResponseEntity<NotificationResponse> markInboxNotificationRead(@PathVariable("id") String id) {
        return markNotificationRead(id);
    }

    @PostMapping("/notifications/inbox/read-all")
    public ResponseEntity<Map<String, Object>> markInboxReadAll() {
        RequestContext ctx = RequestContextHolder.require();
        int updated = notifyService.markAllAsRead(ctx.tenantId());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Object>> markReadAll() {
        return markInboxReadAll();
    }

    @GetMapping("/notifications/inbox/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("count", notifyService.unreadCount(ctx.tenantId())));
    }

    @GetMapping("/notifications/preferences")
    public ResponseEntity<Map<String, Object>> getPreferences(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "patientRef", required = false) String patientRef) {
        RequestContext ctx = RequestContextHolder.require();
        String resolvedPatient = patientId != null && !patientId.isBlank() ? patientId : patientRef;
        if (resolvedPatient == null || resolvedPatient.isBlank()) {
            return ResponseEntity.ok(Map.of("preferences", java.util.List.of()));
        }
        return notifyService.getPreferences(ctx.tenantId(), resolvedPatient)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Map.of("preferences", java.util.List.of())));
    }

    @PutMapping("/notifications/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "patientRef", required = false) String patientRef,
            @RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        String resolvedPatient = patientId != null && !patientId.isBlank() ? patientId : patientRef;
        if (resolvedPatient == null || resolvedPatient.isBlank()) {
            return ResponseEntity.ok(Map.of("updated", false, "reason", "patientId or patientRef was not provided"));
        }
        return notifyService.updatePreferences(ctx.tenantId(), resolvedPatient, body)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Map.of("updated", false, "reason", "upstream unavailable")));
    }
}
