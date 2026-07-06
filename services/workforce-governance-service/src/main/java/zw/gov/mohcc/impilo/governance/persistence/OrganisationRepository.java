package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationRepository extends JpaRepository<OrganisationEntity, UUID> {

    Optional<OrganisationEntity> findByTenantIdAndOrganisationCode(UUID tenantId, String organisationCode);

    List<OrganisationEntity> findByTenantIdOrderByNameAsc(UUID tenantId);

    /** Active organisations, paginated — used by the org-mirror backfill sweep. */
    Page<OrganisationEntity> findByActiveFlagTrueOrderByCreatedAtAsc(Pageable pageable);
}
