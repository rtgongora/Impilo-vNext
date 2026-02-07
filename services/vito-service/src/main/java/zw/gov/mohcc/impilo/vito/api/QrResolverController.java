package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vito.core.qr.QrSigningService;

import java.util.*;

/**
 * QR token resolver — verifies Ed25519 signature + expiry.
 * Returns minimal next-step claims after verification.
 */
@RestController
@RequestMapping("/v1/qr")
public class QrResolverController {

    private final QrSigningService qrSigningService;

    public QrResolverController(QrSigningService qrSigningService) {
        this.qrSigningService = qrSigningService;
    }

    /**
     * GET /v1/qr/resolve/{token} — resolve a signed QR token.
     * Verifies signature and expiry, returns claims.
     */
    @GetMapping("/resolve/{token}")
    public ResponseEntity<Map<String, Object>> resolve(@PathVariable String token) {
        return qrSigningService.verify(token)
                .map(claims -> ResponseEntity.ok(Map.<String, Object>of(
                        "valid", true,
                        "jti", claims.jti(),
                        "tenantId", claims.tenantId(),
                        "purpose", claims.purpose(),
                        "pointer", claims.pointer(),
                        "exp", claims.exp()
                )))
                .orElse(ResponseEntity.ok(Map.of(
                        "valid", false,
                        "message", "Token is invalid or expired"
                )));
    }

    /**
     * GET /v1/qr/public-key — get the public key for external verifiers.
     */
    @GetMapping("/public-key")
    public ResponseEntity<String> publicKey() {
        return ResponseEntity.ok(qrSigningService.getPublicKeyJwk());
    }
}
