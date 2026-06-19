package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceMembershipEntity;

import java.util.List;
import java.util.UUID;

public interface WorkforceMembershipRepository extends JpaRepository<WorkforceMembershipEntity, UUID> {

    List<WorkforceMembershipEntity> findByTenantIdAndWorkforceProfileIdOrderByEffectiveDateDesc(
            UUID tenantId, UUID workforceProfileId);

    List<WorkforceMembershipEntity> findByTenantIdAndOrganisationId(UUID tenantId, UUID organisationId);
}
