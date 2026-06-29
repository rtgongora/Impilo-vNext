package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierCatalogueItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for supplier catalogue items. */
@Repository
public interface SupplierCatalogueItemRepository extends JpaRepository<SupplierCatalogueItemEntity, UUID> {

    List<SupplierCatalogueItemEntity> findByTenantIdAndSupplierIdOrderByItemName(UUID tenantId, UUID supplierId);

    Optional<SupplierCatalogueItemEntity> findByTenantIdAndSupplierIdAndItemName(
            UUID tenantId, UUID supplierId, String itemName);
}
