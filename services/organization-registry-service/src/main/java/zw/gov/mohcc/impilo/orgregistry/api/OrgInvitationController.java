package zw.gov.mohcc.impilo.orgregistry.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.orgregistry.core.OrgInvitationService;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgInvitationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Organisation-invitation onboarding endpoints. An authorized representative issues an
 * invitation for a role; the invitee accepts by token to establish an affiliation.
 */
@RestController
@RequestMapping("/v1")
public class OrgInvitationController {

    private final OrgInvitationService invitationService;

    public OrgInvitationController(OrgInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/organizations/{organizationId}/invitations")
    public List<OrgInvitationEntity> list(@PathVariable UUID organizationId) {
        return invitationService.list(organizationId);
    }

    @PostMapping("/organizations/{organizationId}/invitations")
    public ResponseEntity<OrgInvitationEntity> create(
            @PathVariable UUID organizationId,
            @RequestBody OrgRegistryDtos.CreateInvitationRequest request) {
        OrgInvitationEntity created = invitationService.create(tenantId(), organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/invitations/accept")
    public OrgInvitationEntity accept(@RequestBody OrgRegistryDtos.AcceptInvitationRequest request) {
        return invitationService.accept(tenantId(), request);
    }

    @PostMapping("/invitations/{invitationId}/revoke")
    public OrgInvitationEntity revoke(@PathVariable UUID invitationId) {
        return invitationService.revoke(tenantId(), invitationId);
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }
}
