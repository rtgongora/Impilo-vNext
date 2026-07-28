package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigDefinitionVersionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigDefinitionVersionRepository
        extends JpaRepository<ConfigDefinitionVersionEntity, UUID> {

    Optional<ConfigDefinitionVersionEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ConfigDefinitionVersionEntity> findByTenantIdAndDefinitionIdAndSemanticVersion(
            UUID tenantId, UUID definitionId, String semanticVersion);

    Optional<ConfigDefinitionVersionEntity> findByTenantIdAndDefinitionIdAndLifecycleState(
            UUID tenantId, UUID definitionId, String lifecycleState);

    List<ConfigDefinitionVersionEntity> findByTenantIdAndDefinitionIdOrderByCreatedAtDesc(
            UUID tenantId, UUID definitionId);

    List<ConfigDefinitionVersionEntity> findByIdIn(List<UUID> ids);
}
