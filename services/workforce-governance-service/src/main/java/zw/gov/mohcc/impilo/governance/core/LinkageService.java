package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.FacilityOrganisationLinkEntity;
import zw.gov.mohcc.impilo.governance.persistence.FacilityOrganisationLinkRepository;
import zw.gov.mohcc.impilo.governance.persistence.SiteOrganisationLinkEntity;
import zw.gov.mohcc.impilo.governance.persistence.SiteOrganisationLinkRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class LinkageService {

    private final FacilityOrganisationLinkRepository facilityOrganisationLinkRepository;
    private final SiteOrganisationLinkRepository siteOrganisationLinkRepository;
    private final GovernanceEventService governanceEventService;

    public LinkageService(FacilityOrganisationLinkRepository facilityOrganisationLinkRepository,
                          SiteOrganisationLinkRepository siteOrganisationLinkRepository,
                          GovernanceEventService governanceEventService) {
        this.facilityOrganisationLinkRepository = facilityOrganisationLinkRepository;
        this.siteOrganisationLinkRepository = siteOrganisationLinkRepository;
        this.governanceEventService = governanceEventService;
    }

    @Transactional
    public FacilityOrganisationLinkEntity linkFacility(UUID tenantId,
                                                       Long facilityId,
                                                       UUID organisationId,
                                                       UUID organisationUnitId,
                                                       String relationshipType,
                                                       boolean primary,
                                                       LocalDate startDate,
                                                       LocalDate endDate) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityOrganisationLinkEntity link = new FacilityOrganisationLinkEntity(
                UUID.randomUUID(),
                tenantId,
                facilityId,
                organisationId,
                relationshipType,
                GovernanceEnums.LinkStatus.ACTIVE.name());
        link.setOrganisationUnitId(organisationUnitId);
        link.setPrimaryFlag(primary);
        link.setStartDate(startDate);
        link.setEndDate(endDate);
        link = facilityOrganisationLinkRepository.save(link);
        governanceEventService.enqueue("FACILITY_ORG_LINK", link.getId().toString(), "impilo.governance.facility.organisation_linked",
                Map.of("linkId", link.getId().toString(), "facilityId", facilityId.toString(), "organisationId", organisationId.toString()),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return link;
    }

    @Transactional
    public SiteOrganisationLinkEntity linkSite(UUID tenantId,
                                               UUID siteId,
                                               UUID organisationId,
                                               UUID organisationUnitId,
                                               String relationshipType,
                                               boolean primary,
                                               LocalDate startDate,
                                               LocalDate endDate) {
        TrustContext ctx = TrustContextHolder.require();
        SiteOrganisationLinkEntity link = new SiteOrganisationLinkEntity(
                UUID.randomUUID(),
                tenantId,
                siteId,
                organisationId,
                relationshipType,
                GovernanceEnums.LinkStatus.ACTIVE.name());
        link.setOrganisationUnitId(organisationUnitId);
        link.setPrimaryFlag(primary);
        link.setStartDate(startDate);
        link.setEndDate(endDate);
        link = siteOrganisationLinkRepository.save(link);
        governanceEventService.enqueue("SITE_ORG_LINK", link.getId().toString(), "impilo.governance.site.organisation_linked",
                Map.of("linkId", link.getId().toString(), "siteId", siteId.toString(), "organisationId", organisationId.toString()),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return link;
    }
}
