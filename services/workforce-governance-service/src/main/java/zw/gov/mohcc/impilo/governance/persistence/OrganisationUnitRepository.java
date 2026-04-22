package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrganisationUnitRepository extends JpaRepository<OrganisationUnitEntity, UUID> {

    List<OrganisationUnitEntity> findByTenantIdAndOrganisationIdOrderByNameAsc(UUID tenantId, UUID organisationId);
}
