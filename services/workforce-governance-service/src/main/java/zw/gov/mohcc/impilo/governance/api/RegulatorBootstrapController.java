package zw.gov.mohcc.impilo.governance.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.governance.core.RegulatorBootstrapService;
import zw.gov.mohcc.impilo.governance.persistence.RegulatorBootstrapRequestEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;
import java.util.UUID;

/**
 * The regulator bootstrap request API (RB-3): submit a request to found a regulator, approve it
 * under four-eyes, and activate it.
 *
 * <p>The claimant is always the authenticated actor ({@code X-Actor-ID}), never a body field — a
 * body-supplied claimant would let one person raise a founding request in another's name. The
 * person-assurance gate is applied upstream at the BFF, where the identity-assurance service is
 * reachable.</p>
 */
@RestController
@RequestMapping("/internal/v1/workforce-governance/regulator-bootstrap")
public class RegulatorBootstrapController {

    private final RegulatorBootstrapService bootstrapService;

    public RegulatorBootstrapController(RegulatorBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@RequestBody Map<String, Object> body) {
        UUID organizationId = uuid(body.get("organizationId"), "organizationId");
        String roleCode = str(body.get("roleCode"));
        String evidenceRef = str(body.get("evidenceRef"));
        @SuppressWarnings("unchecked")
        Map<String, Object> check = body.get("authoritativeCheckResult") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;

        RegulatorBootstrapRequestEntity saved = bootstrapService.submit(
                tenant(), organizationId, actor(), roleCode, evidenceRef, check, actor());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(bootstrapService.view(saved), corr()));
    }

    @PostMapping("/actions/{accessRequestId}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable UUID accessRequestId, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = bootstrapService.approve(accessRequestId, actor(),
                str(body.get("approverRole")), str(body.get("decision")), str(body.get("note")));
        return ResponseEntity.ok(ApiResponse.ok(result, corr()));
    }

    @PostMapping("/actions/{accessRequestId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activate(@PathVariable UUID accessRequestId) {
        RegulatorBootstrapRequestEntity activated = bootstrapService.activate(accessRequestId, actor());
        return ResponseEntity.ok(ApiResponse.ok(bootstrapService.view(activated), corr()));
    }

    @PostMapping("/requests/{publicId}/withdraw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdraw(
            @PathVariable String publicId, @RequestBody(required = false) Map<String, Object> body) {
        RegulatorBootstrapRequestEntity row = bootstrapService.withdraw(
                tenant(), publicId, body == null ? null : str(body.get("reason")));
        return ResponseEntity.ok(ApiResponse.ok(bootstrapService.view(row), corr()));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<java.util.List<Map<String, Object>>>> list(
            @RequestParam(required = false) String status) {
        java.util.List<Map<String, Object>> rows = bootstrapService.listByStatus(tenant(), status)
                .stream().map(bootstrapService::view).toList();
        return ResponseEntity.ok(ApiResponse.ok(rows, corr()));
    }

    @GetMapping("/requests/{publicId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(
                bootstrapService.view(bootstrapService.require(tenant(), publicId)), corr()));
    }

    // ── failure mapping ─────────────────────────────────────────────────────

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> onInvalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                ApiResponse.error("BOOTSTRAP_INVALID", ex.getMessage(), 400, corr()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> onConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiResponse.error("BOOTSTRAP_CONFLICT", ex.getMessage(), 409, corr()));
    }

    private UUID tenant() {
        UUID t = TrustContextHolder.require().tenantId();
        if (t == null) throw new IllegalArgumentException("Missing X-Tenant-ID trust header");
        return t;
    }

    private String actor() {
        TrustContext ctx = TrustContextHolder.get();
        String a = ctx != null ? ctx.actorId() : null;
        if (a == null || a.isBlank()) {
            throw new IllegalArgumentException("Missing X-Actor-ID — a bootstrap request needs a claimant");
        }
        return a;
    }

    private String corr() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null ? ctx.correlationId().toString() : "";
    }

    private static UUID uuid(Object value, String field) {
        String s = str(value);
        if (s == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return UUID.fromString(s);
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
