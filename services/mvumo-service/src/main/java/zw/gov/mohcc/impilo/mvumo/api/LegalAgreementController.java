package zw.gov.mohcc.impilo.mvumo.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mvumo.service.LegalAgreementService;
import zw.gov.mohcc.impilo.mvumo.service.TenantIds;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legal/platform agreement acceptance (Privacy Policy, Terms of Use). Auto-proxied to web/mobile
 * by experience-bff's {@code MvumoServiceProxyController} (no new BFF plumbing). Identity (subject)
 * is taken from the request body / trust headers by the caller; this endpoint records the act.
 */
@RestController
@RequestMapping("/internal/v1/mvumo/legal-agreements")
public class LegalAgreementController {

    private final LegalAgreementService service;

    public LegalAgreementController(LegalAgreementService service) {
        this.service = service;
    }

    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> accept(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestBody Map<String, Object> body) {
        return ok(service.accept(parseTenant(tenant), body));
    }

    @GetMapping("/actors/{actorId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listForSubject(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable String actorId) {
        return ok(service.listForSubject(parseTenant(tenant), actorId));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdraw(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        return ok(service.withdraw(parseTenant(tenant), id, actorId));
    }

    /** Session-admission check: body = {subjectActorId, requiredVersions: {PRIVACY_POLICY: "v", TERMS_OF_USE: "v"}}. */
    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> evaluate(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestBody Map<String, Object> body) {
        String subjectActorId = String.valueOf(body.get("subjectActorId"));
        Map<String, String> required = new HashMap<>();
        Object rv = body.get("requiredVersions");
        if (rv instanceof Map<?, ?> m) {
            m.forEach((k, v) -> required.put(String.valueOf(k), String.valueOf(v)));
        }
        return ok(service.evaluate(parseTenant(tenant), subjectActorId, required));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data, UUID.randomUUID().toString()));
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
