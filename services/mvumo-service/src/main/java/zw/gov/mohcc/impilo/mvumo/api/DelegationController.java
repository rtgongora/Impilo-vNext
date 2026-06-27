package zw.gov.mohcc.impilo.mvumo.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mvumo.service.DelegationService;
import zw.gov.mohcc.impilo.mvumo.service.TenantIds;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L5 delegated-access relationships (G-CZO-03). Auto-proxied to web/mobile by experience-bff's
 * {@code MvumoServiceProxyController}. {@code /resolve} is the trust-plane (PolicyEngine Step 4.5)
 * entry point; it returns relationship facts for the PDP to evaluate, never an access decision.
 */
@RestController
@RequestMapping("/internal/v1/mvumo/delegations")
public class DelegationController {

    private final DelegationService service;

    public DelegationController(DelegationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestBody Map<String, Object> body) {
        return created(service.create(parseTenant(tenant), actorId, actorType, body));
    }

    @GetMapping("/delegate/{actorId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listForDelegate(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable String actorId) {
        return ok(service.listForDelegate(parseTenant(tenant), actorId));
    }

    @GetMapping("/subject/{subjectRef}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listForSubject(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable String subjectRef) {
        return ok(service.listForSubject(parseTenant(tenant), subjectRef));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revoke(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (body.get("reason") == null ? null : body.get("reason").toString());
        return ok(service.revoke(parseTenant(tenant), id, reason));
    }

    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolve(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestParam("delegate") String delegateActorId,
            @RequestParam("subject") String subjectRef) {
        return ok(service.resolve(parseTenant(tenant), delegateActorId, subjectRef));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data, UUID.randomUUID().toString()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(201).body(ApiResponse.ok(data, UUID.randomUUID().toString()));
    }

    private static UUID parseTenant(String header) {
        if (header == null || header.isBlank()) {
            return TenantIds.PLATFORM;
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException ex) {
            return TenantIds.PLATFORM;
        }
    }
}
