package zw.gov.mohcc.impilo.orgregistry.api.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.core.AffiliationService;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AffiliationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

/**
 * Internal seam (IATG Wave 2, WS-E): record the administering affiliation for a facility whose
 * administrator appointment went ACTIVE in TUSO. Reuses {@link AffiliationService} — the affiliation
 * is an ordinary {@code FACILITY} relationship link; TUSO remains the facility and appointment SoR.
 */
@RestController
@RequestMapping("/v1/internal/org-registry/organizations/{organizationId}/facility-admin-affiliations")
public class FacilityAdminAffiliationController {

    private final AffiliationService affiliationService;

    public FacilityAdminAffiliationController(AffiliationService affiliationService) {
        this.affiliationService = affiliationService;
    }

    @PostMapping
    public ResponseEntity<AffiliationEntity> record(
            @PathVariable UUID organizationId,
            @RequestBody OrgRegistryDtos.RecordFacilityAdminAffiliationRequest request) throws Exception {
        AffiliationEntity recorded = affiliationService.recordFacilityAdminAffiliation(
                tenantId(), organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(recorded);
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }
}
