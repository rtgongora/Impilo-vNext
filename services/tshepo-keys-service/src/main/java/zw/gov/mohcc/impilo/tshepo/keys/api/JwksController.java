package zw.gov.mohcc.impilo.tshepo.keys.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tshepo.keys.core.JwksService;

import java.util.Map;

/**
 * Public JWKS endpoint for distributing Ed25519 public keys.
 *
 * <p>This endpoint is unauthenticated so that any service or client
 * can fetch the public keys for JWT/JWS verification.</p>
 *
 * <p>Returns an RFC 7517 JWKS containing OKP (Ed25519) public keys.</p>
 */
@RestController
@RequestMapping("/v1/keys")
public class JwksController {

    private final JwksService jwksService;

    public JwksController(JwksService jwksService) {
        this.jwksService = jwksService;
    }

    /**
     * GET /v1/keys/jwks — Returns the JSON Web Key Set containing all active public keys.
     *
     * <p>Response Content-Type: application/json (per RFC 7517 Section 8.5).</p>
     */
    @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getJwks() {
        Map<String, Object> jwks = jwksService.getJwksMap();
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=60")
                .body(jwks);
    }
}
