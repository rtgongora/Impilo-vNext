package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SiteOrganisationLinkRepository extends JpaRepository<SiteOrganisationLinkEntity, UUID> {

    /** Rows whose phase-2c dual-key is not yet populated — used by the callable key backfill. */
    List<SiteOrganisationLinkEntity> findByTenantIdAndOrgRegistryOrgIdIsNull(UUID tenantId);

    List<SiteOrganisationLinkEntity> findByTenantIdAndSiteIdAndStatus(UUID tenantId, UUID siteId, String status);

    List<SiteOrganisationLinkEntity> findByTenantIdAndOrganisationIdAndStatus(UUID tenantId, UUID organisationId, String status);

    long countByTenantIdAndOrganisationIdAndStatus(UUID tenantId, UUID organisationId, String status);
}
