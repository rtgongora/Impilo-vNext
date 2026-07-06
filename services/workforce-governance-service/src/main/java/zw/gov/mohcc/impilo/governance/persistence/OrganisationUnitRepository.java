package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrganisationUnitRepository extends JpaRepository<OrganisationUnitEntity, UUID> {

    /** Rows whose phase-2c dual-key is not yet populated — used by the callable key backfill. */
    List<OrganisationUnitEntity> findByTenantIdAndOrgRegistryOrgIdIsNull(UUID tenantId);

    List<OrganisationUnitEntity> findByTenantIdAndOrganisationIdOrderByNameAsc(UUID tenantId, UUID organisationId);
}
