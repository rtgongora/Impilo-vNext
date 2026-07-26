package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkforceProfileRepository extends JpaRepository<WorkforceProfileEntity, UUID> {

    List<WorkforceProfileEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<WorkforceProfileEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<WorkforceProfileEntity> findByTenantIdAndHealthId(UUID tenantId, String healthId);

    Optional<WorkforceProfileEntity> findByTenantIdAndProviderWorkerId(UUID tenantId, String providerWorkerId);

    /** Batch profile→person-anchor lookup for the experience composition layer (rota display names). */
    List<WorkforceProfileEntity> findByTenantIdAndIdIn(UUID tenantId, java.util.Collection<UUID> ids);

    long countByTenantIdAndCurrentStatus(UUID tenantId, String currentStatus);
}
