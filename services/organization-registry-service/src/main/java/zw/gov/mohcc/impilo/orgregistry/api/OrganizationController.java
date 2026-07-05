package zw.gov.mohcc.impilo.orgregistry.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.orgregistry.core.OrganizationService;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AuthorizedRepresentativeEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<OrganizationEntity> create(
            @RequestBody OrgRegistryDtos.CreateOrganizationRequest request) throws Exception {
        OrganizationEntity created = organizationService.createDraft(tenantId(), request, actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<OrganizationEntity> list(@RequestParam(required = false) String status) {
        return organizationService.list(tenantId(), status);
    }

    @GetMapping("/{id}")
    public OrganizationEntity get(@PathVariable UUID id) {
        return organizationService.get(tenantId(), id);
    }

    @PostMapping("/{id}/representatives")
    public ResponseEntity<AuthorizedRepresentativeEntity> addRepresentative(
            @PathVariable UUID id,
            @RequestBody OrgRegistryDtos.AddRepresentativeRequest request) throws Exception {
        AuthorizedRepresentativeEntity created =
                organizationService.addRepresentative(tenantId(), id, request, actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}/representatives")
    public List<AuthorizedRepresentativeEntity> listRepresentatives(@PathVariable UUID id) {
        return organizationService.listRepresentatives(tenantId(), id);
    }

    @PostMapping("/{id}/submit-verification")
    public OrganizationEntity submitVerification(@PathVariable UUID id) throws Exception {
        return organizationService.submitVerification(tenantId(), id, actorId());
    }

    /** National-admin action — records the verifier from trust context. */
    @PostMapping("/{id}/verify")
    public OrganizationEntity verify(
            @PathVariable UUID id,
            @RequestBody(required = false) OrgRegistryDtos.VerifyOrganizationRequest request) throws Exception {
        return organizationService.verify(tenantId(), id, actorId(),
                request != null ? request.verifiedRepresentativeIds() : null);
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }
}
