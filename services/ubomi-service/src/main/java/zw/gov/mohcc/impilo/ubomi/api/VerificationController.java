package zw.gov.mohcc.impilo.ubomi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.ubomi.core.VerificationService;
import zw.gov.mohcc.impilo.ubomi.core.VerificationService.VerificationResult;

/**
 * Vital Event Verification API.
 *
 * Allows both INTERNAL and EXTERNAL consumers to verify
 * birth/death registration status against the civil registry.
 * This is the primary interoperability endpoint for CRVS systems.
 */
@RestController
@RequestMapping("/v1/verifications")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Verify a vital event (birth or death) registration status.
     * Available to both modes — core interoperability function.
     */
    @GetMapping("/{registrationNumber}")
    public ResponseEntity<ApiResponse<VerificationResult>> verifyRegistration(
            @PathVariable String registrationNumber) {

        TrustContext ctx = TrustContextHolder.require();

        VerificationResult result = verificationService.verify(
                ctx.tenantId(), registrationNumber, ctx.actorId(), ctx.mode().name());

        return ResponseEntity.ok(
            ApiResponse.ok(result, ctx.correlationId().toString())
        );
    }
}
