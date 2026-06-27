package zw.gov.mohcc.impilo.dags.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.dags.core.AuditService;
import zw.gov.mohcc.impilo.dags.core.EnforcementService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Enforcement point for DAGS permit tokens — the consumer that actually verifies the
 * permit signature (closing G056: previously the signed token was never checked).
 *
 * <p>A service about to act on protected data presents the permit token plus the access
 * context it intends to exercise. The permit is verified (signature, expiry, every bound
 * claim) and its nonce consumed (single use); the outcome is audited with the precise
 * reason. The tenant is taken from the server-side {@link TrustContext}, never the body,
 * so a permit cannot be enforced across tenants.</p>
 */
@RestController
@RequestMapping("/internal/v1/permits")
public class PermitEnforcementController {

    private final EnforcementService enforcementService;
    private final AuditService auditService;

    public PermitEnforcementController(EnforcementService enforcementService, AuditService auditService) {
        this.enforcementService = enforcementService;
        this.auditService = auditService;
    }

    @PostMapping("/enforce")
    public ResponseEntity<ApiResponse<PermitEnforceResponse>> enforce(@RequestBody PermitEnforceRequest req) {
        TrustContext ctx = TrustContextHolder.require();

        EnforcementService.PermitContext context = new EnforcementService.PermitContext(
                ctx.tenantId(), req.requesterId(), req.resourceType(), req.resourceId(), req.purpose());

        EnforcementService.PermitVerification result =
                enforcementService.verifyAndConsume(req.token(), context);

        auditService.log(
                ctx.tenantId(),
                result.valid() ? "PERMIT_ENFORCED" : "PERMIT_DENIED",
                ctx.actorId(),
                req.resourceType(),
                req.resourceId(),
                "{\"reason\":\"" + result.reason().name() + "\",\"requester\":\"" + jsonSafe(req.requesterId()) + "\"}",
                ctx.correlationId());

        return ResponseEntity.ok(ApiResponse.ok(
                new PermitEnforceResponse(result.valid(), result.reason().name()),
                ctx.correlationId().toString()));
    }

    private static String jsonSafe(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record PermitEnforceRequest(
            String token,
            String requesterId,
            String resourceType,
            String resourceId,
            String purpose
    ) {}

    public record PermitEnforceResponse(boolean valid, String reason) {}
}
