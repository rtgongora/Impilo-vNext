package zw.gov.mohcc.impilo.costa.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.ClaimPackEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.ClaimPackRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

/**
 * Claims list and detail endpoints — tenant-scoped view of insurance
 * claim packs created from finalized bills.
 */
@RestController
@RequestMapping("/costa/v1/claims")
public class ClaimController {

    private final ClaimPackRepository claimPackRepository;

    public ClaimController(ClaimPackRepository claimPackRepository) {
        this.claimPackRepository = claimPackRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ClaimPackEntity>>> listClaims(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var ctx = TrustContextHolder.require();
        Page<ClaimPackEntity> claims = claimPackRepository.findByTenantId(
                ctx.tenantId(), PageRequest.of(page, size));
        PagedResponse<ClaimPackEntity> paged = PagedResponse.of(
                claims.getContent(), page, size, claims.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClaimPackEntity>> getClaim(@PathVariable Long id) {
        var ctx = TrustContextHolder.require();
        ClaimPackEntity claim = claimPackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
        return ResponseEntity.ok(ApiResponse.ok(claim, ctx.correlationId().toString()));
    }
}
