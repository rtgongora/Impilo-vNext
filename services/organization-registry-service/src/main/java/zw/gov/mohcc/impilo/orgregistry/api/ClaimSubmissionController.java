package zw.gov.mohcc.impilo.orgregistry.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.orgregistry.core.ClaimSubmissionService;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgClaimSubmissionEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class ClaimSubmissionController {

    private final ClaimSubmissionService claimSubmissionService;

    public ClaimSubmissionController(ClaimSubmissionService claimSubmissionService) {
        this.claimSubmissionService = claimSubmissionService;
    }

    @PostMapping("/organizations/{organizationId}/claims")
    public ResponseEntity<OrgClaimSubmissionEntity> submit(
            @PathVariable UUID organizationId,
            @RequestBody OrgRegistryDtos.CreateClaimRequest request) throws Exception {
        OrgClaimSubmissionEntity created = claimSubmissionService.submit(tenantId(), organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/organizations/{organizationId}/claims")
    public List<OrgClaimSubmissionEntity> listForOrganization(@PathVariable UUID organizationId) {
        return claimSubmissionService.listForOrganization(tenantId(), organizationId);
    }

    @GetMapping("/claims/{id}")
    public OrgClaimSubmissionEntity get(@PathVariable UUID id) {
        return claimSubmissionService.get(tenantId(), id);
    }

    @PostMapping("/claims/{id}/transition")
    public OrgClaimSubmissionEntity transition(
            @PathVariable UUID id,
            @RequestBody OrgRegistryDtos.TransitionClaimRequest request) throws Exception {
        return claimSubmissionService.transition(tenantId(), id, request.status(), request.adjudicationRef());
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }
}
