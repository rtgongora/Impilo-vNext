package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigReleaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigReleaseRepository extends JpaRepository<ConfigReleaseEntity, UUID> {

    Optional<ConfigReleaseEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ConfigReleaseEntity> findByTenantIdAndPackIdAndLifecycleState(
            UUID tenantId, UUID packId, String lifecycleState);

    List<ConfigReleaseEntity> findByTenantIdAndPackIdOrderByCreatedAtDesc(UUID tenantId, UUID packId);
}
