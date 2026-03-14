package zw.gov.mohcc.impilo.inventory.elmis.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.elmis.persistence.entity.SyncStateEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SyncStateRepository extends JpaRepository<SyncStateEntity, Long> {

    List<SyncStateEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);
}
