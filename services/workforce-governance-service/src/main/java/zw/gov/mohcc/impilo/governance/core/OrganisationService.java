package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final GovernanceEventService governanceEventService;

    public OrganisationService(OrganisationRepository organisationRepository, GovernanceEventService governanceEventService) {
        this.organisationRepository = organisationRepository;
        this.governanceEventService = governanceEventService;
    }

    @Transactional
    public OrganisationEntity createOrganisation(UUID tenantId,
                                                 String organisationCode,
                                                 String name,
                                                 String legalName,
                                                 String organisationType,
                                                 UUID parentOrganisationId,
                                                 String metadataJson) {
        TrustContext ctx = TrustContextHolder.require();
        if (organisationRepository.findByTenantIdAndOrganisationCode(tenantId, organisationCode).isPresent()) {
            throw new IllegalArgumentException("Organisation code already exists for tenant");
        }
        OrganisationEntity org = new OrganisationEntity(
                UUID.randomUUID(),
                tenantId,
                organisationCode,
                name,
                organisationType,
                GovernanceEnums.OrganisationStatus.DRAFT.name());
        org.setLegalName(legalName);
        org.setParentOrganisationId(parentOrganisationId);
        org.setMetadata(metadataJson);
        org = organisationRepository.save(org);
        governanceEventService.enqueue("ORGANISATION", org.getId().toString(), "impilo.governance.organisation.created",
                Map.of("organisationId", org.getId().toString(), "code", organisationCode, "status", org.getStatus()),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return org;
    }

    @Transactional
    public OrganisationEntity updateStatus(UUID organisationId, GovernanceEnums.OrganisationStatus newStatus) {
        TrustContext ctx = TrustContextHolder.require();
        OrganisationEntity org = organisationRepository.findById(organisationId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        org.setStatus(newStatus.name());
        if (newStatus == GovernanceEnums.OrganisationStatus.ACTIVE) {
            org.setActiveFlag(true);
        } else if (newStatus == GovernanceEnums.OrganisationStatus.CLOSED
                || newStatus == GovernanceEnums.OrganisationStatus.INACTIVE) {
            org.setActiveFlag(false);
        }
        org = organisationRepository.save(org);
        governanceEventService.enqueue("ORGANISATION", org.getId().toString(), "impilo.governance.organisation.updated",
                Map.of("organisationId", org.getId().toString(), "status", org.getStatus()),
                org.getTenantId(), ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return org;
    }

    public OrganisationEntity get(UUID id) {
        return organisationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
    }

    public List<OrganisationEntity> list(UUID tenantId) {
        return organisationRepository.findByTenantIdOrderByNameAsc(tenantId);
    }
}
