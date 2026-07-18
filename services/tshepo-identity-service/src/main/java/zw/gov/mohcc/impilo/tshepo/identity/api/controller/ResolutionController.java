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
            @RequestHeader("x-tenant-id") UUID trustedTenantId,
            @RequestHeader(value = "x-access-mode", required = false) String accessMode) {
        // INTERNAL-only (Identity Journey Doctrine §2): a successful private match
        // returns the person's Health ID, so this must never be reachable by a
        // client — only the trust core (BFF) may resolve, and only to immediately
        // issue a second-factor challenge without surfacing the result.
        if (!"INTERNAL".equalsIgnoreCase(accessMode)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "resolve-identifier is INTERNAL-only");
        }
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
     * Reverse resolution: CPID → mapping (Identity Contract §7.4).
     *
     * <p>The only sanctioned path from a clinical subject key back to the identity
     * plane, for authorised composition flows (e.g. deep-link entry with only a
     * CPID). A purpose of use is mandatory and every call is audited via the
     * outbox ({@code REVERSE_RESOLVED}) — there is no unaudited reverse path.</p>
     */
    @GetMapping("/mappings/by-cpid/{cpid}")
    public ResponseEntity<ApiResponse<MappingResponse>> getMappingByCpid(
            @PathVariable UUID cpid,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-purpose-of-use") String purposeOfUse,
            @RequestHeader(value = "x-actor-id", required = false) String actorId) {
        MappingResponse result = resolutionService.reverseResolveAudited(
                tenantId, cpid, purposeOfUse, actorId);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }

    /**
     * Create a new Health ID → CPID mapping with an independent random CPID
     * (Identity Contract §7). Idempotent: if the mapping already exists, the
     * existing record is returned.
     */
    @PostMapping("/mapping")
    public ResponseEntity<ApiResponse<MappingResponse>> createMapping(
            @Valid @RequestBody CreateMappingRequest request) {
        MappingResponse result = resolutionService.createMapping(request);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }
}
