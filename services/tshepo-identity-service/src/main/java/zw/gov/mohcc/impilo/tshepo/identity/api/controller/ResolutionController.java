package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.core.IdResolutionService;

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

    public ResolutionController(IdResolutionService resolutionService) {
        this.resolutionService = resolutionService;
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
