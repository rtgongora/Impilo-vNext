package zw.gov.mohcc.impilo.guidance.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.guidance.core.AdvisoryAdminService;
import zw.gov.mohcc.impilo.guidance.core.AdvisoryAdminService.AdvisoryValidationException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service Advisory authoring + lifecycle API (internal, staff/admin lane). The experience-bff
 * resolves the operator's trust/role/purpose before proxying here — governance (an approver is
 * required before EMERGENCY/IMPORTANT publish) is enforced in {@link AdvisoryAdminService}.
 *
 * <ul>
 *   <li>GET    /internal/v1/guidance/advisory/admin            — list (optional ?status=)</li>
 *   <li>GET    /internal/v1/guidance/advisory/admin/{id}       — one advisory</li>
 *   <li>POST   /internal/v1/guidance/advisory/admin            — create (DRAFT)</li>
 *   <li>PUT    /internal/v1/guidance/advisory/admin/{id}       — update editable fields</li>
 *   <li>POST   /internal/v1/guidance/advisory/admin/{id}/approve  — set approver</li>
 *   <li>POST   /internal/v1/guidance/advisory/admin/{id}/schedule — DRAFT → SCHEDULED</li>
 *   <li>POST   /internal/v1/guidance/advisory/admin/{id}/publish  — DRAFT|SCHEDULED → PUBLISHED (approval-gated)</li>
 *   <li>POST   /internal/v1/guidance/advisory/admin/{id}/withdraw — PUBLISHED → WITHDRAWN</li>
 *   <li>POST   /internal/v1/guidance/advisory/admin/{id}/expire   — PUBLISHED|SCHEDULED → EXPIRED</li>
 *   <li>DELETE /internal/v1/guidance/advisory/admin/{id}       — delete a draft</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/guidance/advisory/admin")
public class AdvisoryAdminController {

    private final AdvisoryAdminService service;

    public AdvisoryAdminController(AdvisoryAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @RequestParam(required = false) String status) {
        List<Map<String, Object>> data = service.list(tenant(tenantId), status);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @PathVariable String id) {
        return handle(() -> service.get(tenant(tenantId), UUID.fromString(id)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @RequestHeader(value = "x-actor-id", required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        return handle(() -> service.create(tenant(tenantId), actorId, body), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return handle(() -> service.update(tenant(tenantId), UUID.fromString(id), body));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @RequestHeader(value = "x-actor-id", required = false) String actorId,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        String approver = body != null && body.get("approver") != null ? body.get("approver").toString() : actorId;
        return handle(() -> service.approve(tenant(tenantId), UUID.fromString(id), approver));
    }

    @PostMapping("/{id}/schedule")
    public ResponseEntity<Map<String, Object>> schedule(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @PathVariable String id) {
        return handle(() -> service.schedule(tenant(tenantId), UUID.fromString(id)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Map<String, Object>> publish(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @PathVariable String id) {
        return handle(() -> service.publish(tenant(tenantId), UUID.fromString(id)));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @PathVariable String id) {
        return handle(() -> service.withdraw(tenant(tenantId), UUID.fromString(id)));
    }

    @PostMapping("/{id}/expire")
    public ResponseEntity<Map<String, Object>> expire(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @PathVariable String id) {
        return handle(() -> service.expire(tenant(tenantId), UUID.fromString(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @PathVariable String id) {
        return handle(() -> {
            service.delete(tenant(tenantId), UUID.fromString(id));
            return Map.of("deleted", true, "id", id);
        });
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────

    private interface AdvisoryAction {
        Map<String, Object> run();
    }

    private ResponseEntity<Map<String, Object>> handle(AdvisoryAction action) {
        return handle(action, HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> handle(AdvisoryAction action, HttpStatus okStatus) {
        try {
            return ResponseEntity.status(okStatus).body(Map.of("data", action.run()));
        } catch (AdvisoryValidationException e) {
            HttpStatus status = "ADVISORY_NOT_FOUND".equals(e.getCode())
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("error",
                    Map.of("code", e.getCode(), "message", e.getMessage())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    Map.of("code", "BAD_REQUEST", "message", "Invalid advisory id.")));
        }
    }

    private static String tenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "national-spine" : tenantId;
    }
}
