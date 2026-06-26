package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.core.IdResolutionService;
import zw.gov.mohcc.impilo.tshepo.identity.core.SilentIdentifierResolutionService;

import java.util.UUID;

/**
 * Identity resolution endpoints.
 *
 * <p>Handles the full resolution chain (Impilo ID hash → Health ID → CPID),
 * direct Health ID → CPID lookups, and new mapping creation.</p>
 */
@RestController
@RequestMapping("/v1/identity")
public class ResolutionController {

    private final IdResolutionService resolutionService;
    private final SilentIdentifierResolutionService silentResolutionService;

    public ResolutionController(IdResolutionService resolutionService,
                                SilentIdentifierResolutionService silentResolutionService) {
        this.resolutionService = resolutionService;
        this.silentResolutionService = silentResolutionService;
    }

    /**
     * C3 silent identifier resolution (person-first login hardening).
     *
     * <p>Resolves a login-supplied identifier (email/phone/Health ID/Impilo ID/…)
     * to a person anchor. Anti-enumeration: identical response shape and a
     * constant-time floor on hit and miss alike, so existence cannot be probed.
     * {@code PROVIDER_ID}/{@code COUNCIL_NUMBER} resolve a profile but never
     * authenticate ({@code canAuthenticate=false}).</p>
     *
     * <p>The tenant scope is taken from the trusted {@code X-Tenant-ID} header
     * (Envoy/ext_authz-injected), never solely from the caller-supplied body. A
     * request whose body {@code tenantId} disagrees with the trusted header is
     * rejected, so a caller cannot resolve identifiers in a tenant they are not
     * scoped to.</p>
     */
    @PostMapping("/resolve-identifier")
    public ResponseEntity<ApiResponse<IdentifierResolveResponse>> resolveIdentifier(
            @Valid @RequestBody IdentifierResolveRequest request,
            @RequestHeader("x-tenant-id") UUID trustedTenantId) {
        if (!trustedTenantId.equals(request.tenantId())) {
            throw new IllegalArgumentException("tenantId does not match the request trust context");
        }
        IdentifierResolveResponse result = silentResolutionService.resolve(request);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }

    /**
     * Resolve an Impilo ID hash through the full chain: Impilo ID → Health ID → CPID.
     *
     * <p>The Impilo ID is never transmitted in plaintext — only its verifier/lookup
     * hash is sent. VITO resolves this to a Health ID, which is then mapped to a CPID.</p>
     */
    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<ResolveResponse>> resolve(
            @Valid @RequestBody ResolveRequest request) {
        ResolveResponse result = resolutionService.resolve(request);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }

    /**
     * Get the CPID mapping for a given Health ID within a tenant.
     */
    @GetMapping("/mapping/{healthId}")
    public ResponseEntity<ApiResponse<MappingResponse>> getMappingByHealthId(
            @PathVariable UUID healthId,
            @RequestHeader("x-tenant-id") UUID tenantId) {
        MappingResponse result = resolutionService.getMappingByHealthId(tenantId, healthId);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }

    /**
     * Create a new Health ID → CPID mapping. The CPID is deterministically
     * generated (UUID v5). Idempotent: if the mapping already exists, the
     * existing record is returned.
     */
    @PostMapping("/mapping")
    public ResponseEntity<ApiResponse<MappingResponse>> createMapping(
            @Valid @RequestBody CreateMappingRequest request) {
        MappingResponse result = resolutionService.createMapping(request);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }
}
