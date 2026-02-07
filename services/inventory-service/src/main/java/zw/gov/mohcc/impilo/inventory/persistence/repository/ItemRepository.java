package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<ItemEntity, UUID> {

    Optional<ItemEntity> findByTenantIdAndItemCode(UUID tenantId, String itemCode);

    Optional<ItemEntity> findByItemCode(String itemCode);

    List<ItemEntity> findByBarcode(String barcode);

    Optional<ItemEntity> findFirstByBarcode(String barcode);

    List<ItemEntity> findByTenantIdAndCategory(UUID tenantId, String category);
}
