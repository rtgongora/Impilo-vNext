package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationMembershipRepository extends JpaRepository<OrganisationMembershipEntity, UUID> {

    /** Rows whose phase-2c dual-key is not yet populated — used by the callable key backfill. */
    List<OrganisationMembershipEntity> findByTenantIdAndOrgRegistryOrgIdIsNull(UUID tenantId);

    List<OrganisationMembershipEntity> findByTenantIdAndOrganisationIdOrderByUpdatedAtDesc(UUID tenantId, UUID organisationId);

    Optional<OrganisationMembershipEntity> findByTenantIdAndOrganisationIdAndSubjectIdAndSubjectType(
            UUID tenantId, UUID organisationId, String subjectId, String subjectType);
}
