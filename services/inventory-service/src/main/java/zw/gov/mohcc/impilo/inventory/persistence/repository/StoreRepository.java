package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.StoreType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StoreEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface StoreRepository extends JpaRepository<StoreEntity, UUID> {

    List<StoreEntity> findByFacilityId(UUID facilityId);

    List<StoreEntity> findByFacilityIdAndStoreType(UUID facilityId, StoreType storeType);

    List<StoreEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);

    List<StoreEntity> findByParentStoreId(UUID parentStoreId);
}
