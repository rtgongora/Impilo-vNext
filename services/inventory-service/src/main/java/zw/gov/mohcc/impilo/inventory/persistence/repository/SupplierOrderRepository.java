package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierOrderEntity;

import java.util.List;
import java.util.UUID;

/** Repository for supplier orders. */
@Repository
public interface SupplierOrderRepository extends JpaRepository<SupplierOrderEntity, UUID> {

    List<SupplierOrderEntity> findByTenantIdAndSupplierIdOrderByCreatedAtDesc(UUID tenantId, UUID supplierId);

    List<SupplierOrderEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
