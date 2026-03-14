package zw.gov.mohcc.impilo.pharmacy.elmis.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.elmis.persistence.entity.DispenseSyncEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for dispense synchronization tracking records.
 */
@Repository
public interface DispenseSyncRepository extends JpaRepository<DispenseSyncEntity, Long> {

    List<DispenseSyncEntity> findByTenantId(UUID tenantId);

    List<DispenseSyncEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);
}
