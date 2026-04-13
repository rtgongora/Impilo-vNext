package zw.gov.mohcc.impilo.procurement.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procurement.persistence.entity.PurchaseOrderEntity;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, UUID> {
    List<PurchaseOrderEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
