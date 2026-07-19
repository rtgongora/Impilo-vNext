package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.ProviderBadgeService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBadgeEntity;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Badge issuance/revocation (PJ6, D-P5) — registry-administration surface
 * (badge issuance is a governed act; the card-print-agent consumes the issued
 * serial + QR for physical production). Lost badge = revoke the serial only.
 */
@RestController
public class ProviderBadgeController {

    private static final Set<String> ISSUER_ACTOR_TYPES =
            Set.of("SYSTEM", "REGISTRY_ADMIN", "NATIONAL_ADMIN", "ORG_REPRESENTATIVE");

    private final ProviderBadgeService badgeService;

    public ProviderBadgeController(ProviderBadgeService badgeService) {
        this.badgeService = badgeService;
    }

    @PostMapping("/v1/internal/providers/{providerPublicId}/badges")
    public ResponseEntity<ProviderBadgeService.IssuedBadge> issue(
            @PathVariable("providerPublicId") String providerPublicId,
            @RequestBody(required = false) Map<String, String> body) {
        requireIssuer();
        String printJobRef = body != null ? body.get("printJobRef") : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(badgeService.issue(providerPublicId, printJobRef));
    }

    @PostMapping("/v1/internal/badges/{badgeSerial}/revoke")
    public ResponseEntity<Map<String, Object>> revoke(
            @PathVariable("badgeSerial") String badgeSerial,
            @RequestBody(required = false) Map<String, String> body) {
        requireIssuer();
        ProviderBadgeEntity badge = badgeService.revoke(badgeSerial,
                body != null ? body.get("status") : null,
                body != null ? body.get("reason") : null);
        return ResponseEntity.ok(Map.of(
                "badgeSerial", badge.getBadgeSerial(),
                "status", badge.getStatus()));
    }

    private static void requireIssuer() {
        TrustContext ctx = TrustContextHolder.require();
        String actorType = ctx.actorType() == null ? ""
                : ctx.actorType().trim().toUpperCase(Locale.ROOT);
        if (!ISSUER_ACTOR_TYPES.contains(actorType)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Badge issuance is a registry-administration act");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
