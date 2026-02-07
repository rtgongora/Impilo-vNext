package zw.gov.mohcc.impilo.tshepo.audit.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.ChainVerificationRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.ChainVerificationResponse;
import zw.gov.mohcc.impilo.tshepo.audit.core.AuditChainService;

import java.util.UUID;

/**
 * Endpoint for verifying the SHA-256 hash chain integrity for a tenant.
 * Walks the entire chain, recomputing each hash and comparing to stored values.
 */
@RestController
@RequestMapping("/v1/audit")
public class ChainIntegrityController {

    private final AuditChainService chainService;

    public ChainIntegrityController(AuditChainService chainService) {
        this.chainService = chainService;
    }

    /**
     * POST /v1/audit/verify-chain — verify hash chain integrity for a tenant.
     *
     * This is an internal operations endpoint. It walks the full chain from
     * sequence 1 to head, recomputing each hash to detect any tampering.
     */
    @PostMapping("/verify-chain")
    public ResponseEntity<ApiResponse<ChainVerificationResponse>> verifyChain(
            @Valid @RequestBody ChainVerificationRequest request,
            @RequestHeader(value = "x-correlation-id", required = false) UUID correlationId) {

        String corrId = correlationId != null ? correlationId.toString() : UUID.randomUUID().toString();

        ChainVerificationResponse result = chainService.verifyChain(request.tenantId());

        HttpStatus status = result.intact() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok(result, corrId));
    }
}
