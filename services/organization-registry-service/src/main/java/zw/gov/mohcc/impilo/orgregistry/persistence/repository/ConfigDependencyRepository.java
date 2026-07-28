package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigDependencyEntity;

import java.util.List;
import java.util.UUID;

public interface ConfigDependencyRepository extends JpaRepository<ConfigDependencyEntity, UUID> {

    List<ConfigDependencyEntity> findByDefinitionVersionId(UUID definitionVersionId);

    List<ConfigDependencyEntity> findByDefinitionVersionIdIn(List<UUID> definitionVersionIds);
}
