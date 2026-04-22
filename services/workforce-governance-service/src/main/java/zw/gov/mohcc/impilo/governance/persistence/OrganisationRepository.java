package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationRepository extends JpaRepository<OrganisationEntity, UUID> {

    Optional<OrganisationEntity> findByTenantIdAndOrganisationCode(UUID tenantId, String organisationCode);

    List<OrganisationEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
