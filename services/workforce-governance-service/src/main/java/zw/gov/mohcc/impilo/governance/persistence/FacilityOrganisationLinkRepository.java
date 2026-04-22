package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityOrganisationLinkRepository extends JpaRepository<FacilityOrganisationLinkEntity, UUID> {

    List<FacilityOrganisationLinkEntity> findByTenantIdAndFacilityIdAndStatus(UUID tenantId, Long facilityId, String status);

    Optional<FacilityOrganisationLinkEntity> findByTenantIdAndFacilityIdAndPrimaryFlagIsTrueAndStatus(
            UUID tenantId, Long facilityId, String status);

    List<FacilityOrganisationLinkEntity> findByTenantIdAndOrganisationIdAndStatus(UUID tenantId, UUID organisationId, String status);

    long countByTenantIdAndOrganisationIdAndStatus(UUID tenantId, UUID organisationId, String status);
}
