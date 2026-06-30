package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.DeploymentKitEntity;

import java.util.List;
import java.util.UUID;

public interface DeploymentKitRepository extends JpaRepository<DeploymentKitEntity, UUID> {
    List<DeploymentKitEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
