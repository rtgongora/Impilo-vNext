package zw.gov.mohcc.impilo.varapi.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.PublicPractitionerVerificationResponse;
import zw.gov.mohcc.impilo.varapi.core.PublicPractitionerVerificationService;

/**
 * Public practitioner register verification (gateway public lane, W2).
 *
 * <p>Mirrors the tuso public pattern (template: {@code PublicFacilityController}):
 * a {@code Public*Controller} is the ONLY service-side surface a BFF public
 * controller may call (docs/architecture/gateway-public-lane-security-adr.md).
 * Discloses ONLY public register facts via the allow-listed
 * {@link PublicPractitionerVerificationResponse}: display name, profession/cadre,
 * register status, practising-certificate validity window and registering
 * authority. Never contact details, national identifiers, disciplinary or
 * qualification detail.</p>
 *
 * <p>Lookup is exact-match by registration number (enumeration resistance —
 * no fuzzy or name search on this lane). A miss is HTTP 200 with
 * {@code registerStatus=NOT_FOUND} in the identical response shape, so the
 * endpoint yields no existence oracle beyond the register status itself.</p>
 */
@RestController
@RequestMapping("/v1/public/practitioners")
public class PublicPractitionerVerificationController {

    private static final Logger log = LoggerFactory.getLogger(PublicPractitionerVerificationController.class);

    private final PublicPractitionerVerificationService verificationService;

    public PublicPractitionerVerificationController(PublicPractitionerVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/verify/{registrationNumber}")
    public ResponseEntity<ApiResponse<PublicPractitionerVerificationResponse>> verify(
            @PathVariable String registrationNumber) {
        TrustContext ctx = TrustContextHolder.require();
        PublicPractitionerVerificationResponse body =
                verificationService.verify(ctx.tenantId(), registrationNumber);
        log.info("Public practitioner verification [status={}] correlationId={}",
                body.registerStatus(), ctx.correlationId());
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }
}
