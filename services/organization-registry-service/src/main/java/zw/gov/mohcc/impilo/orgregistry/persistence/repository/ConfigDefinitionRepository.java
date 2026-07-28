package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigDefinitionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigDefinitionRepository extends JpaRepository<ConfigDefinitionEntity, UUID> {

    Optional<ConfigDefinitionEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ConfigDefinitionEntity> findByTenantIdAndPackIdAndTypeCodeAndDefinitionKey(
            UUID tenantId, UUID packId, String typeCode, String definitionKey);

    List<ConfigDefinitionEntity> findByTenantIdAndPackId(UUID tenantId, UUID packId);
}
