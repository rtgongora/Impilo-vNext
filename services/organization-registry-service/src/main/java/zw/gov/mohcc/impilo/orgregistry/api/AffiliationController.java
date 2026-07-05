package zw.gov.mohcc.impilo.orgregistry.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.orgregistry.core.AffiliationService;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AffiliationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/organizations/{organizationId}/affiliations")
public class AffiliationController {

    private final AffiliationService affiliationService;

    public AffiliationController(AffiliationService affiliationService) {
        this.affiliationService = affiliationService;
    }

    @GetMapping
    public List<AffiliationEntity> list(@PathVariable UUID organizationId) {
        return affiliationService.list(tenantId(), organizationId);
    }

    @PostMapping
    public ResponseEntity<AffiliationEntity> create(
            @PathVariable UUID organizationId,
            @RequestBody OrgRegistryDtos.CreateAffiliationRequest request) throws Exception {
        AffiliationEntity created = affiliationService.create(tenantId(), organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{affiliationId}")
    public AffiliationEntity update(
            @PathVariable UUID organizationId,
            @PathVariable UUID affiliationId,
            @RequestBody OrgRegistryDtos.UpdateAffiliationRequest request) throws Exception {
        return affiliationService.update(tenantId(), organizationId, affiliationId, request);
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }
}
