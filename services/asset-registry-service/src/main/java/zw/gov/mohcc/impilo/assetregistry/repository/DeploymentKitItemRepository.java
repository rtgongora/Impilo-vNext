package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.DeploymentKitItemEntity;

import java.util.List;
import java.util.UUID;

public interface DeploymentKitItemRepository extends JpaRepository<DeploymentKitItemEntity, UUID> {
    List<DeploymentKitItemEntity> findByKitId(UUID kitId);
}
