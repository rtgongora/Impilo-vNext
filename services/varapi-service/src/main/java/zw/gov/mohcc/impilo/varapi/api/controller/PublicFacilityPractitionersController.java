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
import zw.gov.mohcc.impilo.varapi.api.dto.PublicFacilityPractitionerResponse;
import zw.gov.mohcc.impilo.varapi.core.PublicFacilityPractitionerService;

import java.util.List;

/**
 * Facility-scoped public verified-practitioner listing (gateway public find-care
 * lane — docs/architecture/gateway-public-lane-security-adr.md).
 *
 * <p>Mirrors the {@link PublicPractitionerVerificationController} public pattern:
 * a {@code Public*Controller} is the ONLY service-side surface a BFF public
 * controller may call. Returns ONLY the allow-listed
 * {@link PublicFacilityPractitionerResponse} projection for practitioners who
 * genuinely practise at the facility — a practitioner appears ONLY when
 * registration + active affiliation + facility practice context + platform
 * authority are ALL confirmed. Never contact details, national identifiers,
 * internal identifiers, or any availability / slot / schedule signal.</p>
 *
 * <p>An unknown/empty facility is HTTP 200 with an EMPTY list — identical shape,
 * so the endpoint yields no existence oracle.</p>
 */
@RestController
@RequestMapping("/v1/public/facilities")
public class PublicFacilityPractitionersController {

    private static final Logger log = LoggerFactory.getLogger(PublicFacilityPractitionersController.class);

    private final PublicFacilityPractitionerService practitionerService;

    public PublicFacilityPractitionersController(PublicFacilityPractitionerService practitionerService) {
        this.practitionerService = practitionerService;
    }

    @GetMapping("/{facilityId}/practitioners")
    public ResponseEntity<ApiResponse<List<PublicFacilityPractitionerResponse>>> verify(
            @PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        List<PublicFacilityPractitionerResponse> body =
                practitionerService.listVerifiedPractitioners(ctx.tenantId(), facilityId);
        log.info("Public facility practitioner listing [count={}] correlationId={}",
                body.size(), ctx.correlationId());
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }
}
