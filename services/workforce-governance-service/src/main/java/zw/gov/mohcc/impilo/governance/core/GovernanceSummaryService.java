package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.*;

import java.util.List;
import java.util.UUID;

@Service
public class GovernanceSummaryService {

    private final OrganisationRepository organisationRepository;
    private final OrganisationUnitRepository organisationUnitRepository;
    private final FacilityOrganisationLinkRepository facilityOrganisationLinkRepository;
    private final SiteOrganisationLinkRepository siteOrganisationLinkRepository;
    private final AssignmentRepository assignmentRepository;

    public GovernanceSummaryService(OrganisationRepository organisationRepository,
                                    OrganisationUnitRepository organisationUnitRepository,
                                    FacilityOrganisationLinkRepository facilityOrganisationLinkRepository,
                                    SiteOrganisationLinkRepository siteOrganisationLinkRepository,
                                    AssignmentRepository assignmentRepository) {
        this.organisationRepository = organisationRepository;
        this.organisationUnitRepository = organisationUnitRepository;
        this.facilityOrganisationLinkRepository = facilityOrganisationLinkRepository;
        this.siteOrganisationLinkRepository = siteOrganisationLinkRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public OrganisationSummary organisationSummary(UUID organisationId) {
        OrganisationEntity org = organisationRepository.findById(organisationId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        UUID tenantId = org.getTenantId();
        long units = organisationUnitRepository.findByTenantIdAndOrganisationIdOrderByNameAsc(tenantId, organisationId).size();
        long facilities = facilityOrganisationLinkRepository.countByTenantIdAndOrganisationIdAndStatus(
                tenantId, organisationId, GovernanceEnums.LinkStatus.ACTIVE.name());
        long sites = siteOrganisationLinkRepository.countByTenantIdAndOrganisationIdAndStatus(
                tenantId, organisationId, GovernanceEnums.LinkStatus.ACTIVE.name());
        long assignments = assignmentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(a -> organisationId.equals(a.getOrganisationId()))
                .count();
        return new OrganisationSummary(
                org.getId(),
                org.getTenantId(),
                org.getOrganisationCode(),
                org.getName(),
                org.getStatus(),
                units,
                facilities,
                sites,
                assignments
        );
    }

    public record OrganisationSummary(
            UUID organisationId,
            UUID tenantId,
            String organisationCode,
            String name,
            String status,
            long organisationUnitCount,
            long linkedFacilityCount,
            long linkedSiteCount,
            long assignmentCount
    ) {}

    public FacilityGovernanceSummary facilityGovernanceSummary(UUID tenantId, Long facilityId) {
        List<FacilityOrganisationLinkEntity> links = facilityOrganisationLinkRepository
                .findByTenantIdAndFacilityIdAndStatus(tenantId, facilityId, GovernanceEnums.LinkStatus.ACTIVE.name());
        return new FacilityGovernanceSummary(facilityId, links.stream().map(l ->
                new FacilityOrgLinkView(l.getId(), l.getOrganisationId(), l.getOrganisationUnitId(),
                        l.getRelationshipType(), l.isPrimaryFlag(), l.getStatus())).toList());
    }

    public record FacilityOrgLinkView(
            UUID linkId,
            UUID organisationId,
            UUID organisationUnitId,
            String relationshipType,
            boolean primary,
            String status
    ) {}

    public record FacilityGovernanceSummary(Long facilityId, List<FacilityOrgLinkView> organisationLinks) {}
}
