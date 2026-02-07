package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.RevocationReason;
import zw.gov.mohcc.impilo.vito.core.recovery.HealthSummaryService;
import zw.gov.mohcc.impilo.vito.core.recovery.RecoveryService;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Recovery & Secure Health Summary API — INTERNAL only.
 */
@RestController
@RequestMapping("/v1/recovery")
public class RecoveryController {

    private final RecoveryService recoveryService;
    private final HealthSummaryService healthSummaryService;

    public RecoveryController(RecoveryService recoveryService,
                               HealthSummaryService healthSummaryService) {
        this.recoveryService = recoveryService;
        this.healthSummaryService = healthSummaryService;
    }

    /**
     * Execute Secure Handover — revoke old card, issue new, transfer wallet.
     */
    @PostMapping("/handover")
    public ResponseEntity<ApiResponse<SmartCardEntity>> secureHandover(
            @RequestBody HandoverRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        SmartCardEntity newCard = recoveryService.secureHandover(
                ctx.tenantId(), request.healthId(), request.oldCardId(),
                request.reason(), request.newPublicKey(), ctx.actorId());

        return ResponseEntity.ok(ApiResponse.ok(newCard, ctx.correlationId().toString()));
    }

    /**
     * Create a Secure Health Summary (SHS) credential.
     */
    @PostMapping("/shs/create")
    public ResponseEntity<ApiResponse<Map<String, String>>> createSHS(
            @RequestBody SHSRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        String jws = healthSummaryService.compressAndSign(
                request.fhirBundleJson(), request.healthId());

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("jwsCompact", jws, "healthId", request.healthId()),
                ctx.correlationId().toString()));
    }

    /**
     * Verify a Secure Health Summary credential.
     */
    @PostMapping("/shs/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifySHS(
            @RequestBody SHSVerifyRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        String payload = healthSummaryService.verifyAndExtract(request.jwsCompact());
        boolean valid = payload != null;

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("valid", valid, "payload", payload != null ? payload : ""),
                ctx.correlationId().toString()));
    }

    private void requireInternal(TrustContext ctx) {
        if (ctx.mode() == AccessMode.EXTERNAL) {
            throw new SecurityException("Recovery operations are internal only");
        }
    }

    record HandoverRequest(UUID healthId, Long oldCardId, RevocationReason reason, String newPublicKey) {}
    record SHSRequest(String healthId, String fhirBundleJson) {}
    record SHSVerifyRequest(String jwsCompact) {}
}
