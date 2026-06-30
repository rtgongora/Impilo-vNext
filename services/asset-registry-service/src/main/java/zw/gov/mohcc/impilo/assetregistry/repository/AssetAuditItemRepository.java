package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetAuditItemEntity;

import java.util.List;
import java.util.UUID;

public interface AssetAuditItemRepository extends JpaRepository<AssetAuditItemEntity, UUID> {
    List<AssetAuditItemEntity> findByAuditId(UUID auditId);
}
