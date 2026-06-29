package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierOrderLineEntity;

import java.util.List;
import java.util.UUID;

/** Repository for supplier order lines. */
@Repository
public interface SupplierOrderLineRepository extends JpaRepository<SupplierOrderLineEntity, UUID> {

    List<SupplierOrderLineEntity> findBySupplierOrderId(UUID supplierOrderId);
}
