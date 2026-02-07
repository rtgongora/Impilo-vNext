package zw.gov.mohcc.impilo.credential.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.credential.api.dto.VerificationResult;
import zw.gov.mohcc.impilo.credential.core.CredentialService;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

/**
 * Public credential verification endpoint.
 *
 * NO authentication required. All verification requests are logged
 * for audit purposes. Returns minimal information to prevent PII leakage.
 */
@RestController
@RequestMapping("/v1/public/verify")
public class PublicVerifyController {

    private static final Logger log = LoggerFactory.getLogger(PublicVerifyController.class);

    private final CredentialService credentialService;

    public PublicVerifyController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * Verify a credential by its public verification token.
     *
     * @param token the 64-character hex verification token
     * @return verification result with credential status and minimal details
     */
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<VerificationResult>> verifyCredential(
            @PathVariable String token,
            HttpServletRequest request) {

        String verifierIp = extractClientIp(request);
        String verifierAgent = request.getHeader("User-Agent");

        log.info("Public verification request for token: {}... from IP: {}",
                token.length() > 8 ? token.substring(0, 8) : token, verifierIp);

        VerificationResult result = credentialService.verifyByToken(token, verifierIp, verifierAgent);

        String correlationId = UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
