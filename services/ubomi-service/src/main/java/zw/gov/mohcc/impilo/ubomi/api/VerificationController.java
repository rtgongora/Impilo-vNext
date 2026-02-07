package zw.gov.mohcc.impilo.ubomi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

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

    /**
     * Verify a vital event (birth or death) registration status.
     * Available to both modes — core interoperability function.
     */
    @GetMapping("/{registrationNumber}")
    public ResponseEntity<ApiResponse<String>> verifyRegistration(
            @PathVariable String registrationNumber) {

        TrustContext ctx = TrustContextHolder.require();

        // TODO: delegate to VerificationService for registry lookup
        return ResponseEntity.ok(
            ApiResponse.ok("Verification placeholder for " + registrationNumber + " — mode: " + ctx.mode(),
                ctx.correlationId().toString())
        );
    }
}
