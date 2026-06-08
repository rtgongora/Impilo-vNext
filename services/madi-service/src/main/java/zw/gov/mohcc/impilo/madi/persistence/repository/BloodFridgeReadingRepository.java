package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodFridgeReadingEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface BloodFridgeReadingRepository extends JpaRepository<BloodFridgeReadingEntity, Long> {
    List<BloodFridgeReadingEntity> findByFridgeIdAndTenantIdOrderByRecordedAtDesc(UUID fridgeId, UUID tenantId);
}
