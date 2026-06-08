package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodOrderItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodOrderItemRepository extends JpaRepository<BloodOrderItemEntity, Long> {
    Optional<BloodOrderItemEntity> findByItemIdAndTenantId(UUID itemId, UUID tenantId);
    List<BloodOrderItemEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<BloodOrderItemEntity> findByOrderIdAndTenantId(UUID orderId, UUID tenantId);
}
