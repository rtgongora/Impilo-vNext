package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigReleaseItemEntity;

import java.util.List;
import java.util.UUID;

public interface ConfigReleaseItemRepository extends JpaRepository<ConfigReleaseItemEntity, UUID> {

    List<ConfigReleaseItemEntity> findByReleaseId(UUID releaseId);

    void deleteByReleaseIdAndDefinitionId(UUID releaseId, UUID definitionId);
}
