package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigPackEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigPackRepository extends JpaRepository<ConfigPackEntity, UUID> {

    Optional<ConfigPackEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ConfigPackEntity> findByTenantIdAndOrganizationIdAndPackKey(
            UUID tenantId, UUID organizationId, String packKey);

    List<ConfigPackEntity> findByTenantIdAndOrganizationId(UUID tenantId, UUID organizationId);

    List<ConfigPackEntity> findByLoadState(String loadState);
}
