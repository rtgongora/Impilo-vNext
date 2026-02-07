package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.core.MosipLinkService;

/**
 * MOSIP indirect link management endpoints.
 *
 * <p>Handles encrypted storage and verification of MOSIP opaque reference tokens.
 * The National ID is NEVER accepted, transmitted, or stored by this service.</p>
 */
@RestController
@RequestMapping("/v1/identity/mosip")
public class MosipController {

    private final MosipLinkService mosipService;

    public MosipController(MosipLinkService mosipService) {
        this.mosipService = mosipService;
    }

    /**
     * Store an encrypted MOSIP link reference for a patient.
     *
     * <p>The linkRef (MOSIP opaque token) is encrypted with AES-256-GCM
     * and its SHA-256 hash is stored for lookup. One link per (tenant, healthId).</p>
     */
    @PostMapping("/link")
    public ResponseEntity<ApiResponse<MosipLinkResponse>> storeLink(
            @Valid @RequestBody MosipLinkRequest request) {
        MosipLinkResponse result = mosipService.storeLink(request);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }

    /**
     * Verify a MOSIP link by comparing the hash of the supplied linkRef
     * against the stored hash. If verified, the link status is updated.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<MosipVerifyResponse>> verifyLink(
            @Valid @RequestBody MosipVerifyRequest request) {
        MosipVerifyResponse result = mosipService.verifyLink(request);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }
}
