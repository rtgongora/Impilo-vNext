package zw.gov.mohcc.impilo.varapi.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.varapi.core.ProviderBadgeService;

import java.util.Map;

/**
 * Public badge-scan verification (PJ6/D-P8; doctrine §20): a patient scans a
 * provider's badge QR and sees live public register facts. Strictly separate
 * from login and from the claim journey. One uniform 200 shape — a fake,
 * revoked or not-in-standing badge is indistinguishable (NOT_RECOGNISED), so
 * the lane is not an oracle. Under the /v1/public/practitioners family
 * (permitAll + Envoy trust-header stripping on the gateway public lane).
 */
@RestController
public class PublicBadgeVerificationController {

    private static final Logger log = LoggerFactory.getLogger(PublicBadgeVerificationController.class);

    private final ProviderBadgeService badgeService;

    public PublicBadgeVerificationController(ProviderBadgeService badgeService) {
        this.badgeService = badgeService;
    }

    /** The QR content (signed compact payload) travels in the body, never the URL/logs. */
    @PostMapping("/v1/public/practitioners/badge-scan")
    public ResponseEntity<ProviderBadgeService.BadgeVerification> verifyScan(
            @RequestBody Map<String, String> body) {
        ProviderBadgeService.BadgeVerification result =
                badgeService.verifyScan(body != null ? body.get("qr") : null);
        log.info("Public badge scan [status={}]", result.status());
        return ResponseEntity.ok(result);
    }
}
