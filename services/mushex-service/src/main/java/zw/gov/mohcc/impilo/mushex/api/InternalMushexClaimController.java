package zw.gov.mohcc.impilo.mushex.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEntity;
import zw.gov.mohcc.impilo.mushex.service.ClaimService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

/**
 * Mesh-internal claim transitions driven by coverage-plane (appeal outcomes).
 */
@RestController
@RequestMapping("/internal/v1/claims")
public class InternalMushexClaimController {

    private final ClaimService claimService;

    public InternalMushexClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping("/{id}/appeal-resubmit")
    @Transactional
    public ResponseEntity<ApiResponse<ClaimEntity>> appealResubmit(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        ClaimEntity claim = claimService.getClaim(id);
        UUID headerTenant = ctx.tenantId();
        if (headerTenant == null || !headerTenant.equals(claim.getTenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ClaimEntity updated = claimService.markResubmitPendingAfterAppeal(id);
        return ResponseEntity.ok(ApiResponse.ok(updated, ctx.correlationId().toString()));
    }
}
