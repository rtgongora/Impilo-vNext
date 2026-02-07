package zw.gov.mohcc.impilo.msika.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * National Tariff API — the Tshepo-Msika handshake in action.
 *
 * External 3rd-party apps can fetch the National Tariff list by:
 *   1. Authenticating with Keycloak (OIDC) to get a Tshepo-scoped JWT
 *   2. Calling GET /v1/tariffs with the bearer token
 *   3. TrustContextFilter detects EXTERNAL mode
 *   4. SecurityConfig validates the JWT against Keycloak's JWKS
 *   5. This controller checks AccessMode and serves accordingly
 *
 * Write operations (POST/PUT/DELETE) are restricted to INTERNAL mode only.
 */
@RestController
@RequestMapping("/v1/tariffs")
public class TariffController {

    /**
     * Fetch national tariff list.
     * Available to both INTERNAL and EXTERNAL consumers.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<String>> listTariffs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();

        // TODO: delegate to TariffService for paginated tariff lookup
        return ResponseEntity.ok(
            ApiResponse.ok("Tariff list placeholder — mode: " + ctx.mode(), ctx.correlationId().toString())
        );
    }

    /**
     * Create or update a tariff entry.
     * INTERNAL mode only — external consumers cannot modify the national tariff.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> createTariff(@RequestBody String body) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                ApiResponse.error("FORBIDDEN", "External consumers cannot modify tariffs", 403, ctx.correlationId().toString())
            );
        }

        // TODO: delegate to TariffService
        return ResponseEntity.ok(
            ApiResponse.ok("Tariff created placeholder", ctx.correlationId().toString())
        );
    }
}
